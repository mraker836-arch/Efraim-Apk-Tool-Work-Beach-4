package com.example.diagnostics.diana

import com.example.diagnostics.model.DiagnosticErrorRecord
import com.example.diagnostics.model.ErrorCategory
import com.example.diagnostics.model.Incident
import com.example.diagnostics.model.PerformanceMetricRecord
import com.example.diagnostics.model.PerformanceRating
import com.example.diagnostics.model.ReleaseHealthSummary
import com.example.diagnostics.redactor.SensitiveDataRedactor
import com.example.release.model.ChangePlan
import com.example.release.model.ChangePlanStatus
import com.example.release.model.CiErrorCategory
import com.example.release.model.CiFailureRecord
import java.util.UUID

enum class DianaDiagnosticMode {
    DIAGNOSTIC_ANALYSIS,
    ROOT_CAUSE_ANALYSIS,
    PERFORMANCE_ANALYSIS,
    RELEASE_HEALTH_ANALYSIS,
    CI_FAILURE_ANALYSIS
}

data class DianaDiagnosticReport(
    val mode: DianaDiagnosticMode,
    val summary: String,
    val rootCause: String,
    val evidence: List<String>,
    val recommendedFix: String,
    val risk: String, // "LOW", "MEDIUM", "HIGH"
    val confidence: Int, // 0 - 100
    val suggestedChangePlan: ChangePlan? = null
)

class DianaDiagnosticsAnalyzer {

    fun analyzeError(
        errorRecord: DiagnosticErrorRecord
    ): DianaDiagnosticReport {
        val safeMessage = SensitiveDataRedactor.redact(errorRecord.message)
        val safeStackTrace = SensitiveDataRedactor.redact(errorRecord.stackTrace)

        val evidence = mutableListOf<String>()
        evidence.add("Error Category: ${errorRecord.category.label}")
        evidence.add("Severity: ${errorRecord.severity.label}")
        evidence.add("Originating Module: ${errorRecord.module}")
        if (safeStackTrace.isNotEmpty()) {
            evidence.add("Top Stacktrace: ${safeStackTrace.lines().take(3).joinToString(" -> ")}")
        }

        val (summary, rootCause, fix, risk, confidence) = when (errorRecord.category) {
            ErrorCategory.SIGNING -> {
                val cause = "PKCS#7 signature or certificate keystore validation issue in ${errorRecord.module}."
                val recFix = "Verify keystore credentials and re-generate self-signed developer certificate in Settings/APK Lab."
                Quint(
                    "APK Signing Failure in ${errorRecord.module}",
                    cause,
                    recFix,
                    "MEDIUM",
                    90
                )
            }
            ErrorCategory.APK_IMPORT, ErrorCategory.APK_SCAN -> {
                val cause = "Corrupted or non-standard APK zip archive structure: $safeMessage"
                val recFix = "Run AXML/DEX header inspection or verify APK file integrity before loading."
                Quint(
                    "APK Archive Ingestion Error",
                    cause,
                    recFix,
                    "LOW",
                    88
                )
            }
            ErrorCategory.APK_ANALYSIS -> {
                val cause = "DEX bytecode parsing or Manifest XML decoding failed: $safeMessage"
                val recFix = "Ensure target APK uses supported Dalvik/ART bytecode versions (035-039)."
                Quint(
                    "Bytecode / Manifest Analysis Anomaly",
                    cause,
                    recFix,
                    "LOW",
                    85
                )
            }
            ErrorCategory.GITHUB_ACTIONS, ErrorCategory.BUILD -> {
                val cause = "CI/CD Gradle task or environment build failure: $safeMessage"
                val recFix = "Inspect JDK 21 environment and verify Gradle wrapper permissions."
                Quint(
                    "CI/CD Build Pipeline Failure",
                    cause,
                    recFix,
                    "MEDIUM",
                    92
                )
            }
            ErrorCategory.RUNTIME -> {
                val cause = "Uncaught runtime exception: $safeMessage"
                val recFix = "Check nullability safeguards and thread safety within ${errorRecord.module}."
                Quint(
                    "Runtime Exception in ${errorRecord.module}",
                    cause,
                    recFix,
                    "HIGH",
                    86
                )
            }
            else -> {
                if (safeMessage.isEmpty()) {
                    Quint(
                        "Diagnostic Anomaly",
                        "INSUFFICIENT_EVIDENCE",
                        "Collect additional diagnostic logs before proceeding.",
                        "LOW",
                        20
                    )
                } else {
                    Quint(
                        "Observed ${errorRecord.category.label} Issue",
                        "Module reported: $safeMessage",
                        "Investigate module ${errorRecord.module} logs.",
                        "LOW",
                        70
                    )
                }
            }
        }

        val plan = if (rootCause != "INSUFFICIENT_EVIDENCE") {
            ChangePlan(
                title = "DIANA Fix: Resolve ${errorRecord.category.label} in ${errorRecord.module}",
                description = "DIANA detected root cause: $rootCause. Recommended action: $fix",
                affectedFiles = listOf(errorRecord.module),
                diffPreview = "// Fix for ${errorRecord.category.label}\n// $fix",
                proposedAction = "Fix applied to ${errorRecord.module}",
                status = ChangePlanStatus.PENDING
            )
        } else null

        return DianaDiagnosticReport(
            mode = DianaDiagnosticMode.ROOT_CAUSE_ANALYSIS,
            summary = summary,
            rootCause = rootCause,
            evidence = evidence,
            recommendedFix = fix,
            risk = risk,
            confidence = confidence,
            suggestedChangePlan = plan
        )
    }

