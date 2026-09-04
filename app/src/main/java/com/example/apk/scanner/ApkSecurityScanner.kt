package com.example.apk.scanner

import com.example.apk.model.CertificateStatus
import com.example.apk.model.DexInfo
import com.example.apk.model.ManifestInfo
import com.example.apk.model.NativeLibraryInfo
import com.example.apk.model.ProtectionCategory
import com.example.apk.model.RiskLevel
import com.example.apk.model.ScanCertificateInfo
import com.example.apk.model.ScanPermissionInfo
import com.example.security.model.FindingCategory
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import java.util.UUID

object ApkSecurityScanner {

    fun scan(
        manifest: ManifestInfo,
        dexList: List<DexInfo>,
        permissions: List<ScanPermissionInfo>,
        nativeLibs: List<NativeLibraryInfo>,
        certificates: List<ScanCertificateInfo>,
        abiCoverage: List<String>
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        // 1. debuggable = true
        if (manifest.isDebuggable) {
            findings.add(
                SecurityFinding(
                    id = "SEC-DBG-001-${UUID.randomUUID().toString().take(8)}",
                    category = FindingCategory.EXPORTED_COMPONENTS,
                    severity = SecuritySeverity.CRITICAL,
                    title = "Application is Debuggable in Production",
                    description = "The android:debuggable attribute is enabled. This allows any local attacker or malware with ADB access to attach a debugger, inspect process memory, execute arbitrary code, and extract runtime encryption keys.",
                    evidence = "AndroidManifest.xml specifies <application android:debuggable=\"true\">",
                    recommendation = "Set android:debuggable=\"false\" in release builds to enforce process isolation.",
                    module = "ManifestScanner",
                    affectedFile = "AndroidManifest.xml"
                )
            )
        }

        // 2. Exported components without required protection
        val allComponents = manifest.activities + manifest.services + manifest.receivers + manifest.providers
        for (comp in allComponents) {
            if (comp.exported && comp.permission.isNullOrBlank()) {
                // Filter out standard launcher activities which must be exported to launch
                val isLauncher = comp.intentActions.contains("android.intent.action.MAIN")
                if (!isLauncher || comp.type != "Activity") {
                    val severity = when (comp.type) {
                        "Provider" -> SecuritySeverity.HIGH
                        "Receiver" -> SecuritySeverity.HIGH
                        "Service" -> SecuritySeverity.HIGH
                        else -> SecuritySeverity.MEDIUM
                    }
                    findings.add(
                        SecurityFinding(
                            id = "SEC-EXP-${comp.type.take(3).uppercase()}-${UUID.randomUUID().toString().take(8)}",
                            category = FindingCategory.EXPORTED_COMPONENTS,
                            severity = severity,
                            title = "Exported ${comp.type} without Permission Protection",
                            description = "The component ${comp.name} is exported (accessible to any external application) without requiring an access permission.",
                            evidence = "${comp.type} '${comp.name}' has exported=true with permission=null and actions=[${comp.intentActions.joinToString()}]",
                            recommendation = "Set android:exported=\"false\" if this component is internal to the application, or protect it with an explicit android:permission.",
                            module = "ComponentScanner",
                            affectedFile = "AndroidManifest.xml"
                        )
                    )
                }
            }
        }

        // 3. allowBackup = true
        if (manifest.allowBackup) {
            findings.add(
                SecurityFinding(
                    id = "SEC-BCK-001-${UUID.randomUUID().toString().take(8)}",
                    category = FindingCategory.SECRETS,
                    severity = SecuritySeverity.LOW,
                    title = "Application Data Backup is Allowed",
                    description = "The application allows ADB backup (android:allowBackup=true). Attackers with physical access can dump private application databases and shared preferences.",
                    evidence = "AndroidManifest.xml specifies <application android:allowBackup=\"true\">",
                    recommendation = "Set android:allowBackup=\"false\" or define strict android:dataExtractionRules excluding private keys and session tokens.",
                    module = "ManifestScanner",
                    affectedFile = "AndroidManifest.xml"
                )
            )
        }

        // 4. cleartext traffic enabled
        if (manifest.usesCleartextTraffic) {
            findings.add(
                SecurityFinding(
                    id = "SEC-NET-001-${UUID.randomUUID().toString().take(8)}",
                    category = FindingCategory.NETWORK_SECURITY,
                    severity = SecuritySeverity.HIGH,
                    title = "Cleartext (HTTP) Network Traffic Enabled",
                    description = "The application explicitly permits unencrypted cleartext HTTP traffic (android:usesCleartextTraffic=true), exposing transmitted data to passive eavesdropping and active MitM tampering.",
                    evidence = "AndroidManifest.xml specifies <application android:usesCleartextTraffic=\"true\">",
                    recommendation = "Disable cleartext network traffic (android:usesCleartextTraffic=\"false\") and mandate TLS 1.3.",
                    module = "NetworkSecurity",
                    affectedFile = "AndroidManifest.xml"
                )
            )
        }

        // 5. Suspicious network security configuration
        if (manifest.networkSecurityConfig != null) {
            findings.add(
                SecurityFinding(
                    id = "SEC-NET-002-${UUID.randomUUID().toString().take(8)}",
                    category = FindingCategory.NETWORK_SECURITY,
                    severity = SecuritySeverity.INFO,
                    title = "Custom Network Security Configuration Detected",
                    description = "The application specifies a custom network security configuration: ${manifest.networkSecurityConfig}.",
                    evidence = "AndroidManifest.xml references android:networkSecurityConfig=\"${manifest.networkSecurityConfig}\"",
                    recommendation = "Verify that custom trust anchors do not allow user certificates in release builds unless strictly necessary for debugging.",
                    module = "NetworkSecurity",
                    affectedFile = "AndroidManifest.xml"
                )
            )
        }

        // 6. Dangerous and Special permissions
        for (perm in permissions) {
            if (perm.protectionCategory == ProtectionCategory.DANGEROUS || perm.protectionCategory == ProtectionCategory.SPECIAL) {
                val severity = when (perm.riskIndicator) {
                    RiskLevel.CRITICAL -> SecuritySeverity.CRITICAL
                    RiskLevel.HIGH -> SecuritySeverity.HIGH
                    RiskLevel.WARNING -> SecuritySeverity.MEDIUM
                    RiskLevel.INFO -> SecuritySeverity.LOW
                }
                findings.add(
                    SecurityFinding(
                        id = "SEC-PRM-${perm.simpleName.take(6).uppercase()}-${UUID.randomUUID().toString().take(6)}",
                        category = FindingCategory.APK_PERMISSIONS,
                        severity = severity,
                        title = "${perm.protectionCategory.name} Permission: ${perm.simpleName}",
                        description = perm.reason,
                        evidence = "AndroidManifest.xml requests <uses-permission android:name=\"${perm.name}\">",
                        recommendation = "Audit whether ${perm.name} is necessary for primary app workflows and ensure runtime checks are present.",
                        module = "PermissionScanner",
                        affectedFile = "AndroidManifest.xml"
                    )
                )
            }
        }

        // 7. Excessive permissions (> 15 total permissions)
        if (permissions.size > 15) {
            findings.add(
                SecurityFinding(
                    id = "SEC-PRM-EXCESSIVE-${UUID.randomUUID().toString().take(8)}",
                    category = FindingCategory.APK_PERMISSIONS,
                    severity = SecuritySeverity.MEDIUM,
                    title = "Excessive Permissions Declared (${permissions.size} Total)",
                    description = "The application requests ${permissions.size} distinct permissions. An over-privileged application expands the blast radius in case of component hijacking.",
                    evidence = "Total declared permissions count is ${permissions.size}: ${permissions.take(5).joinToString { it.simpleName }}...",
                    recommendation = "Apply the Principle of Least Privilege and prune unused permission declarations.",
                    module = "PermissionScanner",
                    affectedFile = "AndroidManifest.xml"
                )
            )
        }

        // 8. Native libraries present
        if (nativeLibs.isNotEmpty()) {
            findings.add(
                SecurityFinding(
                    id = "SEC-NAT-001-${UUID.randomUUID().toString().take(8)}",
                    category = FindingCategory.DEPENDENCIES,
                    severity = SecuritySeverity.INFO,
                    title = "Native Shared Libraries Present (${nativeLibs.size} .so files)",
                    description = "The APK contains compiled native ELF binaries for architectures: ${abiCoverage.joinToString()}. Native code is susceptible to memory corruption vulnerabilities (buffer overflows, use-after-free).",
                    evidence = "Found ${nativeLibs.size} native libraries across ABIs: ${abiCoverage.joinToString()}",
                    recommendation = "Ensure native libraries are compiled with ASLR (-fPIE), stack canaries (-fstack-protector-strong), and full RELRO.",
                    module = "NativeAbiScanner",
                    affectedFile = "lib/"
                )
            )
        }

        // 9. Weak or expired certificates
        for (cert in certificates) {
            if (cert.isSelfSigned) {
                findings.add(
                    SecurityFinding(
                        id = "SEC-CRT-SELF-${UUID.randomUUID().toString().take(8)}",
                        category = FindingCategory.SIGNING_INTEGRITY,
                        severity = SecuritySeverity.LOW,
                        title = "Self-Signed Signing Certificate",
                        description = "The APK is signed with a self-signed certificate. While standard for Android debug keys, production distributions should use dedicated release keystores or Google Play App Signing.",
                        evidence = "Subject '${cert.subject}' matches Issuer '${cert.issuer}'",
                        recommendation = "Verify certificate fingerprint (${cert.sha256Fingerprint}) against known build server fingerprints.",
                        module = "CertificateScanner",
                        affectedFile = "META-INF/"
                    )
                )
            }
            if (cert.status == CertificateStatus.SIGNATURE_INVALID) {
                findings.add(
                    SecurityFinding(
                        id = "SEC-CRT-INV-${UUID.randomUUID().toString().take(8)}",
                        category = FindingCategory.SIGNING_INTEGRITY,
                        severity = SecuritySeverity.HIGH,
                        title = "Invalid or Expired Signing Certificate",
                        description = cert.verificationDetails,
                        evidence = "Certificate validity: from ${cert.validFrom} to ${cert.validUntil}",
                        recommendation = "Re-sign the APK with a currently valid X.509 certificate.",
                        module = "CertificateScanner",
                        affectedFile = "META-INF/"
                    )
                )
            }
            if (cert.keySizeBits in 1..2047) {
                findings.add(
                    SecurityFinding(
                        id = "SEC-CRT-WEAK-${UUID.randomUUID().toString().take(8)}",
                        category = FindingCategory.CRYPTOGRAPHY,
                        severity = SecuritySeverity.HIGH,
                        title = "Weak Public Key Length (${cert.keySizeBits} bits)",
                        description = "The certificate public key size is ${cert.keySizeBits} bits, which falls below the modern industry minimum of 2048 bits.",
                        evidence = "Certificate public key algorithm: ${cert.publicKeyAlgorithm}",
                        recommendation = "Generate a new signing key with at least 2048-bit RSA or 256-bit EC.",
                        module = "CertificateScanner",
                        affectedFile = "META-INF/"
                    )
                )
            }
        }

        // 10. Multiple DEX files
        if (dexList.size > 1) {
            findings.add(
                SecurityFinding(
                    id = "SEC-DEX-MULTI-${UUID.randomUUID().toString().take(8)}",
                    category = FindingCategory.DEPENDENCIES,
                    severity = SecuritySeverity.INFO,
                    title = "Multidex Archive (${dexList.size} DEX files)",
                    description = "The APK contains ${dexList.size} DEX files. Multidex is normal for large applications exceeding 65K methods.",
                    evidence = "DEX files: ${dexList.joinToString { it.fileName }}",
                    recommendation = "Ensure R8/ProGuard shrinking is enabled to minimize attack surface and method counts.",
                    module = "DexScanner",
                    affectedFile = "classes.dex"
                )
            )
        }

        return findings
    }
}
