package com.example.apk.model

enum class ScanFilter {
    ALL,
    COMPLETED,
    FAILED,
    SECURITY_ISSUES,
    RECENT
}

enum class ScanSortOption {
    TIMESTAMP_DESC,
    TIMESTAMP_ASC,
    FILE_SIZE_DESC,
    FILE_SIZE_ASC,
    FINDINGS_COUNT_DESC,
    NAME_ASC
}

data class DashboardStatistics(
    val totalScans: Int = 0,
    val successfulScans: Int = 0,
    val failedScans: Int = 0,
    val apksAnalyzed: Int = 0,
    val totalSecurityFindings: Int = 0,
    val highSeverityFindings: Int = 0,
    val dexFilesDetected: Int = 0,
    val nativeLibrariesDetected: Int = 0,
    val isEmpty: Boolean = true
)
