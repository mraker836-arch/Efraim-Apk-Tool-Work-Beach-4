package com.example.apk.scanner.deep

import com.example.apk.model.ApkFileEntry
import com.example.apk.model.CertificateStatus
import com.example.apk.model.ComponentInfo
import com.example.apk.model.DexInfo
import com.example.apk.model.EntryCategory
import com.example.apk.model.ManifestInfo
import com.example.apk.model.NativeLibraryInfo
import com.example.apk.model.ProtectionCategory
import com.example.apk.model.RiskFinding
import com.example.apk.model.RiskLevel
import com.example.apk.model.ScanPermissionInfo
import com.example.apk.model.SignatureInfo
import com.example.apk.model.StaticRiskCategory
import com.example.apk.model.StaticRiskSeverity
import java.util.UUID

/**
 * Transparent, evidence-based static risk engine for real APK archives.
 *
 * IMPORTANT:
 * - This is a STATIC ANALYSIS engine, NOT a malware signature scanner.
 * - Every finding is derived strictly from observable APK characteristics.
 * - All findings maintain [isMalwareConfirmed] = false unless verified by external threat feeds.
 */
object RealApkStaticRiskEngine {

    fun analyze(
        manifest: ManifestInfo?,
        dexList: List<DexInfo>,
        permissions: List<ScanPermissionInfo>,
        components: List<ComponentInfo>,
        nativeLibs: List<NativeLibraryInfo>,
        signatureInfo: SignatureInfo,
        archiveEntries: List<ApkFileEntry>,
        archiveAnomalies: List<String>
    ): List<RiskFinding> {
        val findings = mutableListOf<RiskFinding>()

        // ==========================================
        // 1. ARCHIVE & STRUCTURAL INTEGRITY
        // ==========================================
        if (manifest == null) {
            findings.add(
                RiskFinding(
                    id = "RISK-INT-001",
                    title = "Missing or Corrupted AndroidManifest.xml",
                    category = StaticRiskCategory.INTEGRITY,
                    severity = StaticRiskSeverity.CRITICAL,
                    description = "The APK archive does not contain a valid AndroidManifest.xml at its root. Android package installer will reject this package.",
                    evidence = "Archive contains 0 entries matching AndroidManifest.xml",
                    recommendation = "Verify the APK compilation pipeline generated a valid binary AndroidManifest.xml.",
                    affectedComponentOrFile = "AndroidManifest.xml"
                )
            )
        }

        for (anomaly in archiveAnomalies) {
            val severity = if (anomaly.contains("traversal", ignoreCase = true) || anomaly.contains("slip", ignoreCase = true)) {
                StaticRiskSeverity.CRITICAL
            } else {
                StaticRiskSeverity.HIGH
            }
            findings.add(
                RiskFinding(
                    id = "RISK-ARC-${UUID.randomUUID().toString().take(6).uppercase()}",
                    title = "Archive Structural Anomaly Detected",
                    category = StaticRiskCategory.ARCHIVE_ANOMALY,
                    severity = severity,
                    description = anomaly,
                    evidence = "ZIP central directory / local file header inspection flagged: $anomaly",
                    recommendation = "Rebuild the APK using standard Android Gradle Plugin (AGP) toolchains to eliminate ZIP anomalies.",
                    affectedComponentOrFile = "APK Archive"
                )
            )
        }

        // Suspicious entries inside archive (e.g. zip slip or duplicate)
        for (entry in archiveEntries.filter { it.isSuspicious }) {
            findings.add(
                RiskFinding(
                    id = "RISK-ARC-ENTRY-${UUID.randomUUID().toString().take(6).uppercase()}",
                    title = "Suspicious ZIP Entry: ${entry.name}",
                    category = StaticRiskCategory.ARCHIVE_ANOMALY,
                    severity = StaticRiskSeverity.HIGH,
                    description = entry.anomalyReason ?: "Entry contains suspicious paths or corrupted size headers.",
                    evidence = "Entry '${entry.name}', uncompressed: ${entry.sizeBytes} bytes, compressed: ${entry.compressedSizeBytes} bytes",
                    recommendation = "Sanitize archive entry paths and verify the packaging tool does not generate relative traversal paths.",
                    affectedComponentOrFile = entry.name
                )
            )
        }

        // ==========================================
        // 2. SIGNING & CRYPTOGRAPHIC INTEGRITY
        // ==========================================
        if (!signatureInfo.isSigned) {
            val isDebug = manifest?.isDebuggable == true
            findings.add(
                RiskFinding(
                    id = "RISK-SIG-001",
                    title = "Unsigned APK Archive",
                    category = StaticRiskCategory.INTEGRITY,
                    severity = if (isDebug) StaticRiskSeverity.MEDIUM else StaticRiskSeverity.HIGH,
                    description = "The APK archive has no valid digital signature block or certificate in META-INF. Android devices cannot verify the package publisher or install this package.",
                    evidence = "No signature files (.RSA, .DSA, .EC) or APK Signing Block found",
                    recommendation = "Sign the APK with apksigner using a valid production or debug key before distribution.",
                    affectedComponentOrFile = "META-INF/"
                )
            )
        } else {
            if (signatureInfo.isDebugCertificate) {
                findings.add(
                    RiskFinding(
                        id = "RISK-SIG-002",
                        title = "Signed with Android Debug Key",
                        category = StaticRiskCategory.CRYPTOGRAPHY,
                        severity = StaticRiskSeverity.MEDIUM,
                        description = "The APK is signed with the standard publicly known Android Debug Certificate. Anyone can generate matching debug signatures, enabling APK spoofing.",
                        evidence = "Certificate Subject/Issuer contains '${signatureInfo.certificates.firstOrNull()?.subject ?: "Android Debug"}'",
                        recommendation = "Replace debug keystore with a private release keystore for production deployments.",
                        affectedComponentOrFile = "META-INF/CERT.RSA"
                    )
                )
            }

            for (cert in signatureInfo.certificates) {
                if (cert.status == CertificateStatus.SIGNATURE_INVALID) {
                    findings.add(
                        RiskFinding(
                            id = "RISK-SIG-003",
                            title = "Invalid or Expired Signing Certificate",
                            category = StaticRiskCategory.CRYPTOGRAPHY,
                            severity = StaticRiskSeverity.HIGH,
                            description = cert.verificationDetails,
                            evidence = "Validity: ${cert.validFrom} to ${cert.validUntil}",
                            recommendation = "Re-sign the application with a valid, non-expired certificate.",
                            affectedComponentOrFile = "META-INF/"
                        )
                    )
                }

                if (cert.keySizeBits in 1..2047) {
                    findings.add(
                        RiskFinding(
                            id = "RISK-SIG-004",
                            title = "Weak Public Key Length (${cert.keySizeBits} bits)",
                            category = StaticRiskCategory.CRYPTOGRAPHY,
                            severity = StaticRiskSeverity.HIGH,
                            description = "The certificate public key size (${cert.keySizeBits} bits) is below the modern minimum standard of 2048 bits.",
                            evidence = "Algorithm: ${cert.publicKeyAlgorithm}",
                            recommendation = "Generate a new signing key with at least 2048-bit RSA or 256-bit EC.",
                            affectedComponentOrFile = "META-INF/"
                        )
                    )
                }

                if (cert.isSelfSigned && !signatureInfo.isDebugCertificate) {
                    findings.add(
                        RiskFinding(
                            id = "RISK-SIG-005",
                            title = "Self-Signed Certificate Detected",
                            category = StaticRiskCategory.CRYPTOGRAPHY,
                            severity = StaticRiskSeverity.LOW,
                            description = "The APK is signed with a self-signed certificate. Standard in the Android ecosystem, but certificate fingerprints should be pinned and tracked.",
                            evidence = "Issuer matches Subject: ${cert.subject}",
                            recommendation = "Verify certificate fingerprint (${cert.sha256Fingerprint}) matches trusted publisher CI/CD records.",
                            affectedComponentOrFile = "META-INF/"
                        )
                    )
                }
            }
        }

        // ==========================================
        // 3. APPLICATION RUNTIME & MANIFEST FLAGS
        // ==========================================
        if (manifest != null) {
            if (manifest.isDebuggable) {
                findings.add(
                    RiskFinding(
                        id = "RISK-MAN-001",
                        title = "Application is Debuggable (android:debuggable=true)",
                        category = StaticRiskCategory.SECURITY,
                        severity = StaticRiskSeverity.CRITICAL,
                        description = "The application explicitly enables android:debuggable. This allows anyone with physical or ADB access to attach a debugger, inspect memory, and dump private data.",
                        evidence = "<application android:debuggable=\"true\">",
                        recommendation = "Ensure debuggable is set to false in release build variants.",
                        affectedComponentOrFile = "AndroidManifest.xml"
                    )
                )
            }

            if (manifest.usesCleartextTraffic) {
                findings.add(
                    RiskFinding(
                        id = "RISK-MAN-002",
                        title = "Cleartext HTTP Traffic Permitted",
                        category = StaticRiskCategory.NETWORK_SECURITY,
                        severity = StaticRiskSeverity.HIGH,
                        description = "The application permits unencrypted cleartext HTTP communications (android:usesCleartextTraffic=true), exposing traffic to eavesdropping.",
                        evidence = "<application android:usesCleartextTraffic=\"true\">",
                        recommendation = "Disable cleartext network traffic and mandate TLS 1.3.",
                        affectedComponentOrFile = "AndroidManifest.xml"
                    )
                )
            }

            if (manifest.allowBackup) {
                findings.add(
                    RiskFinding(
                        id = "RISK-MAN-003",
                        title = "Application Data Backup Enabled (android:allowBackup=true)",
                        category = StaticRiskCategory.SECURITY,
                        severity = StaticRiskSeverity.LOW,
                        description = "The application permits ADB backups. Attackers with physical access can dump private application databases and preferences.",
                        evidence = "<application android:allowBackup=\"true\">",
                        recommendation = "Set android:allowBackup=\"false\" or define strict android:dataExtractionRules excluding private keys.",
                        affectedComponentOrFile = "AndroidManifest.xml"
                    )
                )
            }
        }

        // ==========================================
        // 4. COMPONENT EXPOSURE & ATTACK SURFACE
        // ==========================================
        for (comp in components) {
            val isLauncher = comp.intentActions.contains("android.intent.action.MAIN")
            if (comp.exported) {
                if (comp.permission.isNullOrBlank()) {
                    if (comp.type in listOf("Service", "Receiver", "Provider")) {
                        findings.add(
                            RiskFinding(
                                id = "RISK-EXP-${comp.type.take(3).uppercase()}-${comp.simpleName.take(8)}",
                                title = "Exported ${comp.type} Without Permission Protection",
                                category = StaticRiskCategory.COMPONENT_EXPOSURE,
                                severity = StaticRiskSeverity.HIGH,
                                description = "The component ${comp.name} is exported to other applications on device without requiring an access permission.",
                                evidence = "<${comp.type.lowercase()} android:name=\"${comp.name}\" android:exported=\"true\" permission=\"null\">",
                                recommendation = "If this component is internal, set android:exported=\"false\". Otherwise, guard it with an explicit custom android:permission.",
                                affectedComponentOrFile = comp.name
                            )
                        )
                    } else if (comp.type == "Activity" && !isLauncher) {
                        findings.add(
                            RiskFinding(
                                id = "RISK-EXP-ACT-${comp.simpleName.take(8)}",
                                title = "Exported Non-Launcher Activity: ${comp.simpleName}",
                                category = StaticRiskCategory.COMPONENT_EXPOSURE,
                                severity = StaticRiskSeverity.MEDIUM,
                                description = "The Activity ${comp.name} is exported and can be launched by external applications without permission requirements.",
                                evidence = "<activity android:name=\"${comp.name}\" android:exported=\"true\">",
                                recommendation = "Audit whether this Activity handles sensitive intent extras or should be marked internal.",
                                affectedComponentOrFile = comp.name
                            )
                        )
                    } else if (isLauncher) {
                        findings.add(
                            RiskFinding(
                                id = "RISK-EXP-LAUNCHER-${comp.simpleName.take(8)}",
                                title = "Launcher Activity Entry Point: ${comp.simpleName}",
                                category = StaticRiskCategory.COMPONENT_EXPOSURE,
                                severity = StaticRiskSeverity.INFO,
                                description = "Standard launcher activity declared to provide user application entry point.",
                                evidence = "<activity android:name=\"${comp.name}\"> with MAIN / LAUNCHER action",
                                recommendation = "Ensure input validation is performed on any external intent extras.",
                                affectedComponentOrFile = comp.name
                            )
                        )
                    }
                }
            }
        }

        // ==========================================
        // 5. PERMISSION AUDIT & PRIVACY
        // ==========================================
        val declaredPermNames = permissions.map { it.name }.toSet()

        val highRiskPermissions = setOf(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG"
        )

        for (perm in permissions) {
            if (perm.name in highRiskPermissions) {
                findings.add(
                    RiskFinding(
                        id = "RISK-PRM-SENS-${perm.simpleName.take(8)}",
                        title = "High-Privilege Permission: ${perm.simpleName}",
                        category = StaticRiskCategory.PRIVACY,
                        severity = StaticRiskSeverity.HIGH,
                        description = "Declared permission ${perm.name} confers high privileges (${perm.reason}).",
                        evidence = "<uses-permission android:name=\"${perm.name}\">",
                        recommendation = "Review whether this permission is essential for core app functionality and ensure runtime disclosure.",
                        affectedComponentOrFile = perm.name
                    )
                )
            } else if (perm.protectionCategory == ProtectionCategory.DANGEROUS) {
                val severity = when (perm.riskIndicator) {
                    RiskLevel.CRITICAL -> StaticRiskSeverity.HIGH
                    RiskLevel.HIGH -> StaticRiskSeverity.MEDIUM
                    RiskLevel.WARNING -> StaticRiskSeverity.LOW
                    RiskLevel.INFO -> StaticRiskSeverity.INFO
                }
                findings.add(
                    RiskFinding(
                        id = "RISK-PRM-DNG-${perm.simpleName.take(8)}",
                        title = "Runtime Dangerous Permission: ${perm.simpleName}",
                        category = StaticRiskCategory.PRIVACY,
                        severity = severity,
                        description = perm.reason,
                        evidence = "<uses-permission android:name=\"${perm.name}\">",
                        recommendation = "Ensure permission is requested at runtime with graceful fallback when denied.",
                        affectedComponentOrFile = perm.name
                    )
                )
            }
        }

        if (permissions.size > 15) {
            findings.add(
                RiskFinding(
                    id = "RISK-PRM-COUNT",
                    title = "Excessive Permissions Declared (${permissions.size} Total)",
                    category = StaticRiskCategory.PRIVACY,
                    severity = StaticRiskSeverity.MEDIUM,
                    description = "The application declares ${permissions.size} permissions. Excessively broad permissions increase attack surface.",
                    evidence = "Total declared permissions count: ${permissions.size}",
                    recommendation = "Adhere to the Principle of Least Privilege and prune unneeded permissions.",
                    affectedComponentOrFile = "AndroidManifest.xml"
                )
            )
        }

        // ==========================================
        // 6. CORRELATED RISKS (COMBINATIONS)
        // ==========================================
        val hasOverlay = declaredPermNames.contains("android.permission.SYSTEM_ALERT_WINDOW")
        val hasAccessibility = declaredPermNames.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
        val hasInstallPackages = declaredPermNames.contains("android.permission.REQUEST_INSTALL_PACKAGES")
        val hasSms = declaredPermNames.any { it.contains("SMS", ignoreCase = true) }
        val cleartextEnabled = manifest?.usesCleartextTraffic == true

        if (hasOverlay && hasAccessibility) {
            findings.add(
                RiskFinding(
                    id = "RISK-COMBO-OVERLAY-A11Y",
                    title = "Potentially Sensitive Combination: Overlay + Accessibility",
                    category = StaticRiskCategory.SECURITY,
                    severity = StaticRiskSeverity.HIGH,
                    description = "App requests both SYSTEM_ALERT_WINDOW and BIND_ACCESSIBILITY_SERVICE. This combination allows drawing over screens while observing/interacting with UI elements.",
                    evidence = "Permissions: SYSTEM_ALERT_WINDOW and BIND_ACCESSIBILITY_SERVICE",
                    recommendation = "Audit whether both permissions are strictly necessary. Misuse of this combination is heavily scrutinized on Google Play.",
                    affectedComponentOrFile = "AndroidManifest.xml"
                )
            )
        }

        if (hasOverlay && hasInstallPackages) {
            findings.add(
                RiskFinding(
                    id = "RISK-COMBO-OVERLAY-INSTALL",
                    title = "Potentially Sensitive Combination: Overlay + Package Installation",
                    category = StaticRiskCategory.SECURITY,
                    severity = StaticRiskSeverity.HIGH,
                    description = "App requests SYSTEM_ALERT_WINDOW and REQUEST_INSTALL_PACKAGES. This pattern can facilitate deceptive app installation flows.",
                    evidence = "Permissions: SYSTEM_ALERT_WINDOW and REQUEST_INSTALL_PACKAGES",
                    recommendation = "Ensure installation requests are transparently user-initiated.",
                    affectedComponentOrFile = "AndroidManifest.xml"
                )
            )
        }

        if (hasSms && cleartextEnabled) {
            findings.add(
                RiskFinding(
                    id = "RISK-COMBO-SMS-CLEARTEXT",
                    title = "Potentially Sensitive Combination: SMS Access + Cleartext HTTP",
                    category = StaticRiskCategory.PRIVACY,
                    severity = StaticRiskSeverity.HIGH,
                    description = "The application can access SMS messages and permits unencrypted HTTP traffic. This risks accidental transmission of SMS verification codes in plaintext.",
                    evidence = "SMS permissions declared with android:usesCleartextTraffic=\"true\"",
                    recommendation = "Disable cleartext HTTP network traffic and enforce TLS.",
                    affectedComponentOrFile = "AndroidManifest.xml"
                )
            )
        }

        // ==========================================
        // 7. BYTECODE & DEPENDENCIES
        // ==========================================
        if (dexList.size > 1) {
            findings.add(
                RiskFinding(
                    id = "RISK-DEX-MULTI",
                    title = "Multidex Archive (${dexList.size} DEX Files)",
                    category = StaticRiskCategory.SYSTEM_RESTRICTION,
                    severity = StaticRiskSeverity.INFO,
                    description = "The APK contains ${dexList.size} DEX files. Multidex is standard for applications with more than 65,536 methods.",
                    evidence = "Found ${dexList.size} DEX files: ${dexList.joinToString { it.fileName }}",
                    recommendation = "Enable R8/ProGuard code shrinking to minimize APK size and method footprint.",
                    affectedComponentOrFile = "classes.dex"
                )
            )
        }

        if (nativeLibs.isNotEmpty()) {
            val abis = nativeLibs.map { it.abi }.distinct()
            findings.add(
                RiskFinding(
                    id = "RISK-NAT-001",
                    title = "Compiled Native Libraries Present (${nativeLibs.size} .so files)",
                    category = StaticRiskCategory.SECURITY,
                    severity = StaticRiskSeverity.INFO,
                    description = "The APK contains compiled native ELF binaries for architectures: ${abis.joinToString()}. Native code operates outside the managed Android runtime.",
                    evidence = "Found ${nativeLibs.size} native libraries across ABIs: ${abis.joinToString()}",
                    recommendation = "Ensure native code is compiled with ASLR (-fPIE), stack canaries, and strict memory safety practices.",
                    affectedComponentOrFile = "lib/"
                )
            )
        }

        return findings
    }
}
