package com.example.security.model

enum class SecurityGateType(val displayName: String, val isCriticalByDefault: Boolean) {
    NO_CRITICAL_FINDINGS("Zero Critical Findings", true),
    NO_EXPOSED_SECRETS("No Exposed Secrets / Keys", true),
    VALID_SIGNATURE("Valid Release Signature", true),
    VALID_ARTIFACT_HASH("Valid Artifact Integrity Checksum", true),
    PACKAGE_ID_MATCH("Package Identifier Match", true),
    VERSION_MATCH("Release Version Match", false),
    DEPENDENCY_CHECK("Dependency & Supply-Chain Check", false),
    SOURCE_INTEGRITY("Source & Workflow Integrity", false)
}

enum class GateStatus {
    PASS,
    FAIL,
    WARNING,
    NOT_APPLICABLE,
    UNKNOWN
}

data class GateResult(
    val gateType: SecurityGateType,
    val status: GateStatus,
    val title: String,
    val evidence: String,
    val remediation: String,
    val isCritical: Boolean = true,
    val affectedArtifact: String = ""
)

data class ReleaseGateReport(
    val passed: Boolean,
    val evaluatedAt: Long = System.currentTimeMillis(),
    val gateResults: List<GateResult>,
    val blockedReason: String? = null
) {
    val hasBlockingFailures: Boolean
        get() = gateResults.any { it.isCritical && it.status == GateStatus.FAIL }
}
