package com.example.security.model

import java.util.UUID

enum class SecuritySeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class FindingCategory {
    APK_PERMISSIONS,
    EXPORTED_COMPONENTS,
    NETWORK_SECURITY,
    WEBVIEW_SECURITY,
    CRYPTOGRAPHY,
    SECRETS,
    DEPENDENCIES,
    SUPPLY_CHAIN,
    GITHUB_ACTIONS,
    SIGNING_INTEGRITY,
    ARTIFACT_TAMPER,
    SOURCE_INTEGRITY
}

enum class FindingStatus {
    OPEN,
    REVIEWED,
    MITIGATED,
    FALSE_POSITIVE,
    ACCEPTED_RISK
}

data class MitigationRecord(
    val actionType: FindingStatus,
    val reason: String,
    val user: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SecurityFinding(
    val id: String = UUID.randomUUID().toString(),
    val category: FindingCategory,
    val severity: SecuritySeverity,
    val title: String,
    val description: String,
    val evidence: String,
    val recommendation: String,
    val module: String = "Core",
    val affectedFile: String = "",
    val status: FindingStatus = FindingStatus.OPEN,
    val detectedAt: Long = System.currentTimeMillis(),
    val mitigationHistory: List<MitigationRecord> = emptyList()
) {
    fun withStatusChange(
        newStatus: FindingStatus,
        user: String,
        reason: String
    ): SecurityFinding {
        val record = MitigationRecord(
            actionType = newStatus,
            reason = reason,
            user = user,
            timestamp = System.currentTimeMillis()
        )
        return copy(
            status = newStatus,
            mitigationHistory = mitigationHistory + record
        )
    }
}
