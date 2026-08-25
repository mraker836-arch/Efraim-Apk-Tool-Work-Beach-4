package com.example.apk.signing

import java.io.ByteArrayOutputStream
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate

/**
 * Builds standard PKCS#7 SignedData (RFC 2315 / APK v1 scheme block) for Android APK signing.
 */
object Pkcs7BlockBuilder {

    fun createSignedDataBlock(
        dataToSign: ByteArray,
        privateKey: PrivateKey,
        certificate: X509Certificate
    ): ByteArray {
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(dataToSign)
        val encryptedDigest = sig.sign()

        val certEncoded = certificate.encoded

        // SignerInfo
        val signerInfo = createSignerInfo(certificate, encryptedDigest)

        // SignedData sequence
        val version = encodeInteger(1) // PKCS7 v1
        val digestAlgorithms = encodeSet(encodeSha256AlgorithmIdentifier())
        val contentInfo = encodeContentInfoData()
        val certificates = encodeTagged(0, certEncoded)
        val signerInfos = encodeSet(signerInfo)

        val signedData = encodeSequence(
            version,
            digestAlgorithms,
            contentInfo,
            certificates,
            signerInfos
        )

        // Outer ContentInfo with OID 1.2.840.113549.1.7.2 (signedData)
        val signedDataOid = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)
        val taggedSignedData = encodeTagged(0, signedData)

        return encodeSequence(signedDataOid, taggedSignedData)
    }

    private fun createSignerInfo(certificate: X509Certificate, encryptedDigest: ByteArray): ByteArray {
        val version = encodeInteger(1)
        val issuerAndSerial = encodeSequence(
            certificate.issuerX500Principal.encoded,
            encodeInteger(certificate.serialNumber)
        )
        val digestAlgorithm = encodeSha256AlgorithmIdentifier()
        val digestEncryptionAlgorithm = encodeRsaEncryptionAlgorithmIdentifier()
        val encryptedDigestBytes = encodeOctetString(encryptedDigest)

        return encodeSequence(
            version,
            issuerAndSerial,
            digestAlgorithm,
            digestEncryptionAlgorithm,
            encryptedDigestBytes
        )
    }

    private fun encodeSha256AlgorithmIdentifier(): ByteArray {
        // OID 2.16.840.1.101.3.4.2.1 (sha-256) + NULL
        val oid = byteArrayOf(0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)
        val nullVal = byteArrayOf(0x05, 0x00)
        return encodeSequence(oid, nullVal)
    }

    private fun encodeRsaEncryptionAlgorithmIdentifier(): ByteArray {
        // OID 1.2.840.113549.1.1.1 (rsaEncryption) + NULL
        val oid = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)
        val nullVal = byteArrayOf(0x05, 0x00)
        return encodeSequence(oid, nullVal)
    }

    private fun encodeContentInfoData(): ByteArray {
        // OID 1.2.840.113549.1.7.1 (data)
        val oid = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01)
        return encodeSequence(oid)
    }

    private fun encodeInteger(value: Long): ByteArray = encodeInteger(java.math.BigInteger.valueOf(value))

    private fun encodeInteger(value: java.math.BigInteger): ByteArray {
        return encodeTlv(0x02, value.toByteArray())
    }

    private fun encodeOctetString(bytes: ByteArray): ByteArray {
        return encodeTlv(0x04, bytes)
    }

    private fun encodeTagged(tagNo: Int, content: ByteArray): ByteArray {
        val tag = 0xA0 or (tagNo and 0x1F)
        return encodeTlv(tag, content)
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
        val length = value.size
        if (length < 128) {
            baos.write(length)
        } else if (length < 256) {
            baos.write(0x81)
            baos.write(length)
        } else if (length < 65536) {
            baos.write(0x82)
            baos.write(length ushr 8)
            baos.write(length and 0xFF)
        } else {
            baos.write(0x83)
            baos.write(length ushr 16)
            baos.write((length ushr 8) and 0xFF)
            baos.write(length and 0xFF)
        }
        baos.write(value)
        return baos.toByteArray()
    }
}
