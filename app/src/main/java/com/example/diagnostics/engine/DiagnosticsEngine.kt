package com.example.diagnostics.engine

import android.content.Context
import com.example.diagnostics.diana.DianaDiagnosticsAnalyzer
import com.example.diagnostics.model.AlertNotification
import com.example.diagnostics.model.AlertType
import com.example.diagnostics.model.ApkTelemetryRecord
import com.example.diagnostics.model.CrashReport
import com.example.diagnostics.model.DiagnosticErrorRecord
import com.example.diagnostics.model.DiagnosticPermission
import com.example.diagnostics.model.ErrorCategory
import com.example.diagnostics.model.ErrorSeverity
import com.example.diagnostics.model.ErrorStatus
import com.example.diagnostics.model.Incident
import com.example.diagnostics.model.IncidentStatus
import com.example.diagnostics.model.ObservabilitySettings
import com.example.diagnostics.model.PerformanceCategory
import com.example.diagnostics.model.PerformanceMetricRecord
import com.example.diagnostics.model.ReleaseHealthSummary
import com.example.diagnostics.model.SystemHealthReport
import com.example.diagnostics.model.UserRole
import com.example.diagnostics.redactor.SensitiveDataRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

class DiagnosticsEngine(
    private val context: Context,
    val appVersion: String = "1.0",
    val buildNumber: String = "1"
) {

    val errorCenter = ErrorCenter()
    val performanceMonitor = PerformanceMonitor()
    val healthEngine = HealthEngine(context)
    val incidentManager = IncidentManager()
    val notificationService = NotificationService()
    val auditLogService = AuditLogService()
    val dianaDiagnosticsAnalyzer = DianaDiagnosticsAnalyzer()
    val globalErrorHandler = GlobalErrorHandler(errorCenter, appVersion, buildNumber)

    private val _settings = MutableStateFlow(ObservabilitySettings())
    val settings: StateFlow<ObservabilitySettings> = _settings.asStateFlow()

    private val _currentUserRole = MutableStateFlow(UserRole.OWNER)
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    private val _apkTelemetry = MutableStateFlow<List<ApkTelemetryRecord>>(emptyList())
    val apkTelemetry: StateFlow<List<ApkTelemetryRecord>> = _apkTelemetry.asStateFlow()
    private val apkTelemetryList = CopyOnWriteArrayList<ApkTelemetryRecord>()

    private val _releaseHistory = MutableStateFlow<List<ReleaseHealthSummary>>(
        listOf(
            ReleaseHealthSummary(
                versionName = "1.0",
                versionCode = 1L,
                buildStatus = "PASS",
                testStatus = "PASS",
                signingStatus = "VERIFIED",
                releaseStatus = "PUBLISHED",
                errorCount = 0,
                criticalErrorCount = 0,
                deploymentStatus = "HEALTHY"
            )
        )
    )
    val releaseHistory: StateFlow<List<ReleaseHealthSummary>> = _releaseHistory.asStateFlow()

    init {
        // Safe installation of crash handler
        try {
            globalErrorHandler.install()
        } catch (e: Throwable) {
            // Never fail initialization
        }
    }

    fun setUserRole(role: UserRole) {
        _currentUserRole.value = role
        auditLogService.logEvent(
            action = "USER_ROLE_CHANGED",
            details = "Active user role updated to ${role.name}",
            userRole = role.name
        )
    }

    fun updateSettings(newSettings: ObservabilitySettings) {
        if (!_currentUserRole.value.hasPermission(DiagnosticPermission.INCIDENT_UPDATE)) {
            return
        }
        _settings.value = newSettings
        auditLogService.logEvent(
            action = "SETTINGS_UPDATED",
            details = "Observability settings updated (Diagnostics: ${newSettings.diagnosticsEnabled}, CrashReporting: ${newSettings.crashReportingEnabled})",
            userRole = _currentUserRole.value.name
        )
    }

    /**
     * Reports an error safely through the DiagnosticsEngine to ErrorCenter.
     * Prevents duplicates and logs audit info.
     */
    fun reportError(
        category: ErrorCategory,
        severity: ErrorSeverity,
        message: String,
        module: String,
        stackTrace: String = ""
    ): DiagnosticErrorRecord? {
        if (!_settings.value.diagnosticsEnabled && severity != ErrorSeverity.CRITICAL) {
            return null
        }

        return try {
            val record = errorCenter.recordError(
                category = category,
                severity = severity,
                message = message,
                module = module,
                stackTrace = stackTrace,
                releaseVersion = appVersion,
                buildVersion = buildNumber
            )

            // Evaluate automatic incident triggers
            val triggeredIncidents = incidentManager.evaluateTriggers(errorCenter.errors.value)
            for (inc in triggeredIncidents) {
                auditLogService.logEvent(
                    action = "INCIDENT_AUTOMATICALLY_TRIGGERED",
                    details = "Incident [${inc.title}] auto-created due to telemetry threshold",
                    userRole = "SYSTEM"
                )
            }

            // Post notification if critical or error
            if (severity == ErrorSeverity.CRITICAL) {
                notificationService.postAlert(
                    type = AlertType.CRITICAL_ERROR,
                    severity = severity,
                    title = "Critical Error in $module",
                    message = record.message
                )
            }

            record
        } catch (e: Throwable) {
            // Diagnostics must never cause calling code to fail
            null
        }
    }

    /**
     * Tracks safe telemetry metrics for an APK processing operation.
     */
    fun trackApkOperation(
        operationType: String,
        durationMs: Long,
        success: Boolean,
        artifactSizeBytes: Long = 0L,
        module: String = "ApkLab",
        errorMessage: String? = null
    ) {
        if (!_settings.value.diagnosticsEnabled) return
        try {
            val safeError = errorMessage?.let { SensitiveDataRedactor.redact(it) }
            val record = ApkTelemetryRecord(
                operationType = operationType,
                durationMs = durationMs,
                success = success,
                artifactSizeBytes = artifactSizeBytes,
                module = module,
                applicationVersion = appVersion,
                errorMessage = safeError
            )
            apkTelemetryList.add(0, record)
            _apkTelemetry.value = apkTelemetryList.toList()

            // Also record performance timing
            val perfCat = when (operationType.uppercase()) {
                "IMPORT" -> PerformanceCategory.APK_IMPORT
                "SCAN" -> PerformanceCategory.APK_SCAN
                "ANALYSIS" -> PerformanceCategory.APK_ANALYSIS
                "REBUILD" -> PerformanceCategory.REBUILD
                "EXPORT" -> PerformanceCategory.EXPORT
                else -> PerformanceCategory.SCREEN_LOAD
            }
            performanceMonitor.recordMeasurement(
                category = perfCat,
                durationMs = durationMs,
                contextInfo = "Artifact size: ${artifactSizeBytes / 1024} KB",
                isFailure = !success
            )

            if (!success && errorMessage != null) {
                val errCat = when (operationType.uppercase()) {
                    "IMPORT" -> ErrorCategory.APK_IMPORT
                    "SCAN" -> ErrorCategory.APK_SCAN
                    "ANALYSIS" -> ErrorCategory.APK_ANALYSIS
                    "REBUILD" -> ErrorCategory.REBUILD
                    "SIGN" -> ErrorCategory.SIGNING
                    "VERIFY" -> ErrorCategory.VERIFICATION
                    "EXPORT" -> ErrorCategory.EXPORT
                    else -> ErrorCategory.RUNTIME
                }
                reportError(
                    category = errCat,
                    severity = ErrorSeverity.ERROR,
                    message = "$operationType failed: $errorMessage",
                    module = module
                )
            }
        } catch (e: Throwable) {
            // Ignore telemetry failure safely
        }
    }

    fun recordReleaseHealth(
        versionName: String,
        versionCode: Long,
        buildStatus: String,
        testStatus: String,
        signingStatus: String,
        releaseStatus: String,
        deploymentStatus: String
    ): ReleaseHealthSummary {
        val currentErrors = errorCenter.errors.value
        val errCount = currentErrors.count { it.releaseVersion == versionName }
        val critCount = currentErrors.count { it.releaseVersion == versionName && it.severity == ErrorSeverity.CRITICAL }

        // Compare with previous release for regressions
        val previousRelease = _releaseHistory.value.firstOrNull()
        val regressions = mutableListOf<String>()

        if (previousRelease != null) {
            if (critCount > previousRelease.criticalErrorCount) {
                regressions.add("Critical errors increased ($critCount vs ${previousRelease.criticalErrorCount} in ${previousRelease.versionName})")
            }
            if (buildStatus == "FAILED" && previousRelease.buildStatus == "PASS") {
                regressions.add("Build regression: current build failed")
            }
            if (testStatus == "FAILED" && previousRelease.testStatus == "PASS") {
                regressions.add("Test regression: unit tests failed")
            }
        }

        val summary = ReleaseHealthSummary(
            versionName = versionName,
            versionCode = versionCode,
            buildStatus = buildStatus,
            testStatus = testStatus,
            signingStatus = signingStatus,
            releaseStatus = releaseStatus,
            errorCount = errCount,
            criticalErrorCount = critCount,
            deploymentStatus = deploymentStatus,
            regressions = regressions
        )

        val updated = mutableListOf(summary)
        updated.addAll(_releaseHistory.value.filter { it.versionName != versionName })
        _releaseHistory.value = updated

        auditLogService.logEvent(
            action = "RELEASE_HEALTH_RECORDED",
            details = "Release $versionName health recorded (Build: $buildStatus, Tests: $testStatus, Regressions: ${regressions.size})",
            userRole = _currentUserRole.value.name
        )

        return summary
    }

    /**
     * Clears all local diagnostic data.
     */
    fun clearLocalDiagnosticHistory() {
        errorCenter.clearAllErrors()
        performanceMonitor.clearHistory()
        incidentManager.clearIncidents()
        notificationService.clearAlerts()
        globalErrorHandler.clearCrashes()
        apkTelemetryList.clear()
        _apkTelemetry.value = emptyList()

        auditLogService.logEvent(
            action = "DIAGNOSTICS_CLEARED",
            details = "Local diagnostic history purged by user",
            userRole = _currentUserRole.value.name
        )
    }

    /**
     * Exports full Diagnostic Report as JSON.
     * Sanitizes all sensitive secrets.
     */
    fun exportReportAsJson(): String {
        if (!_currentUserRole.value.hasPermission(DiagnosticPermission.DIAGNOSTICS_EXPORT)) {
            return JSONObject().put("error", "Unauthorized: User does not have DIAGNOSTICS_EXPORT permission").toString(2)
        }

        val root = JSONObject()
        root.put("application", "EFRAIM APK WORKBENCH TOOL M")
        root.put("version", appVersion)
        root.put("buildNumber", buildNumber)
        root.put("exportedAt", System.currentTimeMillis())

        // System Health
        val health = healthEngine.healthReport.value
        val healthObj = JSONObject()
        healthObj.put("overallState", health.overallState.label)
        val compArray = JSONArray()
        for (c in health.components) {
            val co = JSONObject()
            co.put("component", c.name)
            co.put("state", c.state.label)
            co.put("details", SensitiveDataRedactor.redact(c.details))
            compArray.put(co)
        }
        healthObj.put("components", compArray)
        root.put("systemHealth", healthObj)

        // Errors
        val errorsArray = JSONArray()
        for (e in errorCenter.errors.value) {
            val eo = JSONObject()
            eo.put("id", e.id)
            eo.put("category", e.category.name)
            eo.put("severity", e.severity.name)
            eo.put("message", SensitiveDataRedactor.redact(e.message))
            eo.put("module", e.module)
            eo.put("releaseVersion", e.releaseVersion)
            eo.put("status", e.status.name)
            eo.put("timestamp", e.timestamp)
            errorsArray.put(eo)
        }
        root.put("errors", errorsArray)

        // Incidents
        val incidentsArray = JSONArray()
        for (inc in incidentManager.incidents.value) {
            val io = JSONObject()
            io.put("id", inc.id)
            io.put("title", SensitiveDataRedactor.redact(inc.title))
            io.put("severity", inc.severity.name)
            io.put("status", inc.status.name)
            io.put("affectedModule", inc.affectedModule)
            io.put("rootCause", SensitiveDataRedactor.redact(inc.rootCause))
            io.put("resolution", SensitiveDataRedactor.redact(inc.resolution))
            incidentsArray.put(io)
        }
        root.put("incidents", incidentsArray)

        // Releases
        val relArray = JSONArray()
        for (r in _releaseHistory.value) {
            val ro = JSONObject()
            ro.put("versionName", r.versionName)
            ro.put("buildStatus", r.buildStatus)
            ro.put("testStatus", r.testStatus)
            ro.put("signingStatus", r.signingStatus)
            ro.put("releaseStatus", r.releaseStatus)
            ro.put("errorCount", r.errorCount)
            ro.put("criticalErrorCount", r.criticalErrorCount)
            relArray.put(ro)
        }
        root.put("releaseHistory", relArray)

        auditLogService.logEvent(
            action = "DIAGNOSTIC_REPORT_EXPORTED_JSON",
            details = "Full diagnostic report exported in JSON format",
            userRole = _currentUserRole.value.name
        )

        return root.toString(2)
    }

    /**
     * Exports full Diagnostic Report as plain text.
     * Sanitizes all sensitive secrets.
     */
    fun exportReportAsText(): String {
        if (!_currentUserRole.value.hasPermission(DiagnosticPermission.DIAGNOSTICS_EXPORT)) {
            return "UNAUTHORIZED: User does not have DIAGNOSTICS_EXPORT permission"
        }

        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("EFRAIM APK WORKBENCH TOOL M — DIAGNOSTIC REPORT")
        sb.appendLine("==================================================")
        sb.appendLine("Application Version: $appVersion (Build $buildNumber)")
        sb.appendLine("Generated At: ${java.util.Date()}")
        sb.appendLine()

        sb.appendLine("--- 1. SYSTEM HEALTH ---")
        val health = healthEngine.healthReport.value
        sb.appendLine("Overall Status: ${health.overallState.symbol} ${health.overallState.label}")
        for (c in health.components) {
            sb.appendLine("  [${c.state.symbol}] ${c.name}: ${SensitiveDataRedactor.redact(c.details)}")
        }
        sb.appendLine()

        sb.appendLine("--- 2. ERROR SUMMARY ---")
        val stats = errorCenter.getTrendingStats()
        if (stats.hasSufficientData) {
            sb.appendLine("Total Errors: ${stats.totalErrors} (Critical: ${stats.criticalErrors}, Regular: ${stats.regularErrors}, Warnings: ${stats.warnings})")
            sb.appendLine("Resolved: ${stats.resolvedErrors} | Unresolved: ${stats.unresolvedErrors}")
        } else {
            sb.appendLine("No errors recorded (INSUFFICIENT DATA)")
        }
        sb.appendLine()

        sb.appendLine("--- 3. ACTIVE INCIDENTS ---")
        val incs = incidentManager.incidents.value
        if (incs.isEmpty()) {
            sb.appendLine("No active incidents.")
        } else {
            for (inc in incs) {
                sb.appendLine("• [${inc.status.name}] ${SensitiveDataRedactor.redact(inc.title)} (${inc.severity.name} in ${inc.affectedModule})")
            }
        }
        sb.appendLine()

        sb.appendLine("--- 4. RELEASE HEALTH ---")
        for (r in _releaseHistory.value) {
            sb.appendLine("• Release ${r.versionName} (Build ${r.versionCode})")
            sb.appendLine("  Build: ${r.buildStatus} | Tests: ${r.testStatus} | Signing: ${r.signingStatus} | Status: ${r.releaseStatus}")
            sb.appendLine("  Critical Errors: ${r.criticalErrorCount}")
            if (r.regressions.isNotEmpty()) {
                sb.appendLine("  Regressions: ${r.regressions.joinToString("; ")}")
            }
        }
        sb.appendLine()
        sb.appendLine("==================================================")
        sb.appendLine("END OF REPORT (Sanitized: Secrets Redacted)")
        sb.appendLine("==================================================")

        auditLogService.logEvent(
            action = "DIAGNOSTIC_REPORT_EXPORTED_TXT",
            details = "Full diagnostic report exported in TXT format",
            userRole = _currentUserRole.value.name
        )

        return sb.toString()
    }
}
