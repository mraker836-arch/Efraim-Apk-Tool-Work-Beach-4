package com.example.security.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SecurityReport(
    val projectName: String = "EFRAIM APK WORKBENCH TOOL M",
    val releaseVersion: String = "1.0",
    val scanTimestamp: Long = System.currentTimeMillis(),
    val scannerVersion: String = "1.0.0-SEC-PROD",
    val findings: List<SecurityFinding> = emptyList(),
    val securityScore: SecurityScore,
    val securityGates: List<GateResult> = emptyList(),
    val isSignatureValid: Boolean? = null,
    val signerSubject: String = "",
    val signerSha256Fingerprint: String = "",
    val artifactHash: String = "",
    val dependencyStatus: String = "CURRENT",
    val secretScanSummary: String = "CLEAN",
    val provenance: BuildProvenance? = null
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("project", projectName)
        root.put("releaseVersion", releaseVersion)
        root.put("scanTimestamp", scanTimestamp)
        root.put("scanTimestampFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(scanTimestamp)))
        root.put("scannerVersion", scannerVersion)
        root.put("overallState", securityScore.state.name)
        root.put("numericScore", securityScore.numericScore ?: -1)
        root.put("summary", securityScore.summary)
        root.put("artifactHash", artifactHash)
        root.put("isSignatureValid", isSignatureValid ?: false)
        root.put("signerSubject", signerSubject)
        root.put("signerSha256Fingerprint", signerSha256Fingerprint)
        root.put("dependencyStatus", dependencyStatus)
        root.put("secretScanSummary", secretScanSummary)

        val findingsArray = JSONArray()
        findings.forEach { f ->
            val fo = JSONObject()
            fo.put("id", f.id)
            fo.put("category", f.category.name)
            fo.put("severity", f.severity.name)
            fo.put("title", f.title)
            fo.put("description", f.description)
            fo.put("evidence", f.evidence)
            fo.put("recommendation", f.recommendation)
            fo.put("module", f.module)
            fo.put("affectedFile", f.affectedFile)
            fo.put("status", f.status.name)
            fo.put("detectedAt", f.detectedAt)
            findingsArray.put(fo)
        }
        root.put("findings", findingsArray)

        val gatesArray = JSONArray()
        securityGates.forEach { g ->
            val go = JSONObject()
            go.put("gate", g.gateType.name)
            go.put("status", g.status.name)
            go.put("title", g.title)
            go.put("evidence", g.evidence)
            go.put("remediation", g.remediation)
            go.put("isCritical", g.isCritical)
            gatesArray.put(go)
        }
        root.put("securityGates", gatesArray)

        provenance?.let { p ->
            val po = JSONObject()
            po.put("commitSha", p.commitSha)
            po.put("version", p.version)
            po.put("javaVersion", p.javaVersion)
            po.put("gradleVersion", p.gradleVersion)
            po.put("androidGradlePlugin", p.androidGradlePlugin)
            po.put("dependenciesHash", p.dependenciesHash)
            po.put("artifactHash", p.artifactHash)
            root.put("buildProvenance", po)
        }

        return root.toString(2)
    }

    fun toFormattedText(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val dateStr = sdf.format(Date(scanTimestamp))
        val sb = StringBuilder()
        sb.appendLine("================================================================")
        sb.appendLine("        EFRAIM APK WORKBENCH SECURITY & INTEGRITY REPORT        ")
        sb.appendLine("================================================================")
        sb.appendLine("Project:           $projectName")
        sb.appendLine("Release:           $releaseVersion")
        sb.appendLine("Scan Date:         $dateStr")
        sb.appendLine("Scanner Version:   $scannerVersion")
        sb.appendLine("Security State:    ${securityScore.state.name}")
        sb.appendLine("Security Score:    ${securityScore.numericScore?.toString() ?: "UNKNOWN"}/100")
        sb.appendLine("Artifact Hash:     ${if (artifactHash.isNotBlank()) artifactHash else "N/A"}")
        sb.appendLine("Signature Status:  ${if (isSignatureValid == true) "VALID & VERIFIED" else if (isSignatureValid == false) "INVALID / UNSIGNED" else "UNKNOWN"}")
        if (signerSubject.isNotBlank()) {
            sb.appendLine("Signer Subject:    $signerSubject")
            sb.appendLine("Signer SHA-256:    $signerSha256Fingerprint")
        }
        sb.appendLine("Secret Status:     $secretScanSummary")
        sb.appendLine("Dependencies:      $dependencyStatus")
        sb.appendLine()
        sb.appendLine("--- SUMMARY ---")
        sb.appendLine(securityScore.summary)
        sb.appendLine("Active Findings: ${securityScore.unmitigatedCount} (Critical: ${securityScore.criticalCount}, High: ${securityScore.highCount}, Medium: ${securityScore.mediumCount}, Low: ${securityScore.lowCount})")
        sb.appendLine()
        sb.appendLine("--- RELEASE SECURITY GATES ---")
        if (securityGates.isEmpty()) {
            sb.appendLine("  (No release gates evaluated yet)")
        } else {
            securityGates.forEach { g ->
                val marker = when (g.status) {
                    GateStatus.PASS -> "[PASS]"
                    GateStatus.FAIL -> "[FAIL - BLOCKING]"
                    GateStatus.WARNING -> "[WARN]"
                    GateStatus.NOT_APPLICABLE -> "[N/A ]"
                    GateStatus.UNKNOWN -> "[UNKN]"
                }
                sb.appendLine("  $marker ${g.gateType.displayName}: ${g.evidence}")
                if (g.status == GateStatus.FAIL) {
                    sb.appendLine("         Remediation: ${g.remediation}")
                }
            }
        }
        sb.appendLine()
        sb.appendLine("--- DETAILED FINDINGS (${findings.size}) ---")
        if (findings.isEmpty()) {
            sb.appendLine("  No security findings detected.")
        } else {
            findings.forEachIndexed { idx, f ->
                sb.appendLine("  [#${idx + 1}] [${f.severity}] ${f.title} (${f.category}) - Status: ${f.status}")
                sb.appendLine("       Description:   ${f.description}")
                sb.appendLine("       Evidence:      ${f.evidence}")
                sb.appendLine("       Recommendation:${f.recommendation}")
                if (f.affectedFile.isNotBlank()) {
                    sb.appendLine("       Affected File: ${f.affectedFile}")
                }
                if (f.mitigationHistory.isNotEmpty()) {
                    val last = f.mitigationHistory.last()
                    sb.appendLine("       Audit Note:    Status changed to ${last.actionType} by ${last.user} ('${last.reason}')")
                }
                sb.appendLine()
            }
        }
        provenance?.let { p ->
            sb.appendLine("--- BUILD PROVENANCE ---")
            sb.appendLine("  Commit SHA:    ${p.commitSha}")
            sb.appendLine("  Version:       ${p.version}")
            sb.appendLine("  Toolchain:     Gradle ${p.gradleVersion}, AGP ${p.androidGradlePlugin}, JDK ${p.javaVersion}")
            sb.appendLine("  Deps Hash:     ${p.dependenciesHash}")
            sb.appendLine("  Artifact Hash: ${p.artifactHash}")
        }
        sb.appendLine("================================================================")
        return sb.toString()
    }
}

data class ScanHistoryEntry(
    val scanId: String,
    val releaseVersion: String,
    val timestamp: Long,
    val state: SecurityState,
    val numericScore: Int?,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val totalFindings: Int,
    val artifactHash: String
)

data class SecurityRegressionReport(
    val hasRegression: Boolean,
    val details: List<String>,
    val previousScanDate: Long?,
    val newCriticalFindings: List<SecurityFinding>,
    val newHighFindings: List<SecurityFinding>,
    val newlyExposedSecrets: List<SecurityFinding>,
    val certificateChanged: Boolean,
    val hashMismatch: Boolean
)
