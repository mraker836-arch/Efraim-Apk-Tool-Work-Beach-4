package com.example.diagnostics.model

import java.util.UUID

enum class ErrorSeverity(val label: String) {
    CRITICAL("CRITICAL"),
    ERROR("ERROR"),
    WARNING("WARNING"),
    INFO("INFO")
}

enum class ErrorCategory(val label: String) {
    BUILD("Build"),
    RUNTIME("Runtime"),
    APK_IMPORT("APK Import"),
    APK_SCAN("APK Scan"),
    APK_ANALYSIS("APK Analysis"),
    REBUILD("Rebuild"),
    SIGNING("Signing"),
    VERIFICATION("Verification"),
    EXPORT("Export"),
    DATABASE("Database"),
    NETWORK("Network"),
    AI("AI / Inference"),
    GITHUB_ACTIONS("GitHub Actions"),
    DEPLOYMENT("Deployment"),
    UI("User Interface")
}

enum class ErrorStatus(val label: String) {
    NEW("New"),
    INVESTIGATING("Investigating"),
    RESOLVED("Resolved"),
    IGNORED("Ignored")
}

data class DiagnosticErrorRecord(
    val id: String = UUID.randomUUID().toString(),
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val module: String,
    val releaseVersion: String = "1.0",
    val buildVersion: String = "1",
    val stackTrace: String = "",
    val status: ErrorStatus = ErrorStatus.NEW,
    val resolvedAt: Long? = null,
    val resolutionNotes: String = ""
)

data class CrashReport(
    val id: String = UUID.randomUUID().toString(),
    val exceptionType: String,
    val safeMessage: String,
    val stackTrace: String,
    val module: String,
    val applicationVersion: String = "1.0",
    val buildNumber: String = "1",
    val platform: String = "Android API " + android.os.Build.VERSION.SDK_INT,
    val timestamp: Long = System.currentTimeMillis()
)

data class ApkTelemetryRecord(
    val id: String = UUID.randomUUID().toString(),
    val operationType: String, // "IMPORT", "SCAN", "INSPECT", "REBUILD", "SIGN", "VERIFY", "EXPORT"
    val durationMs: Long,
    val success: Boolean,
    val artifactSizeBytes: Long = 0L,
    val module: String = "ApkLab",
    val applicationVersion: String = "1.0",
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PerformanceCategory(val label: String) {
    STARTUP("Application Startup"),
    SCREEN_LOAD("Screen Load"),
    APK_IMPORT("APK Import"),
    APK_SCAN("APK Scan"),
    APK_ANALYSIS("APK Analysis"),
    REBUILD("APK Rebuild"),
    EXPORT("Artifact Export"),
    AI_RESPONSE("AI / DIANA Response")
}

enum class PerformanceRating(val label: String) {
    FAST("FAST"),
    NORMAL("NORMAL"),
    SLOW("SLOW"),
    FAILED("FAILED"),
    NOT_AVAILABLE("NOT_AVAILABLE")
}

data class PerformanceMetricRecord(
    val id: String = UUID.randomUUID().toString(),
    val category: PerformanceCategory,
    val durationMs: Long,
    val rating: PerformanceRating,
    val contextInfo: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class PerformanceThresholds(
    val startupFastMs: Long = 1200L,
    val startupNormalMs: Long = 2500L,
    val apkImportFastMs: Long = 800L,
    val apkImportNormalMs: Long = 2000L,
    val apkScanFastMs: Long = 1000L,
    val apkScanNormalMs: Long = 3000L,
    val apkAnalysisFastMs: Long = 1500L,
    val apkAnalysisNormalMs: Long = 4000L,
    val rebuildFastMs: Long = 2500L,
    val rebuildNormalMs: Long = 6000L,
    val exportFastMs: Long = 500L,
    val exportNormalMs: Long = 1500L,
    val aiResponseFastMs: Long = 2000L,
    val aiResponseNormalMs: Long = 5000L
)

enum class HealthState(val symbol: String, val label: String) {
    HEALTHY("✓", "HEALTHY"),
    DEGRADED("⚠", "DEGRADED"),
    UNHEALTHY("✕", "UNHEALTHY"),
    UNKNOWN("?", "UNKNOWN")
}

data class ComponentHealth(
    val name: String,
    val state: HealthState,
    val details: String,
    val lastChecked: Long = System.currentTimeMillis()
)

data class SystemHealthReport(
    val overallState: HealthState = HealthState.HEALTHY,
    val components: List<ComponentHealth> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class ReleaseHealthSummary(
    val versionName: String = "1.0",
    val versionCode: Long = 1L,
    val buildStatus: String = "PASS",
    val testStatus: String = "PASS",
    val signingStatus: String = "VERIFIED",
    val releaseStatus: String = "PUBLISHED",
    val errorCount: Int = 0,
    val criticalErrorCount: Int = 0,
    val deploymentStatus: String = "HEALTHY",
    val regressions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class IncidentStatus(val label: String) {
    OPEN("Open"),
    INVESTIGATING("Investigating"),
    MITIGATED("Mitigated"),
    RESOLVED("Resolved")
}

data class IncidentTimelineEvent(
    val stage: String, // "Detection", "Investigation", "Root Cause", "Fix", "Verification", "Resolution"
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val author: String = "System"
)

data class Incident(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val severity: ErrorSeverity,
    val startedAt: Long = System.currentTimeMillis(),
    var resolvedAt: Long? = null,
    val affectedRelease: String = "1.0",
    val affectedModule: String,
    var status: IncidentStatus = IncidentStatus.OPEN,
    var rootCause: String = "",
    var resolution: String = "",
    val timeline: MutableList<IncidentTimelineEvent> = mutableListOf()
)

enum class AlertType(val label: String) {
    CRITICAL_ERROR("Critical Error"),
    BUILD_FAILURE("Build Failure"),
    TEST_FAILURE("Test Failure"),
    SIGNING_FAILURE("Signing Failure"),
    RELEASE_FAILURE("Release Failure"),
    PERFORMANCE_REGRESSION("Performance Regression"),
    APK_PROCESSING_FAILURE("APK Processing Failure"),
    SYSTEM_DEGRADED("System Degraded")
}

data class AlertNotification(
    val id: String = UUID.randomUUID().toString(),
    val type: AlertType,
    val severity: ErrorSeverity,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

data class ObservabilitySettings(
    val diagnosticsEnabled: Boolean = true,
    val performanceMonitoringEnabled: Boolean = true,
    val crashReportingEnabled: Boolean = true,
    val inAppErrorHistoryEnabled: Boolean = true,
    val logRetentionDays: Int = 30
)

enum class DiagnosticPermission {
    DIAGNOSTICS_READ,
    DIAGNOSTICS_EXPORT,
    INCIDENT_CREATE,
    INCIDENT_UPDATE,
    RELEASE_HEALTH_READ
}

enum class UserRole {
    OWNER,
    EDITOR,
    VIEWER;

    fun hasPermission(permission: DiagnosticPermission): Boolean {
        return when (this) {
            OWNER -> true
            EDITOR -> permission != DiagnosticPermission.DIAGNOSTICS_EXPORT
            VIEWER -> permission == DiagnosticPermission.DIAGNOSTICS_READ || permission == DiagnosticPermission.RELEASE_HEALTH_READ
        }
    }
}
