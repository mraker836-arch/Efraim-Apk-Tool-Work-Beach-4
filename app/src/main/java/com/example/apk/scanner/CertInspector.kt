package com.example.apk.scanner

import com.example.apk.model.CertificateInfo
import com.example.core.CryptoUtils
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale

object CertInspector {

    fun extractCertificates(signatureBlockBytes: ByteArray): List<CertificateInfo> {
        val certs = mutableListOf<CertificateInfo>()
        try {
            val factory = CertificateFactory.getInstance("X.509")
            val certCollection = factory.generateCertificates(ByteArrayInputStream(signatureBlockBytes))
            for (cert in certCollection) {
                if (cert is X509Certificate) {
                    certs.add(toCertificateInfo(cert))
                }
            }
        } catch (e: Exception) {
            // Fallback: search for standard X509 certificate sequence pattern inside PKCS#7 block
            val directCert = findAndParseEmbeddedCert(signatureBlockBytes)
            if (directCert != null) {
                certs.add(directCert)
            }
        }
        return certs
    }

    fun toCertificateInfo(cert: X509Certificate): CertificateInfo {
        val encoded = cert.encoded
        val sha256 = CryptoUtils.formatFingerprint(CryptoUtils.calculateSha256(encoded))
        val sha1 = CryptoUtils.formatFingerprint(CryptoUtils.calculateSha1(encoded))
        val md5 = CryptoUtils.formatFingerprint(CryptoUtils.bytesToHex(java.security.MessageDigest.getInstance("MD5").digest(encoded)))

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val validFrom = dateFormat.format(cert.notBefore)
        val validUntil = dateFormat.format(cert.notAfter)

        val isSelfSigned = try {
            cert.verify(cert.publicKey)
            true
        } catch (e: Exception) {
            false
        }

        val keySize = when (val pubKey = cert.publicKey) {
            is java.security.interfaces.RSAPublicKey -> pubKey.modulus.bitLength()
            is java.security.interfaces.ECPublicKey -> pubKey.params.curve.field.fieldSize
            else -> 2048
        }

        return CertificateInfo(
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            serialNumber = cert.serialNumber.toString(16).uppercase(),
            sha256Fingerprint = sha256,
            sha1Fingerprint = sha1,
            md5Fingerprint = md5,
            algorithm = "${cert.sigAlgName} (${cert.publicKey.algorithm} $keySize-bit)",
            validFrom = validFrom,
            validUntil = validUntil,
            isSelfSigned = isSelfSigned,
            keySizeBits = keySize
        )
    }

    private fun findAndParseEmbeddedCert(bytes: ByteArray): CertificateInfo? {
        val factory = CertificateFactory.getInstance("X.509")
        // Scan for ASN.1 SEQUENCE tag 0x30 0x82 pattern
        for (i in 0 until (bytes.size - 4)) {
            if (bytes[i] == 0x30.toByte() && bytes[i + 1] == 0x82.toByte()) {
                try {
                    val subStream = ByteArrayInputStream(bytes, i, bytes.size - i)
                    val cert = factory.generateCertificate(subStream) as? X509Certificate
                    if (cert != null) {
                        return toCertificateInfo(cert)
                    }
                } catch (e: Exception) {
                    // continue search
                }
            }
        }
        return null
    }
}
