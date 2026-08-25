package com.example.security.dependencies

import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity

enum class DependencyFreshness {
    CURRENT,
    OUTDATED,
    UNKNOWN
}

data class DependencyInfo(
    val group: String,
    val name: String,
    val currentVersion: String,
    val suggestedVersion: String,
    val freshness: DependencyFreshness,
    val compatibilityRisk: String,
    val isDirect: Boolean = true
)

class DependencySecurityAnalyzer(
    private val vulnerabilityProvider: VulnerabilityProvider = DefaultVulnerabilityProvider()
) {

    // Known stable baseline version map for Android / Compose ecosystem
    private val knownBaselines = mapOf(
        "androidx.core:core-ktx" to "1.12.0",
        "androidx.activity:activity-compose" to "1.8.2",
        "androidx.compose.material3:material3" to "1.2.1",
        "androidx.lifecycle:lifecycle-viewmodel-compose" to "2.7.0",
        "androidx.room:room-runtime" to "2.6.1",
        "androidx.room:room-ktx" to "2.6.1",
        "com.google.android.material:material" to "1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android" to "1.8.0",
        "com.squareup.okhttp3:okhttp" to "4.12.0"
    )

    suspend fun analyzeGradleDependencies(
        buildGradleContent: String,
        libsTomlContent: String? = null
    ): Pair<List<DependencyInfo>, List<SecurityFinding>> {
        val dependencies = mutableListOf<DependencyInfo>()
        val findings = mutableListOf<SecurityFinding>()

        // Parse implementation(...) and toml references
        val implRegex = "(implementation|api|ksp)\\([\"']([^:\"']+):([^:\"']+):([^\"']+)[\"']\\)".toRegex()
        implRegex.findAll(buildGradleContent).forEach { match ->
            val group = match.groupValues[2]
            val name = match.groupValues[3]
            val version = match.groupValues[4]
            val fullKey = "$group:$name"

            val baseline = knownBaselines[fullKey]
            val freshness = when {
                baseline == null -> DependencyFreshness.UNKNOWN
                version == baseline -> DependencyFreshness.CURRENT
                compareVersions(version, baseline) < 0 -> DependencyFreshness.OUTDATED
                else -> DependencyFreshness.CURRENT
            }

            val risk = if (freshness == DependencyFreshness.OUTDATED) "LOW to MEDIUM (API deprecations / behavior changes)" else "LOW"

            dependencies.add(
                DependencyInfo(
                    group = group,
                    name = name,
                    currentVersion = version,
                    suggestedVersion = baseline ?: version,
                    freshness = freshness,
                    compatibilityRisk = risk
                )
            )

            if (freshness == DependencyFreshness.OUTDATED) {
                findings.add(
                    SecurityFinding(
                        category = FindingCategory.DEPENDENCIES,
                        severity = SecuritySeverity.LOW,
                        title = "Outdated Dependency: $name ($version -> ${baseline ?: version})",
                        description = "Dependency $group:$name is on version $version. A newer stable baseline ($baseline) is recommended.",
                        evidence = "Declared in build.gradle.kts: $fullKey:$version",
                        recommendation = "Review change notes and upgrade to $baseline after verifying regression test suite.",
                        module = "DependencySecurity",
                        affectedFile = "build.gradle.kts",
                        status = FindingStatus.OPEN
                    )
                )
            }
        }

        // Supply-chain check: check if lockfile or hashes are enabled
        val hasDependencyLocking = buildGradleContent.contains("dependencyLocking") || buildGradleContent.contains("gradle.lockfile")
        if (!hasDependencyLocking) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.SUPPLY_CHAIN,
                    severity = SecuritySeverity.LOW,
                    title = "Dependency Locking Not Configured",
                    description = "Gradle dependency locking is not explicitly enforced. Dynamic or transient updates could introduce unverified dependencies.",
                    evidence = "No dependencyLocking block in build.gradle.kts",
                    recommendation = "Enable Gradle dependency locking or pin exact dependency versions in libs.versions.toml.",
                    module = "SupplyChain",
                    affectedFile = "build.gradle.kts",
                    status = FindingStatus.OPEN
                )
            )
        }

        // Query vulnerability provider
        for (dep in dependencies) {
            val vulns = vulnerabilityProvider.queryVulnerabilities("${dep.group}:${dep.name}", dep.currentVersion)
            for (v in vulns) {
                findings.add(
                    SecurityFinding(
                        category = FindingCategory.DEPENDENCIES,
                        severity = when (v.severity.uppercase()) {
                            "CRITICAL" -> SecuritySeverity.CRITICAL
                            "HIGH" -> SecuritySeverity.HIGH
                            "MEDIUM" -> SecuritySeverity.MEDIUM
                            else -> SecuritySeverity.LOW
                        },
                        title = "Vulnerability in ${dep.name}: ${v.cveId}",
                        description = v.summary,
                        evidence = "Package: ${v.affectedPackage} @ ${dep.currentVersion}",
                        recommendation = "Upgrade to fixed version: ${v.fixedInVersion ?: "latest stable"}",
                        module = "DependencySecurity",
                        affectedFile = "build.gradle.kts",
                        status = FindingStatus.OPEN
                    )
                )
            }
        }

        return Pair(dependencies, findings)
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".", "-").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
