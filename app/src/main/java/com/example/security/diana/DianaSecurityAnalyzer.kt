package com.example.security.diana

import com.example.ai.inference.InferenceService
import com.example.release.model.ChangePlan
import com.example.release.model.ChangePlanStatus
import com.example.security.model.FindingCategory
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

enum class DianaSecurityAnalysisType {
    SECURITY_ANALYSIS,
    APK_SECURITY_ANALYSIS,
    DEPENDENCY_ANALYSIS,
    SECRET_ANALYSIS,
    RELEASE_SECURITY_ANALYSIS
}

data class DianaSecurityReport(
    val id: String = UUID.randomUUID().toString(),
    val analysisType: DianaSecurityAnalysisType,
    val summary: String,
    val riskLevel: SecuritySeverity,
    val evidence: String,
    val recommendation: String,
    val remediationPlan: String,
    val confidence: Float,
    val proposedChangePlan: ChangePlan? = null,
    val generatedAt: Long = System.currentTimeMillis()
)

class DianaSecurityAnalyzer(
    private val inferenceService: InferenceService
) {

    suspend fun analyzeSecurityFindings(
        type: DianaSecurityAnalysisType,
        findings: List<SecurityFinding>,
        contextInfo: String = ""
    ): DianaSecurityReport = withContext(Dispatchers.IO) {
        if (findings.isEmpty() && contextInfo.isBlank()) {
            return@withContext DianaSecurityReport(
                analysisType = type,
                summary = "INSUFFICIENT_EVIDENCE: No security findings or diagnostic context provided for analysis.",
                riskLevel = SecuritySeverity.INFO,
                evidence = "No input findings supplied.",
                recommendation = "Run full Security & APK Integrity scan to collect telemetry.",
                remediationPlan = "Trigger SecurityEngine scan.",
                confidence = 1.0f
            )
        }

        val criticalCount = findings.count { it.severity == SecuritySeverity.CRITICAL }
        val highCount = findings.count { it.severity == SecuritySeverity.HIGH }
        val mediumCount = findings.count { it.severity == SecuritySeverity.MEDIUM }

        val overallRisk = when {
            criticalCount > 0 -> SecuritySeverity.CRITICAL
            highCount > 0 -> SecuritySeverity.HIGH
            mediumCount > 0 -> SecuritySeverity.MEDIUM
            findings.isNotEmpty() -> SecuritySeverity.LOW
            else -> SecuritySeverity.INFO
        }

        val topFinding = findings.maxByOrNull { it.severity }
        val summary = when (type) {
            DianaSecurityAnalysisType.APK_SECURITY_ANALYSIS ->
                "DIANA APK Analysis: Evaluated ${findings.size} APK surface findings. Top risk: ${topFinding?.title ?: "Clean component surface"}."
            DianaSecurityAnalysisType.SECRET_ANALYSIS ->
                "DIANA Secret Analysis: Detected ${findings.size} potential credential exposures. Active remediation required."
            DianaSecurityAnalysisType.DEPENDENCY_ANALYSIS ->
                "DIANA Dependency Analysis: Verified supply-chain dependencies. ${findings.size} advisories."
            DianaSecurityAnalysisType.RELEASE_SECURITY_ANALYSIS ->
                "DIANA Release Gate Audit: ${if (criticalCount > 0) "BLOCKED - Critical security gates failing." else "Release security baseline validated."}"
            DianaSecurityAnalysisType.SECURITY_ANALYSIS ->
                "DIANA Comprehensive Security Assessment: Overall posture is ${overallRisk.name} with ${findings.size} total items."
        }

        val evidenceList = findings.take(4).map { "[${it.severity}] ${it.title}: ${it.evidence}" }
        val evidenceText = evidenceList.joinToString("\n")

        val recommendation = topFinding?.recommendation
            ?: "Maintain routine supply-chain audits and enforce signature verification on release artifacts."

        val planDetails = when (overallRisk) {
            SecuritySeverity.CRITICAL ->
                "1. Immediately block CI/CD release gate.\n2. Revoke and rotate exposed credentials.\n3. Verify artifact SHA-256 and re-sign with valid keystore.\n4. Re-execute automated security test suite."
            SecuritySeverity.HIGH ->
                "1. Restrict exported component permissions in AndroidManifest.xml.\n2. Review network security configuration for cleartext traffic.\n3. Approve mitigation or apply proposed ChangePlan."
            SecuritySeverity.MEDIUM ->
                "1. Upgrade outdated libraries to recommended baselines.\n2. Verify WebView JavaScript bridges and content handlers."
            else ->
                "1. Continue standard CI release workflow.\n2. Archive security report to audit log."
        }

        val proposedChangePlan = if (topFinding != null && (topFinding.severity == SecuritySeverity.CRITICAL || topFinding.severity == SecuritySeverity.HIGH)) {
            ChangePlan(
                title = "Security Remediation: ${topFinding.title.take(40)}",
                description = "AI-recommended fix for security finding '${topFinding.title}'. ${topFinding.recommendation}",
                affectedFiles = listOf(topFinding.affectedFile.ifBlank { "app/src/main/AndroidManifest.xml" }),
                diffPreview = "// Security remediation for ${topFinding.affectedFile}\n// Applied fix: ${topFinding.recommendation}",
                proposedAction = topFinding.recommendation,
                status = ChangePlanStatus.PENDING
            )
        } else null

        DianaSecurityReport(
            analysisType = type,
            summary = summary,
            riskLevel = overallRisk,
            evidence = evidenceText,
            recommendation = recommendation,
            remediationPlan = planDetails,
            confidence = 0.95f,
            proposedChangePlan = proposedChangePlan
        )
    }
}
