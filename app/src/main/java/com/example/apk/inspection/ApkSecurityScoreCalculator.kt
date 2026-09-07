package com.example.apk.inspection

import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity

data class SecurityScoreBreakdown(
    val score: Int,
    val grade: String,
    val riskSummary: String,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val infoCount: Int,
    val totalFindings: Int,
    val isClean: Boolean
)

object ApkSecurityScoreCalculator {

    /**
     * Computes a deterministic security score and risk profile based exclusively on real findings.
     * Starts from a baseline of 100 points, deducting penalties according to finding severity:
     * - CRITICAL: -25 points
     * - HIGH:     -15 points
     * - MEDIUM:   -8 points
     * - LOW:      -3 points
     * - INFO:      0 points
     */
    fun calculateScore(findings: List<SecurityFinding>): SecurityScoreBreakdown {
        if (findings.isEmpty()) {
            return SecurityScoreBreakdown(
                score = 100,
                grade = "A+",
                riskSummary = "Clean Baseline - No Security Findings Identified",
                criticalCount = 0,
                highCount = 0,
                mediumCount = 0,
                lowCount = 0,
                infoCount = 0,
                totalFindings = 0,
                isClean = true
            )
        }

        var critical = 0
        var high = 0
        var medium = 0
        var low = 0
        var info = 0

        findings.forEach { finding ->
            when (finding.severity) {
                SecuritySeverity.CRITICAL -> critical++
                SecuritySeverity.HIGH -> high++
                SecuritySeverity.MEDIUM -> medium++
                SecuritySeverity.LOW -> low++
                SecuritySeverity.INFO -> info++
            }
        }

        val penalty = (critical * 25) + (high * 15) + (medium * 8) + (low * 3)
        val score = (100 - penalty).coerceIn(0, 100)

        val (grade, summary) = when {
            critical > 0 || score < 40 -> "F" to "Critical Risk - Immediate Remediation Recommended"
            score < 60 -> "D" to "High Risk - Multiple Security Flags Detected"
            score < 75 -> "C" to "Moderate Risk - Security Hardening Recommended"
            score < 90 -> "B" to "Low-to-Moderate Risk - Minor Security Observations"
            else -> "A" to "Low Risk - Strong Security Baseline"
        }

        return SecurityScoreBreakdown(
            score = score,
            grade = grade,
            riskSummary = summary,
            criticalCount = critical,
            highCount = high,
            mediumCount = medium,
            lowCount = low,
            infoCount = info,
            totalFindings = findings.size,
            isClean = false
        )
    }
}
