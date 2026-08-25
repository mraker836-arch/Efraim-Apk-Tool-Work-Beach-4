package com.example.apk.model

enum class BuildStatus {
    IDLE,
    IMPORTING,
    SCANNING,
    ANALYZING,
    PREPARING,
    REBUILDING,
    ALIGNING,
    SIGNING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class ChangeType {
    APPLICATION_LABEL,
    VERSION_NAME,
    VERSION_CODE,
    MIN_SDK,
    TARGET_SDK,
    DEBUGGABLE_FLAG,
    ALLOW_BACKUP,
    REPLACE_ASSET,
    REPLACE_RESOURCE,
    INJECT_CONFIG,
    SIGNING_KEY
}

data class BuildChange(
    val type: ChangeType,
    val description: String,
    val targetKey: String,
    val oldValue: String,
    val newValue: String
)

data class BuildPlan(
    val id: String,
    val inputApkName: String,
    val inputApkPath: String,
    val outputApkName: String,
    val outputApkPath: String,
    val changes: List<BuildChange>,
    val createdAt: Long = System.currentTimeMillis()
)

data class BuildLog(
    val timestamp: Long = System.currentTimeMillis(),
    val stage: BuildStatus,
    val message: String,
    val isError: Boolean = false,
    val detail: String? = null
)

data class CustomAssetItem(
    val relativePath: String,
    val contentString: String,
    val isBinary: Boolean = false
)

data class RebuildConfig(
    val newAppName: String,
    val newVersionName: String,
    val newVersionCode: Long,
    val newMinSdk: Int = 24,
    val newTargetSdk: Int = 36,
    val setDebuggable: Boolean? = null,
    val customAssets: List<CustomAssetItem> = emptyList(),
    val resourceReplacements: Map<String, String> = emptyMap(),
    val keyAlias: String = "apk_workbench_key",
    val keyPassword: String = "workbench_pass",
    val keystorePassword: String = "workbench_pass",
    val alignApk: Boolean = true,
    val signApk: Boolean = true
)

data class BuildResult(
    val isSuccess: Boolean,
    val outputApkFile: java.io.File? = null,
    val outputSha256: String = "",
    val outputFileSize: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
    val logs: List<BuildLog> = emptyList(),
    val signatureVerified: Boolean = false
)

enum class BuildType {
    DEBUG,
    RELEASE
}

enum class AbiOption(val label: String, val folderName: String) {
    ARM64_V8A("ARM 64-bit (arm64-v8a)", "arm64-v8a"),
    ARMEABI_V7A("ARM 32-bit (armeabi-v7a)", "armeabi-v7a"),
    X86_64("x86 64-bit (x86_64)", "x86_64"),
    X86("x86 32-bit (x86)", "x86"),
    UNIVERSAL("Universal (All ABIs)", "universal")
}
