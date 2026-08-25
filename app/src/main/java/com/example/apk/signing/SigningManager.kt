package com.example.apk.signing

import android.content.Context
import android.util.Base64
import com.example.apk.model.CertificateInfo
import com.example.apk.scanner.CertInspector
import com.example.core.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPair
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class KeyEntryInfo(
    val alias: String,
    val algorithm: String,
    val sha256Fingerprint: String,
    val subject: String,
    val validUntil: String,
    val isDefault: Boolean = false
)

data class SignatureVerificationResult(
    val isValid: Boolean,
    val status: String,
    val signingScheme: String,
    val certificates: List<CertificateInfo>,
    val verifiedEntriesCount: Int,
    val issues: List<String> = emptyList(),
    val sha256Fingerprint: String = ""
)

class SigningManager(private val context: Context) {

    private val keystoreFile: File by lazy {
        File(context.filesDir, "workbench_keys.p12")
    }
    private val defaultPassword = "apk_workbench_secure_pass".toCharArray()

    init {
        ensureDefaultKeyExists()
    }

    private fun ensureDefaultKeyExists() {
        try {
            val keyStore = loadKeyStore()
            if (!keyStore.containsAlias("default_release_key")) {
                generateAndSaveKeySync("default_release_key", "CN=EFRAIM APK Workbench Release, O=Android Developers, C=US")
            }
        } catch (e: Exception) {
            // Error handling
        }
    }

