package com.example.ai.diana

import com.example.apk.model.APKInfo
import com.example.apk.model.RiskLevel

data class DianaFinding(
    val severity: RiskLevel, // INFO, WARNING, HIGH, CRITICAL
    val category: String,
    val title: String,
    val description: String,
    val recommendation: String
)

data class DianaAnalysisReport(
    val apkOverview: String,
    val architectureSummary: String,
    val findings: List<DianaFinding>,
    val permissionAnalysis: String,
    val manifestAnalysis: String,
    val certificateAnalysis: String,
    val rebuildRecommendations: List<String>,
    val overallSecurityScore: Int // 0 - 100
)

class DianaApkAnalyzer {

    fun analyze(apkInfo: APKInfo): DianaAnalysisReport {
        val findings = mutableListOf<DianaFinding>()
        val recommendations = mutableListOf<String>()
        var penaltyScore = 0

        // 1. Debuggable check
        if (apkInfo.isDebuggable) {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.CRITICAL,
                    category = "Manifest Security",
                    title = "Application is Debuggable (android:debuggable=\"true\")",
                    description = "Debuggable APKs expose JDWP debugging endpoints, allowing external debuggers to attach, inspect memory, and alter runtime flow.",
                    recommendation = "Disable debuggable flag in the Rebuild Workspace prior to production signing."
                )
            )
            recommendations.add("Disable debug mode: set android:debuggable=\"false\"")
            penaltyScore += 25
        } else {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.INFO,
                    category = "Manifest Security",
                    title = "Debuggable Flag Disabled",
                    description = "The application does not expose JDWP debugging interfaces.",
                    recommendation = "Maintain debuggable=\"false\" for all release builds."
                )
            )
        }

        // 2. AllowBackup check
        if (apkInfo.allowsBackup) {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.WARNING,
                    category = "Data Protection",
                    title = "ADB Backup Enabled (android:allowBackup=\"true\")",
                    description = "Allows extraction of internal databases and SharedPreferences via 'adb backup' if physical device access is obtained.",
                    recommendation = "Disable allowBackup or define strict rules via android:dataExtractionRules if sensitive data is stored."
                )
            )
            recommendations.add("Consider setting android:allowBackup=\"false\" or providing custom backup extraction rules")
            penaltyScore += 10
        }

        // 3. SDK Levels check
        if (apkInfo.minSdk < 24) {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.WARNING,
                    category = "API Compatibility",
                    title = "Legacy Minimum SDK Target (minSdkVersion ${apkInfo.minSdk})",
                    description = "APKs targeting minSdk < 24 lack native TLS 1.3 defaults and modern Android runtime sandboxing features.",
                    recommendation = "Upgrade minSdkVersion to 24 or 26 in the Rebuild Workspace."
                )
            )
            recommendations.add("Upgrade minSdkVersion to 24+ for improved cryptographic defaults")
            penaltyScore += 5
        }

        if (apkInfo.targetSdk < 34) {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.WARNING,
                    category = "API Compliance",
                    title = "Outdated Target SDK (targetSdkVersion ${apkInfo.targetSdk})",
                    description = "Google Play requires targetSdkVersion >= 34/35 for modern security, background limits, and permission granularity.",
                    recommendation = "Upgrade targetSdkVersion to 35 or 36 during rebuild."
                )
            )
            recommendations.add("Update targetSdkVersion to 35/36 to ensure compliance with modern Android behavioral policies")
            penaltyScore += 10
        }

        // 4. Permissions check
        val dangerousPerms = apkInfo.permissions.filter { it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.CRITICAL }
        if (dangerousPerms.isNotEmpty()) {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.WARNING,
                    category = "Permission Auditing",
                    title = "${dangerousPerms.size} High-Privilege Permission(s) Detected",
                    description = "Application requests sensitive capabilities: ${dangerousPerms.joinToString(", ") { it.simpleName }}.",
                    recommendation = "Verify that all declared permissions are strictly necessary for core functionality."
                )
            )
            penaltyScore += (dangerousPerms.size * 5).coerceAtMost(25)
        } else {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.INFO,
                    category = "Permission Auditing",
                    title = "Minimal Permission Footprint",
                    description = "Application does not request sensitive or critical runtime permissions.",
                    recommendation = "Preserve lean permission configuration."
                )
            )
        }

        // 5. Native libraries & 64-bit readiness
        val abis = apkInfo.nativeLibraries.keys
        val has64Bit = abis.any { it.contains("64") }
        val has32BitOnly = abis.isNotEmpty() && !has64Bit
        if (has32BitOnly) {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.CRITICAL,
                    category = "Architecture",
                    title = "Missing 64-bit Native Libraries",
                    description = "Application only contains 32-bit native libraries (${abis.joinToString(", ")}). Modern 64-bit-only devices will fail to launch.",
                    recommendation = "Include arm64-v8a and x86_64 binaries in the build plan."
                )
            )
            recommendations.add("Compile and pack 64-bit native libraries (arm64-v8a)")
            penaltyScore += 20
        } else if (abis.isNotEmpty()) {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.INFO,
                    category = "Architecture",
                    title = "64-bit ABI Support Verified",
                    description = "Native binaries detected for: ${abis.joinToString(", ")} with 64-bit readiness.",
                    recommendation = "Verify 16KB memory page size alignment for Android 15+ compatibility."
                )
            )
        }

        // 6. Signing & Certificate
        val cert = apkInfo.certificates.firstOrNull()
        if (cert != null) {
            if (cert.isSelfSigned) {
                findings.add(
                    DianaFinding(
                        severity = RiskLevel.INFO,
                        category = "Signing & PKI",
                        title = "Self-Signed Developer Certificate",
                        description = "Signed with self-signed certificate (Subject: ${cert.subject.substringBefore(",")}, Algo: ${cert.algorithm}). Standard for Android APKs.",
                        recommendation = "Ensure private signing key is securely stored in Android Keystore."
                    )
                )
            }
        } else {
            findings.add(
                DianaFinding(
                    severity = RiskLevel.HIGH,
                    category = "Signing & PKI",
                    title = "APK is Unsigned or Missing Certificate Block",
                    description = "Android OS will refuse to install unsigned application packages.",
                    recommendation = "Run the Signing Engine on this APK before deployment."
                )
            )
            recommendations.add("Sign the APK using user Keystore in the Signing Engine")
            penaltyScore += 30
        }

        val score = (100 - penaltyScore).coerceIn(10, 100)

        val overview = "APK '${apkInfo.fileName}' (${formatSize(apkInfo.fileSize)}) belongs to package '${apkInfo.packageName}' (v${apkInfo.versionName}, code ${apkInfo.versionCode}). " +
                "Targeting Android API ${apkInfo.targetSdk} (Min SDK ${apkInfo.minSdk}). Contains ${apkInfo.dexFiles.size} DEX file(s) with ${apkInfo.totalDexClasses} total class definitions."

        val arch = if (apkInfo.hasNativeLibs) {
            "Hybrid Native/Dalvik architecture with ${apkInfo.nativeLibraries.values.flatten().size} shared objects across ${abis.size} ABI(s) (${abis.joinToString(", ")})."
        } else {
            "Pure Dalvik/ART Java bytecode architecture (${apkInfo.totalDexClasses} classes, ${apkInfo.assets.size} assets, ${apkInfo.resources.size} resources)."
        }

        val permSummary = "${apkInfo.permissions.size} total declared permissions. ${dangerousPerms.size} elevated privilege permissions."
        val manifestSummary = "Activities: ${apkInfo.activities.size}, Services: ${apkInfo.services.size}, Receivers: ${apkInfo.receivers.size}, Providers: ${apkInfo.providers.size}. Debuggable=${apkInfo.isDebuggable}, AllowBackup=${apkInfo.allowsBackup}."
        val certSummary = if (cert != null) {
            "Signed with ${cert.algorithm}. SHA-256: ${cert.sha256Fingerprint}. Valid until ${cert.validUntil}."
        } else {
            "No valid X.509 certificate detected in META-INF."
        }

        return DianaAnalysisReport(
            apkOverview = overview,
            architectureSummary = arch,
            findings = findings,
            permissionAnalysis = permSummary,
            manifestAnalysis = manifestSummary,
            certificateAnalysis = certSummary,
            rebuildRecommendations = recommendations.ifEmpty { listOf("APK meets standard security and configuration requirements.") },
            overallSecurityScore = score
        )
    }

    private fun formatSize(bytes: Long): String {
        return if (bytes < 1024 * 1024) {
            "${bytes / 1024} KB"
        } else {
            String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        }
    }
}
