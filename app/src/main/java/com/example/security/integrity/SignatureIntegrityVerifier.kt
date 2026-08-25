package com.example.security.integrity

import com.example.apk.model.CertificateInfo
import com.example.apk.signing.SignatureVerificationResult
import com.example.apk.signing.SigningManager
import com.example.security.model.FindingCategory
import com.example.security.model.FindingStatus
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CertificateIdentity(
    val subject: String,
    val issuer: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String = "",
    val serialNumber: String = "",
    val isSelfSigned: Boolean = true,
    val isExpired: Boolean = false
)

data class SignatureIntegrityReport(
    val isValid: Boolean,
    val scheme: String,
    val identity: CertificateIdentity?,
    val certificateChanged: Boolean,
    val findings: List<SecurityFinding>
)

class SignatureIntegrityVerifier(
    private val signingManager: SigningManager
) {

    // Stored previous release certificate fingerprint
    private var lastKnownCertificateFingerprint: String? = null

    fun setLastKnownCertificateFingerprint(fingerprint: String) {
        lastKnownCertificateFingerprint = fingerprint.lowercase().trim()
    }

    suspend fun verifySignatureAndIdentity(
        apkFile: File,
        expectedFingerprint: String? = null
    ): SignatureIntegrityReport = withContext(Dispatchers.IO) {
        val findings = mutableListOf<SecurityFinding>()

        if (!apkFile.exists()) {
            val f = SecurityFinding(
                category = FindingCategory.SIGNING_INTEGRITY,
                severity = SecuritySeverity.HIGH,
                title = "APK File Missing for Signature Verification",
                description = "Cannot verify signature on nonexistent file: ${apkFile.name}",
                evidence = "Path: ${apkFile.absolutePath}",
                recommendation = "Build or import target APK before running signature verification.",
                module = "SignatureVerifier",
                affectedFile = apkFile.name,
                status = FindingStatus.OPEN
            )
            return@withContext SignatureIntegrityReport(
                isValid = false,
                scheme = "NONE",
                identity = null,
                certificateChanged = false,
                findings = listOf(f)
            )
        }

        val rawResult: SignatureVerificationResult = signingManager.verifyApk(apkFile)

        val cert = rawResult.certificates.firstOrNull()
        val identity = if (cert != null) {
            val sha256 = cert.sha256Fingerprint.ifBlank {
                cert.md5Fingerprint
            }
            CertificateIdentity(
                subject = cert.subject,
                issuer = cert.issuer,
                sha256Fingerprint = sha256.lowercase().trim(),
                sha1Fingerprint = cert.sha1Fingerprint,
                serialNumber = cert.serialNumber,
                isSelfSigned = cert.isSelfSigned,
                isExpired = false
            )
        } else null

        if (!rawResult.isValid) {
            findings.add(
                SecurityFinding(
                    category = FindingCategory.SIGNING_INTEGRITY,
                    severity = SecuritySeverity.CRITICAL,
                    title = "APK Signature Verification Failed",
                    description = "The APK is not signed or the signature block is corrupted / tampered. Android OS will reject installation.",
                    evidence = "Issues: ${rawResult.issues.joinToString(", ")}",
                    recommendation = "Sign the APK with a valid RSA/EC keystore prior to release.",
                    module = "SignatureVerifier",
                    affectedFile = apkFile.name,
                    status = FindingStatus.OPEN
                )
            )
        }

        // Certificate change detection
        var certChanged = false
        val baseline = expectedFingerprint ?: lastKnownCertificateFingerprint
        if (identity != null && baseline != null && baseline.isNotBlank()) {
            if (identity.sha256Fingerprint != baseline.lowercase().trim()) {
                certChanged = true
                findings.add(
                    SecurityFinding(
                        category = FindingCategory.SIGNING_INTEGRITY,
                        severity = SecuritySeverity.HIGH,
                        title = "SIGNING IDENTITY CHANGED",
                        description = "Current signing certificate SHA-256 (${identity.sha256Fingerprint}) differs from previous release certificate ($baseline). On Android devices, existing users will be unable to update without uninstalling.",
                        evidence = "Previous Cert: $baseline, Current Cert: ${identity.sha256Fingerprint}",
                        recommendation = "Ensure same signing key is used for release builds, or record an authorized signing key rotation with APK Signature Scheme v3 lineage.",
                        module = "SignatureVerifier",
                        affectedFile = apkFile.name,
                        status = FindingStatus.OPEN
                    )
                )
            }
        } else if (identity != null && lastKnownCertificateFingerprint == null) {
            // Register current as baseline
            lastKnownCertificateFingerprint = identity.sha256Fingerprint
        }

        SignatureIntegrityReport(
            isValid = rawResult.isValid,
            scheme = rawResult.signingScheme,
            identity = identity,
            certificateChanged = certChanged,
            findings = findings
        )
    }
}