    private fun loadKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("PKCS12")
        if (keystoreFile.exists()) {
            FileInputStream(keystoreFile).use { fis ->
                keyStore.load(fis, defaultPassword)
            }
        } else {
            keyStore.load(null, defaultPassword)
        }
        return keyStore
    }

    private fun saveKeyStore(keyStore: KeyStore) {
        FileOutputStream(keystoreFile).use { fos ->
            keyStore.store(fos, defaultPassword)
        }
    }

    private fun generateAndSaveKeySync(alias: String, subjectDN: String): KeyEntryInfo {
        val keyPair = CryptoUtils.generateRsaKeyPair(2048)
        val cert = CryptoUtils.generateSelfSignedCertificate(keyPair, subjectDN)

        val keyStore = loadKeyStore()
        keyStore.setKeyEntry(
            alias,
            keyPair.private,
            defaultPassword,
            arrayOf(cert)
        )
        saveKeyStore(keyStore)

        val certInfo = CertInspector.toCertificateInfo(cert)
        return KeyEntryInfo(
            alias = alias,
            algorithm = certInfo.algorithm,
            sha256Fingerprint = certInfo.sha256Fingerprint,
            subject = certInfo.subject,
            validUntil = certInfo.validUntil,
            isDefault = alias == "default_release_key"
        )
    }

    suspend fun listKeys(): List<KeyEntryInfo> = withContext(Dispatchers.IO) {
        val keyStore = loadKeyStore()
        val list = mutableListOf<KeyEntryInfo>()
        val aliases = keyStore.aliases()

        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            val cert = keyStore.getCertificate(alias) as? X509Certificate
            if (cert != null) {
                val certInfo = CertInspector.toCertificateInfo(cert)
                list.add(
                    KeyEntryInfo(
                        alias = alias,
                        algorithm = certInfo.algorithm,
                        sha256Fingerprint = certInfo.sha256Fingerprint,
                        subject = certInfo.subject,
                        validUntil = certInfo.validUntil,
                        isDefault = alias == "default_release_key"
                    )
                )
            }
        }
        list
    }

    suspend fun generateAndSaveKey(
        alias: String,
        subjectDN: String = "CN=Custom Developer Key, O=EFRAIM Workbench, C=US"
    ): KeyEntryInfo = withContext(Dispatchers.IO) {
        generateAndSaveKeySync(alias, subjectDN)
    }

    /**
     * Signs an APK file with APK v1 / JAR signing scheme using the chosen key.
     */
    suspend fun signApk(
        inputApk: File,
        outputApk: File,
        alias: String = "default_release_key"
    ): File = withContext(Dispatchers.IO) {
        val keyStore = loadKeyStore()
        val privateKey = keyStore.getKey(alias, defaultPassword) as? PrivateKey
            ?: throw IllegalStateException("Private key not found for alias: $alias")
        val certificate = keyStore.getCertificate(alias) as? X509Certificate
            ?: throw IllegalStateException("Certificate not found for alias: $alias")

        // 1. Read input APK and collect entries (excluding old META-INF signatures)
        val entriesData = mutableMapOf<String, ByteArray>()
        ZipFile(inputApk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (!entry.isDirectory && !isSignatureFile(name)) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    entriesData[name] = bytes
                }
            }
        }

        // 2. Build MANIFEST.MF
        val manifestBaos = ByteArrayOutputStream()
        val mfLines = StringBuilder()
        mfLines.append("Manifest-Version: 1.0\r\n")
        mfLines.append("Created-By: 1.0 (EFRAIM APK WORKBENCH TOOL M)\r\n")
        mfLines.append("\r\n")

        val entryDigests = mutableMapOf<String, String>()
        val sha256Digest = MessageDigest.getInstance("SHA-256")

        for ((name, data) in entriesData.toSortedMap()) {
            val hash = sha256Digest.digest(data)
            val base64Hash = Base64.encodeToString(hash, Base64.NO_WRAP)
            entryDigests[name] = base64Hash

            mfLines.append("Name: $name\r\n")
            mfLines.append("SHA-256-Digest: $base64Hash\r\n")
            mfLines.append("\r\n")
        }
        val manifestBytes = mfLines.toString().toByteArray(Charsets.UTF_8)

        // 3. Build CERT.SF
        val sfLines = StringBuilder()
        sfLines.append("Signature-Version: 1.0\r\n")
        sfLines.append("Created-By: 1.0 (EFRAIM APK WORKBENCH TOOL M)\r\n")
        val mfDigest = Base64.encodeToString(sha256Digest.digest(manifestBytes), Base64.NO_WRAP)
        sfLines.append("SHA-256-Digest-Manifest: $mfDigest\r\n")
        sfLines.append("\r\n")

        for ((name, data) in entriesData.toSortedMap()) {
            val sectionHeader = "Name: $name\r\nSHA-256-Digest: ${entryDigests[name]}\r\n\r\n".toByteArray(Charsets.UTF_8)
            val sectionDigest = Base64.encodeToString(sha256Digest.digest(sectionHeader), Base64.NO_WRAP)
            sfLines.append("Name: $name\r\n")
            sfLines.append("SHA-256-Digest: $sectionDigest\r\n")
            sfLines.append("\r\n")
        }
        val certSfBytes = sfLines.toString().toByteArray(Charsets.UTF_8)

        // 4. Build PKCS#7 signed block (CERT.RSA)
        val certRsaBytes = Pkcs7BlockBuilder.createSignedDataBlock(certSfBytes, privateKey, certificate)

        // 5. Write everything into output APK
        outputApk.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputApk)).use { zos ->
            // First write signature files in META-INF
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write(manifestBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            zos.write(certSfBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zos.write(certRsaBytes)
            zos.closeEntry()

            // Write all original content files
            for ((name, bytes) in entriesData) {
                val entry = ZipEntry(name)
                // Keep .so libraries and uncompressed assets stored if necessary
                if (name.endsWith(".so") || name.endsWith(".png") || name.endsWith(".jpg")) {
                    entry.method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        outputApk
    }

    suspend fun verifyApk(apkFile: File): SignatureVerificationResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<String>()
        val certificates = mutableListOf<CertificateInfo>()
        var verifiedEntries = 0
        var isValid = false
        var signingScheme = "None"

        try {
            var manifestBytes: ByteArray? = null
            var certSfBytes: ByteArray? = null
            var certRsaBytes: ByteArray? = null
            val entriesData = mutableMapOf<String, ByteArray>()

            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name == "META-INF/MANIFEST.MF") {
                        manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                    } else if (name == "META-INF/CERT.SF") {
                        certSfBytes = zip.getInputStream(entry).use { it.readBytes() }
                    } else if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        certRsaBytes = zip.getInputStream(entry).use { it.readBytes() }
                    } else if (!entry.isDirectory && !isSignatureFile(name)) {
                        entriesData[name] = zip.getInputStream(entry).use { it.readBytes() }
                    }
                }
            }

            if (manifestBytes == null) {
                issues.add("Missing META-INF/MANIFEST.MF")
            }
            if (certSfBytes == null) {
                issues.add("Missing META-INF/CERT.SF")
            }
            if (certRsaBytes == null) {
                issues.add("Missing META-INF/CERT.RSA signature block")
            }

            if (manifestBytes != null && certSfBytes != null && certRsaBytes != null) {
                signingScheme = "APK Signature Scheme v1 (JAR)"
                val extractedCerts = CertInspector.extractCertificates(certRsaBytes!!)
                certificates.addAll(extractedCerts)

                // Verify file digests against MANIFEST.MF
                val sha256 = MessageDigest.getInstance("SHA-256")
                val manifestText = String(manifestBytes!!, Charsets.UTF_8)

                for ((name, data) in entriesData) {
                    val actualDigest = Base64.encodeToString(sha256.digest(data), Base64.NO_WRAP)
                    val expectedPattern = "Name: $name"
                    if (manifestText.contains(expectedPattern)) {
                        if (manifestText.contains(actualDigest)) {
                            verifiedEntries++
                        } else {
                            issues.add("Digest mismatch for entry: $name")
                        }
                    } else {
                        issues.add("Unlisted entry in manifest: $name")
                    }
                }

                if (issues.isEmpty() && certificates.isNotEmpty()) {
                    isValid = true
                }
            }
        } catch (e: Exception) {
            issues.add("Verification exception: ${e.message}")
        }

        SignatureVerificationResult(
            isValid = isValid,
            status = if (isValid) "VERIFIED_VALID" else "VERIFICATION_FAILED",
            signingScheme = signingScheme,
            certificates = certificates,
            verifiedEntriesCount = verifiedEntries,
            issues = issues,
            sha256Fingerprint = certificates.firstOrNull()?.sha256Fingerprint ?: ""
        )
    }

    private fun isSignatureFile(name: String): Boolean {
        val upper = name.uppercase()
        return upper.startsWith("META-INF/") && (
            upper.endsWith(".SF") || upper.endsWith(".RSA") ||
            upper.endsWith(".DSA") || upper.endsWith(".EC") ||
            upper.endsWith(".MF") || upper.endsWith(".SIG")
        )
    }
}