    fun analyzeIncident(
        incident: Incident
    ): DianaDiagnosticReport {
        val evidence = mutableListOf<String>()
        evidence.add("Incident Title: ${incident.title}")
        evidence.add("Severity: ${incident.severity.label}")
        evidence.add("Affected Module: ${incident.affectedModule}")
        evidence.add("Timeline Events: ${incident.timeline.size}")

        val summary = "Incident Investigation for '${incident.title}'"
        val rootCause = if (incident.timeline.isNotEmpty()) {
            incident.timeline.first().description
        } else {
            "INSUFFICIENT_EVIDENCE"
        }

        val fix = "Review affected module [${incident.affectedModule}], inspect recent commits or changes, and apply automated recovery."
        val plan = ChangePlan(
            title = "DIANA Mitigation Plan: ${incident.title}",
            description = "Automated mitigation strategy for ${incident.affectedModule}.",
            affectedFiles = listOf(incident.affectedModule),
            diffPreview = "// Mitigation applied for incident: ${incident.title}",
            proposedAction = "Mitigate active incident ${incident.id}",
            status = ChangePlanStatus.PENDING
        )

        return DianaDiagnosticReport(
            mode = DianaDiagnosticMode.DIAGNOSTIC_ANALYSIS,
            summary = summary,
            rootCause = rootCause,
            evidence = evidence,
            recommendedFix = fix,
            risk = if (incident.severity.label == "CRITICAL") "HIGH" else "MEDIUM",
            confidence = 88,
            suggestedChangePlan = plan
        )
    }

    fun analyzePerformance(
        metric: PerformanceMetricRecord
    ): DianaDiagnosticReport {
        val evidence = listOf(
            "Category: ${metric.category.label}",
            "Recorded Duration: ${metric.durationMs}ms",
            "Rating: ${metric.rating.label}",
            "Context: ${metric.contextInfo}"
        )

        val (rootCause, fix) = when (metric.rating) {
            PerformanceRating.SLOW -> Pair(
                "Execution duration (${metric.durationMs}ms) exceeded threshold for ${metric.category.label}.",
                "Optimize I/O operations, leverage background Coroutine Dispatchers.IO, or batch memory reads."
            )
            PerformanceRating.FAILED -> Pair(
                "Operation failed to complete within acceptable parameters.",
                "Review error logs and ensure file descriptors and memory resources are released properly."
            )
            PerformanceRating.FAST, PerformanceRating.NORMAL -> Pair(
                "Performance is within nominal operating tolerances.",
                "No optimization required at this time."
            )
            PerformanceRating.NOT_AVAILABLE -> Pair(
                "INSUFFICIENT_EVIDENCE",
                "Enable performance instrumentation to capture timing data."
            )
        }

        return DianaDiagnosticReport(
            mode = DianaDiagnosticMode.PERFORMANCE_ANALYSIS,
            summary = "Performance Assessment: ${metric.category.label} (${metric.rating.label})",
            rootCause = rootCause,
            evidence = evidence,
            recommendedFix = fix,
            risk = if (metric.rating == PerformanceRating.SLOW) "MEDIUM" else "LOW",
            confidence = 90
        )
    }

    fun analyzeReleaseHealth(
        release: ReleaseHealthSummary
    ): DianaDiagnosticReport {
        val evidence = mutableListOf<String>()
        evidence.add("Release Version: ${release.versionName} (Build ${release.versionCode})")
        evidence.add("Build Status: ${release.buildStatus}")
        evidence.add("Signing: ${release.signingStatus}")
        evidence.add("Critical Errors: ${release.criticalErrorCount}")
        evidence.add("Regressions: ${release.regressions.size}")

        val isHealthy = release.criticalErrorCount == 0 && release.buildStatus == "PASS" && release.regressions.isEmpty()
        val rootCause = if (isHealthy) {
            "Release ${release.versionName} metrics indicate nominal production stability."
        } else {
            "Detected ${release.criticalErrorCount} critical errors and ${release.regressions.size} regressions in release ${release.versionName}."
        }

        val fix = if (isHealthy) {
            "Release is ready for production deployment."
        } else {
            "Hold deployment and resolve critical issues before releasing to production."
        }

        return DianaDiagnosticReport(
            mode = DianaDiagnosticMode.RELEASE_HEALTH_ANALYSIS,
            summary = "Release Observability Audit: ${release.versionName}",
            rootCause = rootCause,
            evidence = evidence,
            recommendedFix = fix,
            risk = if (isHealthy) "LOW" else "HIGH",
            confidence = 95
        )
    }

    private data class Quint(
        val first: String,
        val second: String,
        val third: String,
        val fourth: String,
        val fifth: Int
    )
}
