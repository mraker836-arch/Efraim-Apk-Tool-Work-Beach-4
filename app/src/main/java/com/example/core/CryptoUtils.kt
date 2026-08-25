package com.example.core

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

object CryptoUtils {

    fun calculateSha256(file: File): String {
        return calculateDigest(file, "SHA-256")
    }

    fun calculateMd5(file: File): String {
        return calculateDigest(file, "MD5")
    }

    fun calculateSha1(file: File): String {
        return calculateDigest(file, "SHA-1")
    }

    fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return bytesToHex(hash)
    }

    fun calculateSha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes)
        return bytesToHex(hash)
    }

    private fun calculateDigest(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return bytesToHex(digest.digest())
    }

    fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789ABCDEF".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun formatFingerprint(hex: String): String {
        val clean = hex.replace(":", "").uppercase()
        return clean.chunked(2).joinToString(":")
    }

    fun generateRsaKeyPair(keySize: Int = 2048): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(keySize, SecureRandom())
        return keyGen.generateKeyPair()
    }

    fun generateEcKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256, SecureRandom())
        return keyGen.generateKeyPair()
    }

    /**
     * Parses an X.509 certificate from DER or PEM input stream.
     */
    fun parseX509Certificate(inputStream: InputStream): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(inputStream) as X509Certificate
    }

    /**
     * Generates a self-signed X.509 certificate for local APK signing.
     * Uses standard ASN.1 / X509 v3 structure encoding.
     */
    fun generateSelfSignedCertificate(
        keyPair: KeyPair,
        subjectDN: String = "CN=EFRAIM APK Workbench Developer, O=Local Workspace, C=US",
        validityDays: Int = 365 * 25 // 25 years default for Android keys
    ): X509Certificate {
        val now = System.currentTimeMillis()
        val startDate = Date(now - 1000L * 60 * 60 * 24) // 1 day ago
        val endDate = Date(now + 1000L * 60 * 60 * 24 * validityDays)
        val serialNumber = BigInteger(64, SecureRandom())

        // Build a standard self-signed certificate using Java Certificate generator or fallback ASN.1
        val cert = SelfSignedCertBuilder.buildCertificate(
            keyPair = keyPair,
            subjectDN = subjectDN,
            serialNumber = serialNumber,
            startDate = startDate,
            endDate = endDate
        )
        return cert
    }
}
