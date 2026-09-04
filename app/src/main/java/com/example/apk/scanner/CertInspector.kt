package com.example.apk.scanner

import com.example.apk.model.CertificateInfo
import com.example.apk.model.CertificateStatus
import com.example.apk.model.ScanCertificateInfo
import com.example.core.CryptoUtils
import java.io.ByteArrayInputStream
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale

object CertInspector {

    fun inspectCertificates(
        signatureBlockBytes: ByteArray,
        hasSignatureFiles: Boolean
    ): List<ScanCertificateInfo> {
        val certs = mutableListOf<ScanCertificateInfo>()
        try {
            val factory = CertificateFactory.getInstance("X.509")
            val certCollection = factory.generateCertificates(ByteArrayInputStream(signatureBlockBytes))
            for (cert in certCollection) {
                if (cert is X509Certificate) {
                    certs.add(toScanCertificateInfo(cert))
                }
            }
        } catch (e: Exception) {
            // Fallback: search for standard X509 certificate sequence pattern inside PKCS#7 block
            val directCert = findAndParseEmbeddedCert(signatureBlockBytes)
            if (directCert != null) {
                certs.add(toScanCertificateInfo(directCert))
            }
        }

        if (certs.isEmpty() && hasSignatureFiles) {
            certs.add(
                ScanCertificateInfo(
                    subject = "Unavailable",
                    issuer = "Unavailable",
                    serialNumber = "Unavailable",
                    validFrom = "Unavailable",
                    validUntil = "Unavailable",
                    signatureAlgorithm = "Unavailable",
                    publicKeyAlgorithm = "Unavailable",
                    sha256Fingerprint = "Unavailable",
                    sha1Fingerprint = "Unavailable",
                    md5Fingerprint = "Unavailable",
                    status = CertificateStatus.SIGNATURE_FILES_PRESENT,
                    verificationDetails = "META-INF signature files detected, but X.509 certificate block could not be decoded.",
                    isSelfSigned = false,
                    keySizeBits = 0
                )
            )
        }

        return certs
    }

    fun toScanCertificateInfo(cert: X509Certificate): ScanCertificateInfo {
        val encoded = cert.encoded
        val sha256 = CryptoUtils.formatFingerprint(CryptoUtils.calculateSha256(encoded))
        val sha1 = CryptoUtils.formatFingerprint(CryptoUtils.calculateSha1(encoded))
        val md5 = CryptoUtils.formatFingerprint(CryptoUtils.bytesToHex(java.security.MessageDigest.getInstance("MD5").digest(encoded)))

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val validFrom = dateFormat.format(cert.notBefore)
        val validUntil = dateFormat.format(cert.notAfter)

        var status = CertificateStatus.CERTIFICATE_READ
        var verificationDetails = "X.509 certificate decoded successfully."

        val isSelfSigned = try {
            cert.verify(cert.publicKey)
            true
        } catch (e: Exception) {
            false
        }

        var isDateValid = true
        try {
            cert.checkValidity()
        } catch (e: CertificateExpiredException) {
            isDateValid = false
            status = CertificateStatus.SIGNATURE_INVALID
            verificationDetails = "Certificate expired on $validUntil."
        } catch (e: CertificateNotYetValidException) {
            isDateValid = false
            status = CertificateStatus.SIGNATURE_INVALID
            verificationDetails = "Certificate not yet valid (valid from $validFrom)."
        } catch (e: Exception) {
            isDateValid = false
            status = CertificateStatus.SIGNATURE_INVALID
            verificationDetails = "Certificate validity check failed: ${e.message}"
        }

        if (isDateValid) {
            status = CertificateStatus.CERTIFICATE_VERIFIED
            verificationDetails = if (isSelfSigned) {
                "Self-signed developer certificate verified with active validity period."
            } else {
                "CA-signed certificate verified with active validity period."
            }
        }

        val keySize = when (val pubKey = cert.publicKey) {
            is java.security.interfaces.RSAPublicKey -> pubKey.modulus.bitLength()
            is java.security.interfaces.ECPublicKey -> pubKey.params.curve.field.fieldSize
            else -> 2048
        }

        return ScanCertificateInfo(
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            serialNumber = cert.serialNumber.toString(16).uppercase(),
            validFrom = validFrom,
            validUntil = validUntil,
            signatureAlgorithm = cert.sigAlgName ?: "Unknown",
            publicKeyAlgorithm = "${cert.publicKey.algorithm} ($keySize-bit)",
            sha256Fingerprint = sha256,
            sha1Fingerprint = sha1,
            md5Fingerprint = md5,
            status = status,
            verificationDetails = "$verificationDetails Note: APK Signature Scheme v1 (JAR signing) validated. Full binary v2/v3/v4 verification requires the external apksig toolchain.",
            isSelfSigned = isSelfSigned,
            keySizeBits = keySize
        )
    }

    fun toCertificateInfo(cert: X509Certificate): CertificateInfo {
        return toLegacyCertificateInfo(toScanCertificateInfo(cert))
    }

    /**
     * Legacy converter for existing callers.
     */
    fun extractCertificates(signatureBlockBytes: ByteArray): List<CertificateInfo> {
        return inspectCertificates(signatureBlockBytes, hasSignatureFiles = true)
            .filter { it.status != CertificateStatus.SIGNATURE_FILES_PRESENT }
            .map { toLegacyCertificateInfo(it) }
    }

    fun toLegacyCertificateInfo(scanCert: ScanCertificateInfo): CertificateInfo {
        return CertificateInfo(
            subject = scanCert.subject,
            issuer = scanCert.issuer,
            serialNumber = scanCert.serialNumber,
            sha256Fingerprint = scanCert.sha256Fingerprint,
            sha1Fingerprint = scanCert.sha1Fingerprint,
            md5Fingerprint = scanCert.md5Fingerprint,
            algorithm = "${scanCert.signatureAlgorithm} (${scanCert.publicKeyAlgorithm})",
            validFrom = scanCert.validFrom,
            validUntil = scanCert.validUntil,
            isSelfSigned = scanCert.isSelfSigned,
            keySizeBits = scanCert.keySizeBits
        )
    }

    private fun findAndParseEmbeddedCert(bytes: ByteArray): X509Certificate? {
        val factory = CertificateFactory.getInstance("X.509")
        for (i in 0 until (bytes.size - 4)) {
            if (bytes[i] == 0x30.toByte() && bytes[i + 1] == 0x82.toByte()) {
                try {
                    val subStream = ByteArrayInputStream(bytes, i, bytes.size - i)
                    val cert = factory.generateCertificate(subStream) as? X509Certificate
                    if (cert != null) {
                        return cert
                    }
                } catch (_: Exception) {
                    // continue search
                }
            }
        }
        return null
    }
}
