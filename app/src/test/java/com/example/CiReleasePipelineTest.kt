package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.apk.builder.ApkBuildManager
import com.example.apk.builder.ProjectBuildConfig
import com.example.apk.model.AbiOption
import com.example.apk.model.BuildType
import com.example.apk.signing.SigningManager
import com.example.release.diana.DianaCiAnalyzer
import com.example.release.model.ChangePlanStatus
import com.example.release.model.CiErrorCategory
import com.example.release.model.CiFailureRecord
import com.example.release.model.WorkflowRunStatus
import com.example.release.service.CiWorkflowState
import com.example.release.service.GitHubDeploymentProvider
import com.example.release.service.ReleaseEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CiReleasePipelineTest {

    private lateinit var context: Context
    private lateinit var signingManager: SigningManager
    private lateinit var releaseEngine: ReleaseEngine
    private lateinit var dianaCiAnalyzer: DianaCiAnalyzer
    private lateinit var gitHubProvider: GitHubDeploymentProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        signingManager = SigningManager(context)
        gitHubProvider = GitHubDeploymentProvider()
        releaseEngine = ReleaseEngine(context, signingManager, gitHubProvider)
        dianaCiAnalyzer = DianaCiAnalyzer()
    }

    @Test
    fun testReleaseEngine_BuildManifestAndVerification() = runBlocking {
        // Build sample test APK
        val buildManager = ApkBuildManager(context, signingManager)
        val config = ProjectBuildConfig(
            projectName = "ReleaseVerificationApp",
            packageName = "com.aistudio.apkworkbench.wb7x",
            versionName = "1.0.0",
            versionCode = 100L,
            buildType = BuildType.RELEASE,
            targetAbis = listOf(AbiOption.ARM64_V8A)
        )

        val buildResult = buildManager.executeBuild(config)
        assertTrue("Build result must be successful", buildResult.isSuccess)
        assertNotNull("Generated APK must not be null", buildResult.outputApkFile)

        val manifestResult = releaseEngine.buildReleaseManifest(
            apkFile = buildResult.outputApkFile!!,
            versionName = "1.0.0",
            versionCode = 100L
        )

        assertTrue("Release manifest generation must succeed", manifestResult.isSuccess)
        val manifest = manifestResult.getOrThrow()

        assertEquals("EFRAIM APK WORKBENCH TOOL M", manifest.productName)
        assertEquals("com.aistudio.apkworkbench.wb7x", manifest.applicationId)
        assertEquals("1.0.0", manifest.versionName)
        assertEquals(100L, manifest.versionCode)
        assertEquals(1, manifest.artifacts.size)

        val artifact = manifest.artifacts[0]
        assertEquals("APK", artifact.artifactType)
        assertTrue("Artifact size must be > 0", artifact.sizeBytes > 0)
        assertEquals(64, artifact.sha256.length)
        assertTrue("Artifact should be signed", artifact.isSigned)
    }

    @Test
    fun testDianaCiAnalyzer_AllCategoriesDiagnosis() = runBlocking {
        // 1. SIGNING_ERROR
        val signingError = CiFailureRecord(
            category = CiErrorCategory.SIGNING_ERROR,
            stageName = "Signing Setup",
            summary = "Keystore password or secret missing",
            logSnippet = "jarsigner: certificate chain not found in environment"
        )
        val signingAnalysis = dianaCiAnalyzer.analyzeCiFailure(signingError)
        assertEquals("CRITICAL", signingAnalysis.severity)
        assertNotNull(signingAnalysis.proposedChangePlan)
        assertEquals("Configure Release Signing Secrets", signingAnalysis.proposedChangePlan?.title)

        // 2. TEST_ERROR
        val testError = CiFailureRecord(
            category = CiErrorCategory.TEST_ERROR,
            stageName = "Execute Tests",
            summary = "Assertion failed in ExampleRobolectricTest",
            logSnippet = "expected: EFRAIM APK WORKBENCH TOOL M but was: Old Name"
        )
        val testAnalysis = dianaCiAnalyzer.analyzeCiFailure(testError)
        assertEquals("HIGH", testAnalysis.severity)
        assertNotNull(testAnalysis.proposedChangePlan)

        // 3. BUILD_ERROR
        val buildError = CiFailureRecord(
            category = CiErrorCategory.BUILD_ERROR,
            stageName = "Build Android Release",
            summary = "Compilation failed with AGP toolchain",
            logSnippet = "Task :app:compileReleaseKotlin failed"
        )
        val buildAnalysis = dianaCiAnalyzer.analyzeCiFailure(buildError)
        assertEquals("CRITICAL", buildAnalysis.severity)
        assertNotNull(buildAnalysis.proposedChangePlan)

        // 4. CONFIGURATION_ERROR
        val configError = CiFailureRecord(
            category = CiErrorCategory.CONFIGURATION_ERROR,
            stageName = "Detect and Validate",
            summary = "Application ID mismatch",
            logSnippet = "applicationId does not match release configuration"
        )
        val configAnalysis = dianaCiAnalyzer.analyzeCiFailure(configError)
        assertEquals("MEDIUM", configAnalysis.severity)

        // 5. ARTIFACT_ERROR
        val artifactError = CiFailureRecord(
            category = CiErrorCategory.ARTIFACT_ERROR,
            stageName = "Artifact Validation",
            summary = "APK missing from output_artifacts",
            logSnippet = "File not found: output_artifacts/*.apk"
        )
        val artifactAnalysis = dianaCiAnalyzer.analyzeCiFailure(artifactError)
        assertEquals("HIGH", artifactAnalysis.severity)

        // 6. RELEASE_ERROR
        val releaseError = CiFailureRecord(
            category = CiErrorCategory.RELEASE_ERROR,
            stageName = "Publish GitHub Release",
            summary = "Permission denied for GITHUB_TOKEN",
            logSnippet = "HTTP 403: Resource not accessible by integration"
        )
        val releaseAnalysis = dianaCiAnalyzer.analyzeCiFailure(releaseError)
        assertEquals("CRITICAL", releaseAnalysis.severity)
    }

    @Test
    fun testGitHubDeploymentProvider_AuthValidation() = runBlocking {
        // Without token, checkConnection must gracefully fail requiring token
        val res = gitHubProvider.checkConnection("efraim-user", "efraim-apk-workbench", null)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("GitHub token is not configured") == true)
    }

    @Test
    fun testWorkflowFileExistsAndValid() {
        var workflowFile = File(".github/workflows/android-release.yml")
        if (!workflowFile.exists()) {
            workflowFile = File("../.github/workflows/android-release.yml")
        }
        assertTrue("Workflow file must exist at .github/workflows/android-release.yml", workflowFile.exists())
        val content = workflowFile.readText()
        assertTrue(content.contains("EFRAIM APK Workbench Android Release Pipeline"))
        assertTrue(content.contains("temurin"))
        assertTrue(content.contains("release-metadata.json"))
        assertTrue(content.contains("efraim-apk-workbench-release.apk"))
    }
}
