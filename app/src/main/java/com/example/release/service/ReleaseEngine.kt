package com.example.release.service

import android.content.Context
import com.example.apk.signing.SigningManager
import com.example.core.CryptoUtils
import com.example.release.model.CiErrorCategory
import com.example.release.model.CiFailureRecord
import com.example.release.model.ReleaseArtifactInfo
import com.example.release.model.ReleaseMetadata
import com.example.release.model.WorkflowRunStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class CiWorkflowState(
    val repositoryName: String = "efraim-user/efraim-apk-workbench",
    val branchName: String = "main",
    val latestCommitSha: String = "7b89f10a2d",
    val status: WorkflowRunStatus = WorkflowRunStatus.IDLE,
    val buildStatus: String = "READY",
    val testStatus: String = "PASSED",
    val signingStatus: String = "CONFIGURED (RSA-2048 / v1)",
    val artifactStatus: String = "READY",
    val releaseStatus: String = "IDLE",
    val latestVersion: String = "1.0",
    val latestRunId: Long? = null,
    val runUrl: String = ""
)

class GitHubDeploymentProvider(
    private val gitHubService: GitHubService = GitHubService()
) {
    suspend fun checkConnection(
        owner: String,
        repo: String,
        token: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(Exception("GitHub token is not configured. Real repository connection required for production actions."))
        }
        val result = gitHubService.getRepositoryInfo(owner, repo, token)
        result.map { json ->
            json.optString("full_name", "$owner/$repo")
        }
    }

    suspend fun triggerBuild(
        owner: String,
        repo: String,
        branch: String = "main",
        token: String
    ): Result<Boolean> {
        val inputs = mapOf(
            "release_type" to "development",
            "create_github_release" to false
        )
        return gitHubService.triggerWorkflowDispatch(owner, repo, "android-release.yml", branch, inputs, token)
    }

    suspend fun triggerProductionRelease(
        owner: String,
        repo: String,
        branch: String = "main",
        customNotes: String = "",
        token: String
    ): Result<Boolean> {
        val inputs = mapOf(
            "release_type" to "production",
            "create_github_release" to true,
            "custom_release_notes" to customNotes
        )
        return gitHubService.triggerWorkflowDispatch(owner, repo, "android-release.yml", branch, inputs, token)
    }
}

class ReleaseEngine(
    private val context: Context,
    private val signingManager: SigningManager,
    private val gitHubDeploymentProvider: GitHubDeploymentProvider = GitHubDeploymentProvider()
) {
    private val _ciWorkflowState = MutableStateFlow(CiWorkflowState())
    val ciWorkflowState: StateFlow<CiWorkflowState> = _ciWorkflowState.asStateFlow()

    private val _ciErrors = MutableStateFlow<List<CiFailureRecord>>(emptyList())
    val ciErrors: StateFlow<List<CiFailureRecord>> = _ciErrors.asStateFlow()

    fun updateWorkflowState(newState: CiWorkflowState) {
        _ciWorkflowState.value = newState
    }

    fun recordCiError(error: CiFailureRecord) {
        _ciErrors.value = listOf(error) + _ciErrors.value
    }

    fun clearCiErrors() {
        _ciErrors.value = emptyList()
    }

    suspend fun buildReleaseManifest(
        apkFile: File,
        versionName: String = "1.0",
        versionCode: Long = 1L
    ): Result<ReleaseMetadata> = withContext(Dispatchers.IO) {
        try {
            if (!apkFile.exists()) {
                val err = CiFailureRecord(
                    category = CiErrorCategory.ARTIFACT_ERROR,
                    stageName = "Release Artifact Packaging",
                    summary = "Release APK file not found at path: ${apkFile.absolutePath}",
                    logSnippet = "File not found: ${apkFile.name}",
                    suggestedRemediation = "Verify that the APK build task produced a valid output artifact in app/build/outputs/apk/release."
                )
                recordCiError(err)
                return@withContext Result.failure(Exception("Release APK does not exist"))
            }

            val sha256 = CryptoUtils.calculateSha256(apkFile)
            val size = apkFile.length()

            val verification = signingManager.verifyApk(apkFile)
            val isSigned = verification.isValid

            if (!isSigned) {
                val err = CiFailureRecord(
                    category = CiErrorCategory.SIGNING_ERROR,
                    stageName = "Signature Verification",
                    summary = "Artifact was not cryptographically signed with release certificate.",
                    logSnippet = "Signing verification details: ${verification.issues.joinToString(", ")}",
                    suggestedRemediation = "Configure KEYSTORE_BASE64 and STORE_PASSWORD secrets in GitHub Repository Secrets."
                )
                recordCiError(err)
            }

            val artifact = ReleaseArtifactInfo(
                fileName = apkFile.name,
                artifactType = "APK",
                sizeBytes = size,
                sha256 = sha256,
                isSigned = isSigned,
                downloadUrl = ""
            )

            val metadata = ReleaseMetadata(
                productName = "EFRAIM APK WORKBENCH TOOL M",
                shortName = "EFRAIM APK WORKBENCH",
                applicationId = "com.aistudio.apkworkbench.wb7x",
                versionName = versionName,
                versionCode = versionCode,
                artifacts = listOf(artifact)
            )

            Result.success(metadata)
        } catch (e: Exception) {
            val err = CiFailureRecord(
                category = CiErrorCategory.BUILD_ERROR,
                stageName = "Release Engine Packaging",
                summary = "Failed to build release manifest: ${e.message}",
                logSnippet = e.stackTraceToString(),
                suggestedRemediation = "Review build configuration and filesystem storage permissions."
            )
            recordCiError(err)
            Result.failure(e)
        }
    }

    /**
     * Evaluates release gating against critical security findings.
     */
    fun validateReleaseSecurityGates(
        hasCriticalSecurityFailures: Boolean,
        failedGateSummary: String? = null
    ): Result<Boolean> {
        if (hasCriticalSecurityFailures) {
            val err = CiFailureRecord(
                category = CiErrorCategory.RELEASE_ERROR,
                stageName = "Security Release Gate",
                summary = "SECURITY RELEASE BLOCKED: Critical security findings or exposed secrets detected.",
                logSnippet = failedGateSummary ?: "Critical gate failure detected.",
                suggestedRemediation = "Review Security & Integrity findings, resolve critical vulnerabilities, or obtain authorized risk acceptance."
            )
            recordCiError(err)
            return Result.failure(IllegalStateException("Release blocked by security gate: $failedGateSummary"))
        }
        return Result.success(true)
    }
}
