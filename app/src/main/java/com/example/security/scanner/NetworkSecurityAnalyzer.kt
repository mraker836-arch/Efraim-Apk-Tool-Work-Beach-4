package com.example.security.scanner

import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity

data class NetworkSecurityConfigInfo(
    val allowsCleartext: Boolean,
    val usesNetworkSecurityConfig: Boolean,
    val customCertificatesAllowed: Boolean,
    val detectedHttpEndpoints: List<String>
)

class NetworkSecurityAnalyzer {

    fun analyzeNetworkSecurity(
        manifestRaw: String,
        networkConfigXml: String? = null,
        codebaseEndpoints: List<String> = emptyList()
    ): Pair<NetworkSecurityConfigInfo, List<SecurityFinding>> {
        val findings = mutableListOf<SecurityFinding>()

        val allowsCleartextExplicit = manifestRaw.contains("android:usesCleartextTraffic=\"true\"")
        val usesNetSecConfig = manifestRaw.contains("android:networkSecurityConfig")
        val customCerts = networkConfigXml?.contains("certificates src=\"user\"") == true

        val httpEndpoints = codebaseEndpoints.filter { 
            it.startsWith("http://", ignoreCase = true) && !it.contains("localhost") && !it.contains("127.0.0.1") && !it.contains("schemas.android.com") && !it.contains("w3.org")
        }

        if (allowsCleartextExplicit) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.NETWORK_SECURITY,
                    severity = SecuritySeverity.HIGH,
                    title = "Cleartext Traffic Allowed Globally",
                    description = "Application explicitly sets android:usesCleartextTraffic=\"true\", allowing unencrypted HTTP connections subject to Man-in-the-Middle (MitM) attacks.",
                    evidence = "android:usesCleartextTraffic=\"true\" found in AndroidManifest.xml",
                    recommendation = "Disable global cleartext traffic and enforce HTTPS / TLS for all remote communications.",
                    module = "NetworkSecurity",
                    affectedFile = "AndroidManifest.xml",
                    status = FindingStatus.OPEN
                )
            )
        }

        if (!usesNetSecConfig && allowsCleartextExplicit) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.NETWORK_SECURITY,
                    severity = SecuritySeverity.MEDIUM,
                    title = "Missing Network Security Policy",
                    description = "No network_security_config.xml defined to enforce certificate pinning or domain-specific cleartext exceptions.",
                    evidence = "android:networkSecurityConfig attribute not declared in application tag",
                    recommendation = "Add res/xml/network_security_config.xml to pin trusted certificates and restrict cleartext domains.",
                    module = "NetworkSecurity",
                    affectedFile = "res/xml/network_security_config.xml",
                    status = FindingStatus.OPEN
                )
            )
        }

        if (customCerts) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.NETWORK_SECURITY,
                    severity = SecuritySeverity.HIGH,
                    title = "Trusts User-Installed Certificates",
                    description = "Network security configuration trusts user CAs in release build, making the app vulnerable to proxy-based traffic interception.",
                    evidence = "<certificates src=\"user\" /> detected in network configuration",
                    recommendation = "Restrict user-installed CA trust to debug builds only using <debug-overrides>.",
                    module = "NetworkSecurity",
                    affectedFile = "network_security_config.xml",
                    status = FindingStatus.OPEN
                )
            )
        }

        if (httpEndpoints.isNotEmpty()) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.NETWORK_SECURITY,
                    severity = SecuritySeverity.MEDIUM,
                    title = "Insecure Cleartext HTTP Endpoints Detected",
                    description = "Found hardcoded plaintext HTTP API endpoints: ${httpEndpoints.take(3).joinToString(", ")}",
                    evidence = "Plaintext HTTP URLs: ${httpEndpoints.take(5).joinToString(", ")}",
                    recommendation = "Upgrade all network endpoints to HTTPS / TLS 1.3.",
                    module = "NetworkSecurity",
                    affectedFile = "NetworkClient.kt",
                    status = FindingStatus.OPEN
                )
            )
        }

        val info = NetworkSecurityConfigInfo(
            allowsCleartext = allowsCleartextExplicit,
            usesNetworkSecurityConfig = usesNetSecConfig,
            customCertificatesAllowed = customCerts,
            detectedHttpEndpoints = httpEndpoints
        )

        return Pair(info, findings)
    }
}
