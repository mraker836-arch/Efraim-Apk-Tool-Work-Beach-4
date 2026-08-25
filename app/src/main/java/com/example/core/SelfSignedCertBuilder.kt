package com.example.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds valid X.509 v3 Certificates directly in pure Java/Android ASN.1 DER format.
 */
object SelfSignedCertBuilder {

    fun buildCertificate(
        keyPair: KeyPair,
        subjectDN: String,
        serialNumber: BigInteger,
        startDate: Date,
        endDate: Date
    ): X509Certificate {
        val tbsCertificate = createTbsCertificate(keyPair, subjectDN, serialNumber, startDate, endDate)

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbsCertificate)
        val signatureBytes = sig.sign()

        val fullCertDer = encodeSequence(
            tbsCertificate,
            encodeSha256WithRsaAlgorithmId(),
            encodeBitString(signatureBytes)
        )

        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(ByteArrayInputStream(fullCertDer)) as X509Certificate
    }

    private fun createTbsCertificate(
        keyPair: KeyPair,
        subjectDN: String,
        serialNumber: BigInteger,
        startDate: Date,
        endDate: Date
    ): ByteArray {
        val version = encodeExplicit(0, encodeInteger(BigInteger.valueOf(2))) // v3
        val serial = encodeInteger(serialNumber)
        val signatureAlg = encodeSha256WithRsaAlgorithmId()
        val issuer = encodeName(subjectDN)
        val validity = encodeValidity(startDate, endDate)
        val subject = encodeName(subjectDN)
        val subjectPublicKeyInfo = keyPair.public.encoded

        return encodeSequence(
            version,
            serial,
            signatureAlg,
            issuer,
            validity,
            subject,
            subjectPublicKeyInfo
        )
    }

    private fun encodeSha256WithRsaAlgorithmId(): ByteArray {
        // OID 1.2.840.113549.1.1.11 (sha256WithRSAEncryption) + NULL
        val oidBytes = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)
        val nullBytes = byteArrayOf(0x05, 0x00)
        return encodeSequence(oidBytes, nullBytes)
    }

    private fun encodeName(dn: String): ByteArray {
        // Parses simple "CN=Name, O=Org, C=US" into RDNSequence
        val rdnSets = mutableListOf<ByteArray>()
        val pairs = dn.split(",")
        for (rawPair in pairs) {
            val part = rawPair.trim()
            if (part.contains("=")) {
                val split = part.split("=", limit = 2)
                val key = split[0].trim().uppercase()
                val value = split[1].trim()
                val oid = when (key) {
                    "CN" -> byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03) // 2.5.4.3
                    "O" -> byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x0A)  // 2.5.4.10
                    "OU" -> byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x0B) // 2.5.4.11
                    "C" -> byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x06)  // 2.5.4.6
                    else -> byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)
                }
                val valueDer = encodeUtf8String(value)
                val atv = encodeSequence(oid, valueDer)
                val rdnSet = encodeSet(atv)
                rdnSets.add(rdnSet)
            }
        }
        return encodeSequence(*rdnSets.toTypedArray())
    }

    private fun encodeValidity(startDate: Date, endDate: Date): ByteArray {
        val dateFormat = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val notBefore = encodeUtcTime(dateFormat.format(startDate))
        val notAfter = encodeUtcTime(dateFormat.format(endDate))
        return encodeSequence(notBefore, notAfter)
    }

    private fun encodeUtcTime(timeStr: String): ByteArray {
        val bytes = timeStr.toByteArray(Charsets.US_ASCII)
        return encodeTlv(0x17, bytes)
    }

    private fun encodeUtf8String(str: String): ByteArray {
        val bytes = str.toByteArray(Charsets.UTF_8)
        return encodeTlv(0x0C, bytes)
    }

    private fun encodeInteger(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return encodeTlv(0x02, bytes)
    }

    private fun encodeBitString(bytes: ByteArray): ByteArray {
        val out = ByteArray(bytes.size + 1)
        out[0] = 0 // 0 unused bits
        System.arraycopy(bytes, 0, out, 1, bytes.size)
        return encodeTlv(0x03, out)
    }

    private fun encodeExplicit(tagNo: Int, content: ByteArray): ByteArray {
        val tag = (0xA0 or (tagNo and 0x1F)).toByte()
        return encodeTlv(tag.toInt(), content)
    }

    private fun encodeSequence(vararg elements: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        for (el in elements) baos.write(el)
        return encodeTlv(0x30, baos.toByteArray())
    }

    private fun encodeSet(vararg elements: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        for (el in elements) baos.write(el)
        return encodeTlv(0x31, baos.toByteArray())
    }

    private fun encodeTlv(tag: Int, value: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        baos.write(tag)
        encodeLength(value.size, baos)
        baos.write(value)
        return baos.toByteArray()
    }

    private fun encodeLength(length: Int, out: ByteArrayOutputStream) {
        if (length < 128) {
            out.write(length)
        } else if (length < 256) {
            out.write(0x81)
            out.write(length)
        } else if (length < 65536) {
            out.write(0x82)
            out.write(length ushr 8)
            out.write(length and 0xFF)
        } else {
            out.write(0x83)
            out.write(length ushr 16)
            out.write((length ushr 8) and 0xFF)
            out.write(length and 0xFF)
        }
    }
}
