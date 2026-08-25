package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.diagnostics.diana.DianaDiagnosticsAnalyzer
import com.example.diagnostics.engine.AuditLogService
import com.example.diagnostics.engine.DiagnosticsEngine
import com.example.diagnostics.engine.ErrorCenter
import com.example.diagnostics.engine.GlobalErrorHandler
import com.example.diagnostics.engine.HealthEngine
import com.example.diagnostics.engine.IncidentManager
import com.example.diagnostics.engine.NotificationService
import com.example.diagnostics.engine.PerformanceMonitor
import com.example.diagnostics.model.ErrorCategory
import com.example.diagnostics.model.ErrorSeverity
import com.example.diagnostics.model.ErrorStatus
import com.example.diagnostics.model.HealthState
import com.example.diagnostics.model.IncidentStatus
import com.example.diagnostics.model.PerformanceCategory
import com.example.diagnostics.model.PerformanceRating
import com.example.diagnostics.model.UserRole
import com.example.diagnostics.redactor.SensitiveDataRedactor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductionMonitoringDiagnosticsTest {

    private lateinit var context: Context
    private lateinit var diagnosticsEngine: DiagnosticsEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        diagnosticsEngine = DiagnosticsEngine(context, appVersion = "1.0.0", buildNumber = "100")
    }

    @Test
    fun testSensitiveDataRedactor_RedactsCredentials() {
        val rawLog = "Error with api_key=AIzaSyD-1234567890abcdef and password: superSecretPassword123 " +
                "using Bearer ghp_1234567890abcdefghij and keystorePass: MyKeyPass456"
        val sanitized = SensitiveDataRedactor.redact(rawLog)

        assertFalse("Raw password should not appear", sanitized.contains("superSecretPassword123"))
        assertFalse("Raw API key should not appear", sanitized.contains("AIzaSyD-1234567890abcdef"))
        assertFalse("Raw token should not appear", sanitized.contains("ghp_1234567890abcdefghij"))
        assertFalse("Raw keystore pass should not appear", sanitized.contains("MyKeyPass456"))
        assertTrue("Should contain [REDACTED]", sanitized.contains("[REDACTED]"))
    }

    @Test
    fun testErrorCenter_RecordAndTrending() {
        val errorCenter = diagnosticsEngine.errorCenter

        val err1 = errorCenter.recordError(
            category = ErrorCategory.RUNTIME,
            severity = ErrorSeverity.CRITICAL,
            message = "Uncaught NullPointerException in ApkScannerService",
            module = "ApkScannerService",
            stackTrace = "java.lang.NullPointerException: Null path"
        )
        assertNotNull(err1)
        assertEquals("Should be in NEW status", ErrorStatus.NEW, err1.status)

        val err2 = errorCenter.recordError(
            category = ErrorCategory.APK_SCAN,
            severity = ErrorSeverity.ERROR,
            message = "Failed to parse malformed DEX header",
            module = "ApkReader",
            stackTrace = "DexParseException: invalid magic"
        )
        assertNotNull(err2)

        val errors = errorCenter.errors.value
        assertEquals(2, errors.size)

        // Test status update
        val updated = errorCenter.updateStatus(err1.id, ErrorStatus.RESOLVED, "Fixed null check")
        assertTrue(updated)
        assertEquals(ErrorStatus.RESOLVED, errorCenter.errors.value.find { it.id == err1.id }?.status)

        // Test trending metrics
        val stats = errorCenter.getTrendingStats()
        assertEquals(2, stats.totalErrors)
        assertEquals(1, stats.resolvedErrors)
        assertEquals(1, stats.criticalErrors)
    }

    @Test
    fun testPerformanceMonitor_TimingAndRatings() {
        val monitor = diagnosticsEngine.performanceMonitor

        val recFast = monitor.recordMeasurement(
            category = PerformanceCategory.APK_SCAN,
            durationMs = 85L,
            contextInfo = "5MB APK"
        )
        assertEquals(PerformanceRating.FAST, recFast.rating)

        val recSlow = monitor.recordMeasurement(
            category = PerformanceCategory.REBUILD,
            durationMs = 15000L,
            contextInfo = "Heavy DEX repacking"
        )
        assertEquals(PerformanceRating.SLOW, recSlow.rating)

        val metrics = monitor.metrics.value
        assertEquals(2, metrics.size)
    }

    @Test
    fun testHealthEngine_FullComponentCheck() = runBlocking {
        val healthEngine = diagnosticsEngine.healthEngine
        val report = healthEngine.runFullHealthCheck(
            isDianaLoaded = true,
            ciStatus = "SUCCESS",
            hasKeystore = true
        )

        assertNotNull(report)
        assertTrue("Report must have evaluated components", report.components.isNotEmpty())
        assertEquals(9, report.components.size)

        // Check individual component states
        val appComp = report.components.find { it.name == "APPLICATION" }
        assertNotNull(appComp)
        assertEquals(HealthState.HEALTHY, appComp?.state)

        val dianaComp = report.components.find { it.name.startsWith("DIANA") }
        assertNotNull(dianaComp)
        assertEquals(HealthState.HEALTHY, dianaComp?.state)

        val dbComp = report.components.find { it.name == "DATABASE" }
        assertNotNull(dbComp)
        assertEquals(HealthState.HEALTHY, dbComp?.state)
    }

    @Test
    fun testIncidentManager_LifecycleAndTimeline() {
        val incidentManager = diagnosticsEngine.incidentManager
        val incident = incidentManager.createIncident(
            title = "DEX Alignment Regression in v1.0",
            severity = ErrorSeverity.CRITICAL,
            affectedModule = "ApkRebuildPipeline",
            affectedRelease = "1.0.0",
            initialDescription = "4-byte zip alignment failed during rebuild"
        )

        assertNotNull(incident)
        assertEquals(IncidentStatus.OPEN, incident.status)
        assertEquals(1, incident.timeline.size)
        assertTrue(incident.timeline[0].stage.equals("DETECTION", ignoreCase = true))

        // Transition to INVESTIGATING
        val updatedInv = incidentManager.updateIncidentStatus(
            incidentId = incident.id,
            newStatus = IncidentStatus.INVESTIGATING,
            author = "DIANA_AI"
        )
        assertTrue(updatedInv)
        val currentInc = incidentManager.incidents.value.find { it.id == incident.id }
        assertEquals(IncidentStatus.INVESTIGATING, currentInc?.status)
        assertEquals(2, currentInc?.timeline?.size)

        // Transition to RESOLVED
        val updatedRes = incidentManager.updateIncidentStatus(
            incidentId = incident.id,
            newStatus = IncidentStatus.RESOLVED,
            rootCause = "ZipEntry zero-fill alignment logic corrected",
            resolution = "Applied patch in RebuildPipeline",
            author = "SecurityLead"
        )
        assertTrue(updatedRes)
        val resolvedInc = incidentManager.incidents.value.find { it.id == incident.id }
        assertEquals(IncidentStatus.RESOLVED, resolvedInc?.status)
    }

    @Test
    fun testDianaDiagnosticsAnalyzer_RootCauseAndProposedChangePlan() {
        val analyzer = diagnosticsEngine.dianaDiagnosticsAnalyzer
        val err = diagnosticsEngine.errorCenter.recordError(
            category = ErrorCategory.APK_SCAN,
            severity = ErrorSeverity.CRITICAL,
            message = "Hardcoded AWS Access Key detected in AndroidManifest.xml: AKIAIOSFODNN7EXAMPLE",
            module = "ApkScannerService"
        )

        val report = analyzer.analyzeError(err)
        assertNotNull(report)
        assertTrue("Report summary should be present", report.summary.isNotBlank())
        assertTrue("Root cause should be diagnosed", report.rootCause.isNotBlank())
        assertNotNull("Suggested ChangePlan should be generated", report.suggestedChangePlan)

        // Verify that ChangePlan requires user approval and is NOT silently applied
        val plan = report.suggestedChangePlan!!
        assertEquals("Plan must start in PENDING status", com.example.release.model.ChangePlanStatus.PENDING, plan.status)
    }

    @Test
    fun testExportDiagnosticReport_JsonAndText() {
        diagnosticsEngine.reportError(
            category = ErrorCategory.RUNTIME,
            severity = ErrorSeverity.WARNING,
            message = "Disk buffer threshold reached with api_key=secretKey999",
            module = "StorageManager"
        )

        val jsonReport = diagnosticsEngine.exportReportAsJson()
        assertTrue("JSON export must be non-empty", jsonReport.isNotBlank())
        assertFalse("Sensitive token must be redacted from JSON export", jsonReport.contains("secretKey999"))
        assertTrue("Should contain redacted string", jsonReport.contains("[REDACTED]"))

        val textReport = diagnosticsEngine.exportReportAsText()
        assertTrue("Text export must be non-empty", textReport.isNotBlank())
        assertFalse("Sensitive token must be redacted from TXT export", textReport.contains("secretKey999"))
        assertTrue("Text export should format sections", textReport.contains("EFRAIM") && textReport.contains("DIAGNOSTIC REPORT"))
    }

    @Test
    fun testUserRoleAndAuditLog() {
        val audit = diagnosticsEngine.auditLogService
        assertEquals(UserRole.OWNER, diagnosticsEngine.currentUserRole.value)

        diagnosticsEngine.setUserRole(UserRole.EDITOR)
        assertEquals(UserRole.EDITOR, diagnosticsEngine.currentUserRole.value)

        val logs = audit.logs.value
        assertTrue("Audit log should capture role change", logs.any { it.action.contains("ROLE") })
    }
}
