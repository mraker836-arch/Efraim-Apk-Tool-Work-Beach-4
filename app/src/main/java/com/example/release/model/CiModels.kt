package com.example.release.model

import java.util.UUID

enum class CiErrorCategory(val label: String, val severity: String) {
    BUILD_ERROR("Build Error", "CRITICAL"),
    TEST_ERROR("Test Failure", "HIGH"),
    SIGNING_ERROR("Signing / Keystore Error", "CRITICAL"),
    CONFIGURATION_ERROR("Configuration Mismatch", "MEDIUM"),
    ARTIFACT_ERROR("Artifact Validation Error", "HIGH"),
    RELEASE_ERROR("GitHub Release Error", "CRITICAL")
}

data class CiFailureRecord(
    val id: String = UUID.randomUUID().toString(),
    val category: CiErrorCategory,
    val stageName: String,
    val summary: String,
    val logSnippet: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false,
    val suggestedRemediation: String = ""
)

enum class WorkflowRunStatus {
    IDLE,
    QUEUED,
    IN_PROGRESS,
    BUILDING,
    TESTING,
    SIGNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ReleaseArtifactInfo(
    val fileName: String,
    val artifactType: String, // "APK", "AAB", "SHA256", "METADATA"
    val sizeBytes: Long,
    val sha256: String,
    val isSigned: Boolean,
    val downloadUrl: String = ""
)

data class ReleaseMetadata(
    val productName: String = "EFRAIM APK WORKBENCH TOOL M",
    val shortName: String = "EFRAIM APK WORKBENCH",
    val applicationId: String = "com.aistudio.apkworkbench.wb7x",
    val versionName: String = "1.0",
    val versionCode: Long = 1L,
    val buildTimestamp: Long = System.currentTimeMillis(),
    val gitCommitSha: String = "HEAD",
    val gitRef: String = "refs/heads/main",
    val artifacts: List<ReleaseArtifactInfo> = emptyList()
)

enum class ChangePlanStatus {
    PENDING,
    APPROVED,
    REJECTED,
    APPLIED
}

data class ChangePlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val affectedFiles: List<String>,
    val diffPreview: String,
    val proposedAction: String,
    val status: ChangePlanStatus = ChangePlanStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
