package com.example.diagnostics.engine

import com.example.diagnostics.model.DiagnosticErrorRecord
import com.example.diagnostics.model.ErrorCategory
import com.example.diagnostics.model.ErrorSeverity
import com.example.diagnostics.model.Incident
import com.example.diagnostics.model.IncidentStatus
import com.example.diagnostics.model.IncidentTimelineEvent
import com.example.diagnostics.redactor.SensitiveDataRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

class IncidentManager {

    private val _incidents = MutableStateFlow<List<Incident>>(emptyList())
    val incidents: StateFlow<List<Incident>> = _incidents.asStateFlow()

    private val incidentList = CopyOnWriteArrayList<Incident>()

    fun createIncident(
        title: String,
        severity: ErrorSeverity,
        affectedModule: String,
        affectedRelease: String = "1.0",
        initialDescription: String = "Incident automatically triggered"
    ): Incident {
        val safeTitle = SensitiveDataRedactor.redact(title)
        val safeDesc = SensitiveDataRedactor.redact(initialDescription)

        val incident = Incident(
            title = safeTitle,
            severity = severity,
            affectedModule = affectedModule,
            affectedRelease = affectedRelease,
            status = IncidentStatus.OPEN,
            timeline = mutableListOf(
                IncidentTimelineEvent(
                    stage = "Detection",
                    description = safeDesc,
                    author = "Incident Engine"
                )
            )
        )

        incidentList.add(0, incident)
        _incidents.value = incidentList.toList()
        return incident
    }

    fun addTimelineEvent(
        incidentId: String,
        stage: String,
        description: String,
        author: String = "Engineer"
    ): Boolean {
        val incident = incidentList.find { it.id == incidentId } ?: return false
        val safeDesc = SensitiveDataRedactor.redact(description)

        incident.timeline.add(
            IncidentTimelineEvent(
                stage = stage,
                description = safeDesc,
                author = author
            )
        )
        _incidents.value = incidentList.toList()
        return true
    }

    fun updateIncidentStatus(
        incidentId: String,
        newStatus: IncidentStatus,
        rootCause: String = "",
        resolution: String = "",
        author: String = "Engineer"
    ): Boolean {
        val incident = incidentList.find { it.id == incidentId } ?: return false

        incident.status = newStatus
        if (rootCause.isNotEmpty()) {
            incident.rootCause = SensitiveDataRedactor.redact(rootCause)
        }
        if (resolution.isNotEmpty()) {
            incident.resolution = SensitiveDataRedactor.redact(resolution)
        }

        if (newStatus == IncidentStatus.RESOLVED) {
            incident.resolvedAt = System.currentTimeMillis()
            incident.timeline.add(
                IncidentTimelineEvent(
                    stage = "Resolution",
                    description = if (resolution.isNotEmpty()) "Resolved: ${incident.resolution}" else "Incident marked resolved",
                    author = author
                )
            )
        } else if (newStatus == IncidentStatus.INVESTIGATING) {
            incident.timeline.add(
                IncidentTimelineEvent(
                    stage = "Investigation",
                    description = "Investigation underway by $author",
                    author = author
                )
            )
        } else if (newStatus == IncidentStatus.MITIGATED) {
            incident.timeline.add(
                IncidentTimelineEvent(
                    stage = "Fix",
                    description = "Mitigation applied: $resolution",
                    author = author
                )
            )
        }

        _incidents.value = incidentList.toList()
        return true
    }

    /**
     * Checks error telemetry to automatically detect structured incidents.
     * Prevents noisy incident creation by checking configurable repetition counts (>= 2).
     */
    fun evaluateTriggers(errors: List<DiagnosticErrorRecord>): List<Incident> {
        val newlyCreated = mutableListOf<Incident>()

        // 1. Check repeated critical crashes (>=2 in runtime/app)
        val criticalCrashes = errors.filter { it.severity == ErrorSeverity.CRITICAL && it.category == ErrorCategory.RUNTIME }
        if (criticalCrashes.size >= 2) {
            val title = "Repeated Runtime Crash in ${criticalCrashes.first().module}"
            if (incidentList.none { it.title == title && it.status != IncidentStatus.RESOLVED }) {
                val inc = createIncident(
                    title = title,
                    severity = ErrorSeverity.CRITICAL,
                    affectedModule = criticalCrashes.first().module,
                    initialDescription = "Detected ${criticalCrashes.size} consecutive critical runtime crashes: ${criticalCrashes.first().message}"
                )
                newlyCreated.add(inc)
            }
        }

        // 2. Check repeated signing failures
        val signingFailures = errors.filter { it.category == ErrorCategory.SIGNING }
        if (signingFailures.size >= 2) {
            val title = "Repeated APK Signing Pipeline Failures"
            if (incidentList.none { it.title == title && it.status != IncidentStatus.RESOLVED }) {
                val inc = createIncident(
                    title = title,
                    severity = ErrorSeverity.CRITICAL,
                    affectedModule = "SigningManager",
                    initialDescription = "APK signature generation failed ${signingFailures.size} times."
                )
                newlyCreated.add(inc)
            }
        }

        // 3. Check repeated GitHub Actions failures
        val ciFailures = errors.filter { it.category == ErrorCategory.GITHUB_ACTIONS }
        if (ciFailures.size >= 2) {
            val title = "GitHub Actions CI/CD Build Failures"
            if (incidentList.none { it.title == title && it.status != IncidentStatus.RESOLVED }) {
                val inc = createIncident(
                    title = title,
                    severity = ErrorSeverity.ERROR,
                    affectedModule = "GitHubActions",
                    initialDescription = "Workflow execution reported ${ciFailures.size} job failures."
                )
                newlyCreated.add(inc)
            }
        }

        // 4. Check repeated APK Analysis/Import failures
        val apkFailures = errors.filter { it.category == ErrorCategory.APK_ANALYSIS || it.category == ErrorCategory.APK_IMPORT }
        if (apkFailures.size >= 3) {
            val title = "APK Processing Pipeline Disruption"
            if (incidentList.none { it.title == title && it.status != IncidentStatus.RESOLVED }) {
                val inc = createIncident(
                    title = title,
                    severity = ErrorSeverity.WARNING,
                    affectedModule = "ApkLab",
                    initialDescription = "Multiple APK import/analysis operations failed (${apkFailures.size} failures)."
                )
                newlyCreated.add(inc)
            }
        }

        return newlyCreated
    }

    fun clearIncidents() {
        incidentList.clear()
        _incidents.value = emptyList()
    }
}
