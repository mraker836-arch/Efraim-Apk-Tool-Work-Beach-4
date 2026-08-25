package com.example.security.model

enum class SecurityState {
    SECURE,
    LOW_RISK,
    MEDIUM_RISK,
    HIGH_RISK,
    CRITICAL,
    UNKNOWN
}

data class SecurityScore(
    val numericScore: Int?, // 0..100 or null if UNKNOWN
    val state: SecurityState,
    val summary: String,
    val totalFindings: Int,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val infoCount: Int,
    val unmitigatedCount: Int,
    val hasExposedSecrets: Boolean,
    val isSignatureValid: Boolean?,
    val isArtifactIntegrityValid: Boolean?
) {
    companion object {
        fun unknown(reason: String = "Required security checks have not been performed yet"): SecurityScore {
            return SecurityScore(
                numericScore = null,
                state = SecurityState.UNKNOWN,
                summary = reason,
                totalFindings = 0,
                criticalCount = 0,
                highCount = 0,
                mediumCount = 0,
                lowCount = 0,
                infoCount = 0,
                unmitigatedCount = 0,
                hasExposedSecrets = false,
                isSignatureValid = null,
                isArtifactIntegrityValid = null
            )
        }

        fun calculate(
            findings: List<SecurityFinding>,
            isSignatureValid: Boolean?,
            isArtifactIntegrityValid: Boolean?,
            checksCompleted: Boolean
        ): SecurityScore {
            if (!checksCompleted) {
                return unknown("Security analysis has not run")
            }

            val activeFindings = findings.filter { 
                it.status == FindingStatus.OPEN || it.status == FindingStatus.REVIEWED 
            }

            val critical = activeFindings.count { it.severity == SecuritySeverity.CRITICAL }
            val high = activeFindings.count { it.severity == SecuritySeverity.HIGH }
            val medium = activeFindings.count { it.severity == SecuritySeverity.MEDIUM }
            val low = activeFindings.count { it.severity == SecuritySeverity.LOW }
            val info = activeFindings.count { it.severity == SecuritySeverity.INFO }
            val hasExposedSecrets = activeFindings.any { it.category == FindingCategory.SECRETS && it.severity >= SecuritySeverity.HIGH }

            var score = 100
            score -= (critical * 35)
            score -= (high * 15)
            score -= (medium * 5)
            score -= (low * 2)

            if (isSignatureValid == false) score -= 30
            if (isArtifactIntegrityValid == false) score -= 40
            if (hasExposedSecrets) score -= 25

            score = score.coerceIn(0, 100)

            val state = when {
                critical > 0 || isArtifactIntegrityValid == false || hasExposedSecrets -> SecurityState.CRITICAL
                high > 0 || isSignatureValid == false -> SecurityState.HIGH_RISK
                medium > 0 -> SecurityState.MEDIUM_RISK
                low > 0 -> SecurityState.LOW_RISK
                else -> SecurityState.SECURE
            }

            val summary = when (state) {
                SecurityState.SECURE -> "All automated security & integrity checks passed. Zero critical or high risks detected."
                SecurityState.LOW_RISK -> "Minor advisory findings detected. No critical or high risks."
                SecurityState.MEDIUM_RISK -> "Medium risk findings require review before production release."
                SecurityState.HIGH_RISK -> "High risk items identified (e.g. unverified signatures or sensitive exposed components)."
                SecurityState.CRITICAL -> "CRITICAL security gate violations or exposed secrets. Release MUST be blocked."
                SecurityState.UNKNOWN -> "Security checks incomplete."
            }

            return SecurityScore(
                numericScore = score,
                state = state,
                summary = summary,
                totalFindings = findings.size,
                criticalCount = critical,
                highCount = high,
                mediumCount = medium,
                lowCount = low,
                infoCount = info,
                unmitigatedCount = activeFindings.size,
                hasExposedSecrets = hasExposedSecrets,
                isSignatureValid = isSignatureValid,
                isArtifactIntegrityValid = isArtifactIntegrityValid
            )
        }
    }
}
