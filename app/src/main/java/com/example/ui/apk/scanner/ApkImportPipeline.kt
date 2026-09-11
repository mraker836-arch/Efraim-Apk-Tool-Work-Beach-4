package com.example.ui.apk.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.apk.scanner.AxmlParser
import com.example.apk.scanner.CertInspector
import com.example.core.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Explicit error states for the APK import and validation pipeline.
 */
enum class ApkImportErrorCode {
    NONE,
    INVALID_FILE,
    FILE_NOT_FOUND,
    ACCESS_DENIED,
    INVALID_APK,
    CORRUPTED_APK,
    MANIFEST_MISSING,
    PACKAGE_INFO_FAILED,
    SIGNATURE_READ_FAILED,
    UNKNOWN_ERROR
}

/**
 * Strongly typed result model for the APK Import & Validation Pipeline.
 */
data class ApkImportResult(
    val success: Boolean,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val hasManifest: Boolean,
    val hasDex: Boolean,
    val hasResources: Boolean,
    val hasSignature: Boolean,
    val signerInfo: String?,
    val certificateSha256: String?,
    val isDebuggable: Boolean,
    val warnings: List<String>,
    val errors: List<String>,
    val errorCode: ApkImportErrorCode = ApkImportErrorCode.NONE,
    val stagedFile: File? = null
) {
    companion object {
        fun failure(
            errorCode: ApkImportErrorCode,
            errorMessage: String,
            fileName: String = "unknown.apk",
            fileSize: Long = 0L,
            sha256: String = "",
            warnings: List<String> = emptyList(),
            packageName: String? = null
        ): ApkImportResult = ApkImportResult(
            success = false,
            fileName = fileName,
            fileSize = fileSize,
            sha256 = sha256,
            packageName = packageName,
            versionName = null,
            versionCode = null,
            minSdk = null,
            targetSdk = null,
            permissions = emptyList(),
            activities = emptyList(),
            services = emptyList(),
            receivers = emptyList(),
            providers = emptyList(),
            hasManifest = false,
            hasDex = false,
            hasResources = false,
            hasSignature = false,
            signerInfo = null,
            certificateSha256 = null,
            isDebuggable = false,
            warnings = warnings,
            errors = listOf(errorMessage),
            errorCode = errorCode,
            stagedFile = null
        )
    }
}

/**
 * Production-ready Real APK Import & Validation Pipeline.
 * 
 * Safely ingests untrusted APK input via Storage Access Framework (SAF)
 * or filesystem, strictly validates ZIP headers and integrity, extracts real
 * metadata via Android APIs and AXML decoder, and produces a complete
 * security and structural validation report.
 */
class ApkImportPipeline(private val context: Context) {

    private val axmlParser = AxmlParser()
    private val maxApkSizeBytes: Long = 500L * 1024 * 1024 // 500 MB maximum safety ceiling

    /**
     * Imports and validates an APK from an Android Storage Access Framework URI.
     */
    suspend fun importAndValidate(
        uri: Uri,
        fileNameHint: String? = null
    ): ApkImportResult = withContext(Dispatchers.IO) {
        // 1. Take persistable URI permissions if granted
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Document provider may not support persistable grants; proceed with active session
        }

        // 2. Resolve display name and declared size
        var resolvedFileName = fileNameHint ?: "unknown.apk"
        var declaredSize = 0L

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        cursor.getString(nameIdx)?.let { resolvedFileName = it }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) {
                        declaredSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to hint or URI path segment
            uri.lastPathSegment?.let { seg ->
                val candidate = seg.substringAfterLast('/')
                if (candidate.isNotBlank() && resolvedFileName == "unknown.apk") {
                    resolvedFileName = candidate
                }
            }
        }

        // 3. Early type and extension filter
        val lowerName = resolvedFileName.lowercase()
        val isApkExtension = lowerName.endsWith(".apk")
        val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        val isApkMime = mimeType == "application/vnd.android.package-archive" || mimeType == "application/octet-stream"

        if (!isApkExtension && mimeType != null && !isApkMime) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_FILE,
                errorMessage = "File '$resolvedFileName' is not an APK file (MIME: $mimeType).",
                fileName = resolvedFileName
            )
        }

        if (!isApkExtension && (lowerName.endsWith(".txt") || lowerName.endsWith(".pdf") || lowerName.endsWith(".png") ||
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".json") ||
                    lowerName.endsWith(".xml") || lowerName.endsWith(".mp4") || lowerName.endsWith(".zip"))
        ) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_FILE,
                errorMessage = "Selected file '$resolvedFileName' is not an Android APK archive (.apk required).",
                fileName = resolvedFileName
            )
        }

        if (declaredSize > maxApkSizeBytes) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_FILE,
                errorMessage = "File size ($declaredSize bytes) exceeds maximum limit of $maxApkSizeBytes bytes.",
                fileName = resolvedFileName
            )
        }

        // 4. Open input stream safely
        val inputStream: InputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: SecurityException) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.ACCESS_DENIED,
                errorMessage = "Storage access denied: ${e.message}",
                fileName = resolvedFileName
            )
        } catch (e: FileNotFoundException) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.FILE_NOT_FOUND,
                errorMessage = "File not found at URI: $uri",
                fileName = resolvedFileName
            )
        } catch (e: Exception) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.UNKNOWN_ERROR,
                errorMessage = "Failed to open input stream: ${e.message}",
                fileName = resolvedFileName
            )
        } ?: return@withContext ApkImportResult.failure(
            errorCode = ApkImportErrorCode.FILE_NOT_FOUND,
            errorMessage = "Input stream for URI could not be resolved: $uri",
            fileName = resolvedFileName
        )

        // 5. Stage safely to isolated sandbox cache
        val sandboxDir = File(context.cacheDir, "apk_imports").apply { mkdirs() }
        purgeStaleSandboxFiles(sandboxDir)
        val importId = "IMPORT-${UUID.randomUUID().toString().take(8).uppercase()}"
        val stagedFile = File(sandboxDir, "$importId.apk")

        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val headerBuffer = ByteArray(4)
        var headerRead = 0
        var totalBytesRead = 0L

        try {
            inputStream.use { input ->
                FileOutputStream(stagedFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        if (headerRead < 4) {
                            val toCopy = minOf(4 - headerRead, read)
                            System.arraycopy(buffer, 0, headerBuffer, headerRead, toCopy)
                            headerRead += toCopy
                        }
                        totalBytesRead += read
                        if (totalBytesRead > maxApkSizeBytes) {
                            stagedFile.delete()
                            return@withContext ApkImportResult.failure(
                                errorCode = ApkImportErrorCode.INVALID_FILE,
                                errorMessage = "APK size exceeded safety ceiling during transfer ($maxApkSizeBytes bytes).",
                                fileName = resolvedFileName
                            )
                        }
                        sha256Digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            stagedFile.delete()
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.CORRUPTED_APK,
                errorMessage = "Failed while reading input stream: ${e.message}",
                fileName = resolvedFileName
            )
        }

        val sha256 = CryptoUtils.bytesToHex(sha256Digest.digest())

        // 6. Check empty file
        if (totalBytesRead == 0L) {
            stagedFile.delete()
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_FILE,
                errorMessage = "File is empty (0 bytes).",
                fileName = resolvedFileName,
                fileSize = 0L,
                sha256 = sha256
            )
        }

        // 7. Validate ZIP magic header: 0x50 0x4B 0x03 0x04 (PK\x03\x04)
        if (headerRead < 4 || headerBuffer[0] != 0x50.toByte() || headerBuffer[1] != 0x4B.toByte() ||
            headerBuffer[2] != 0x03.toByte() || headerBuffer[3] != 0x04.toByte()
        ) {
            stagedFile.delete()
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_APK,
                errorMessage = "File is not a valid APK/ZIP archive (magic header mismatch).",
                fileName = resolvedFileName,
                fileSize = totalBytesRead,
                sha256 = sha256
            )
        }

        // 8. Run structural and metadata validation on staged file
        validateStagedApk(
            stagedFile = stagedFile,
            resolvedFileName = resolvedFileName,
            fileSize = totalBytesRead,
            sha256 = sha256
        )
    }

    /**
     * Validates an existing File directly (useful for tests and local storage imports).
     */
    suspend fun importAndValidateFile(
        file: File,
        fileNameHint: String? = null
    ): ApkImportResult = withContext(Dispatchers.IO) {
        val fileName = fileNameHint ?: file.name

        if (!file.exists()) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.FILE_NOT_FOUND,
                errorMessage = "File does not exist: ${file.absolutePath}",
                fileName = fileName
            )
        }

        if (!file.canRead()) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.ACCESS_DENIED,
                errorMessage = "Permission denied: cannot read file at ${file.absolutePath}",
                fileName = fileName
            )
        }

        val lowerName = fileName.lowercase()
        if (!lowerName.endsWith(".apk")) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_FILE,
                errorMessage = "Selected file '$fileName' does not have an .apk extension.",
                fileName = fileName
            )
        }

        val fileSize = file.length()
        if (fileSize == 0L) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_FILE,
                errorMessage = "Target APK file is empty (0 bytes).",
                fileName = fileName,
                fileSize = 0L
            )
        }

        if (fileSize > maxApkSizeBytes) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_FILE,
                errorMessage = "File size exceeds maximum limit of $maxApkSizeBytes bytes.",
                fileName = fileName,
                fileSize = fileSize
            )
        }

        // Validate ZIP magic header: 0x50 0x4B 0x03 0x04 (PK\x03\x04)
        val headerBuffer = ByteArray(4)
        val headerRead = try {
            file.inputStream().use { it.read(headerBuffer) }
        } catch (e: Exception) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.CORRUPTED_APK,
                errorMessage = "Could not read file header: ${e.message}",
                fileName = fileName
            )
        }

        if (headerRead < 4 || headerBuffer[0] != 0x50.toByte() || headerBuffer[1] != 0x4B.toByte() ||
            headerBuffer[2] != 0x03.toByte() || headerBuffer[3] != 0x04.toByte()
        ) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.INVALID_APK,
                errorMessage = "File is not a valid APK/ZIP archive (magic header mismatch).",
                fileName = fileName,
                fileSize = fileSize
            )
        }

        // Calculate SHA-256
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        try {
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    sha256Digest.update(buffer, 0, read)
                }
            }
        } catch (e: Exception) {
            return@withContext ApkImportResult.failure(
                errorCode = ApkImportErrorCode.CORRUPTED_APK,
                errorMessage = "Failed while hashing APK: ${e.message}",
                fileName = fileName
            )
        }
        val sha256 = CryptoUtils.bytesToHex(sha256Digest.digest())

        // Run validation
        validateStagedApk(
            stagedFile = file,
            resolvedFileName = fileName,
            fileSize = fileSize,
            sha256 = sha256
        )
    }

    /**
     * Internal structural and metadata validator operating on an authenticated, staged APK archive.
     */
    private fun validateStagedApk(
        stagedFile: File,
        resolvedFileName: String,
        fileSize: Long,
        sha256: String
    ): ApkImportResult {
        var hasManifest = false
        var hasDex = false
        var hasResources = false
        var hasSignature = false
        var manifestBytes: ByteArray? = null
        var signatureBlockBytes: ByteArray? = null
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // 1. Inspect ZIP Structure & Detect Critical Entries
        try {
            ZipFile(stagedFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    when {
                        name == "AndroidManifest.xml" -> {
                            hasManifest = true
                            manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                        }
                        name == "classes.dex" || (name.startsWith("classes") && name.endsWith(".dex")) -> {
                            hasDex = true
                        }
                        name == "resources.arsc" -> {
                            hasResources = true
                        }
                        name.startsWith("META-INF/") && (
                            name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC") ||
                            name.endsWith(".SF") || name == "META-INF/MANIFEST.MF"
                        ) -> {
                            hasSignature = true
                            if ((name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC")) && signatureBlockBytes == null) {
                                signatureBlockBytes = zip.getInputStream(entry).use { it.readBytes() }
                            }
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            return ApkImportResult.failure(
                errorCode = ApkImportErrorCode.CORRUPTED_APK,
                errorMessage = "Corrupted APK archive: ${e.message}",
                fileName = resolvedFileName,
                fileSize = fileSize,
                sha256 = sha256
            )
        } catch (e: Exception) {
            return ApkImportResult.failure(
                errorCode = ApkImportErrorCode.CORRUPTED_APK,
                errorMessage = "Corrupted or unreadable APK archive structure: ${e.message}",
                fileName = resolvedFileName,
                fileSize = fileSize,
                sha256 = sha256
            )
        }

        // 2. Validate AndroidManifest.xml presence
        if (!hasManifest || manifestBytes == null || manifestBytes.isEmpty()) {
            return ApkImportResult.failure(
                errorCode = ApkImportErrorCode.MANIFEST_MISSING,
                errorMessage = "AndroidManifest.xml is missing or empty in the APK root.",
                fileName = resolvedFileName,
                fileSize = fileSize,
                sha256 = sha256,
                warnings = listOf("No AndroidManifest.xml detected in root directory.")
            )
        }

        if (!hasDex) {
            warnings.add("No classes.dex found in APK archive.")
        }
        if (!hasResources) {
            warnings.add("No resources.arsc table found in APK archive.")
        }

        // 3. Extract metadata using Android PackageManager APIs
        var packageName: String? = null
        var versionName: String? = null
        var versionCode: Long? = null
        var minSdk: Int? = null
        var targetSdk: Int? = null
        var permissions: List<String> = emptyList()
        var activities: List<String> = emptyList()
        var services: List<String> = emptyList()
        var receivers: List<String> = emptyList()
        var providers: List<String> = emptyList()
        var isDebuggable = false
        var signerInfo: String? = null
        var certificateSha256: String? = null

        try {
            val pm = context.packageManager
            val packageInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flags = (PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_SIGNING_CERTIFICATES).toLong()
                pm.getPackageArchiveInfo(stagedFile.absolutePath, PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_SIGNATURES
                pm.getPackageArchiveInfo(stagedFile.absolutePath, flags)
            }

            if (packageInfo != null) {
                packageName = packageInfo.packageName
                versionName = packageInfo.versionName
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                packageInfo.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = stagedFile.absolutePath
                    appInfo.publicSourceDir = stagedFile.absolutePath
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        minSdk = appInfo.minSdkVersion
                    }
                    targetSdk = appInfo.targetSdkVersion
                    isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                }

                permissions = packageInfo.requestedPermissions?.filterNotNull() ?: emptyList()
                activities = packageInfo.activities?.map { it.name } ?: emptyList()
                services = packageInfo.services?.map { it.name } ?: emptyList()
                receivers = packageInfo.receivers?.map { it.name } ?: emptyList()
                providers = packageInfo.providers?.map { it.name } ?: emptyList()

                // Extract signing info from PackageInfo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val signingInfo = packageInfo.signingInfo
                    val sigs = if (signingInfo != null) {
                        if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
                    } else null
                    val primarySig = sigs?.firstOrNull()
                    if (primarySig != null) {
                        try {
                            val certFactory = CertificateFactory.getInstance("X.509")
                            val cert = certFactory.generateCertificate(ByteArrayInputStream(primarySig.toByteArray())) as? X509Certificate
                            if (cert != null) {
                                hasSignature = true
                                signerInfo = cert.subjectDN.name
                                certificateSha256 = CryptoUtils.formatFingerprint(CryptoUtils.calculateSha256(cert.encoded))
                            }
                        } catch (e: Exception) {
                            warnings.add("Failed to extract X.509 certificate from package signing info: ${e.message}")
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val sigs = packageInfo.signatures
                    val primarySig = sigs?.firstOrNull()
                    if (primarySig != null) {
                        try {
                            val certFactory = CertificateFactory.getInstance("X.509")
                            val cert = certFactory.generateCertificate(ByteArrayInputStream(primarySig.toByteArray())) as? X509Certificate
                            if (cert != null) {
                                hasSignature = true
                                signerInfo = cert.subjectDN.name
                                certificateSha256 = CryptoUtils.formatFingerprint(CryptoUtils.calculateSha256(cert.encoded))
                            }
                        } catch (e: Exception) {
                            warnings.add("Failed to parse signature: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            warnings.add("PackageManager archive inspection warning: ${e.message}")
        }

        // 4. Complement or fallback using AxmlParser
        if (packageName.isNullOrBlank() && manifestBytes != null) {
            try {
                val parsedAxml = axmlParser.parse(manifestBytes)
                if (parsedAxml.packageName.isNotBlank() && parsedAxml.packageName != "unknown.package") {
                    packageName = parsedAxml.packageName
                    if (versionName.isNullOrBlank() && parsedAxml.versionName.isNotBlank()) versionName = parsedAxml.versionName
                    if (versionCode == null || versionCode == 0L) versionCode = parsedAxml.versionCode
                    if (minSdk == null || minSdk == 0) minSdk = parsedAxml.minSdk
                    if (targetSdk == null || targetSdk == 0) targetSdk = parsedAxml.targetSdk
                    if (permissions.isEmpty()) permissions = parsedAxml.permissions
                    if (activities.isEmpty()) activities = parsedAxml.activities.map { it.name }
                    if (services.isEmpty()) services = parsedAxml.services.map { it.name }
                    if (receivers.isEmpty()) receivers = parsedAxml.receivers.map { it.name }
                    if (providers.isEmpty()) providers = parsedAxml.providers.map { it.name }
                    isDebuggable = parsedAxml.isDebuggable
                }
            } catch (e: Exception) {
                warnings.add("Binary AXML manifest parsing error: ${e.message}")
            }
        }

        // 5. Complement certificate parsing using CertInspector if not found via PackageInfo
        if (certificateSha256 == null && signatureBlockBytes != null) {
            try {
                val certs = CertInspector.inspectCertificates(signatureBlockBytes, hasSignature)
                val primary = certs.firstOrNull()
                if (primary != null && primary.subject != "Unavailable") {
                    signerInfo = primary.subject
                    certificateSha256 = primary.sha256Fingerprint
                }
            } catch (e: Exception) {
                warnings.add("Certificate inspection warning: ${e.message}")
            }
        }

        // 6. Security Warnings Validation
        if (!hasSignature) {
            warnings.add("Unsigned APK: No digital signature or META-INF signing block detected.")
        } else if (signerInfo == null && certificateSha256 == null) {
            warnings.add("Signature files detected in META-INF, but certificate details could not be parsed.")
        }

        if (isDebuggable) {
            warnings.add("APK is flagged as debuggable (android:debuggable=\"true\"). Production releases should disable debugging.")
        }

        // 7. Validate Package Name
        if (packageName.isNullOrBlank()) {
            return ApkImportResult.failure(
                errorCode = ApkImportErrorCode.PACKAGE_INFO_FAILED,
                errorMessage = "Failed to extract valid package name from APK manifest.",
                fileName = resolvedFileName,
                fileSize = fileSize,
                sha256 = sha256,
                warnings = warnings
            )
        }

        return ApkImportResult(
            success = true,
            fileName = resolvedFileName,
            fileSize = fileSize,
            sha256 = sha256,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            hasManifest = hasManifest,
            hasDex = hasDex,
            hasResources = hasResources,
            hasSignature = hasSignature,
            signerInfo = signerInfo,
            certificateSha256 = certificateSha256,
            isDebuggable = isDebuggable,
            warnings = warnings,
            errors = errors,
            errorCode = ApkImportErrorCode.NONE,
            stagedFile = stagedFile
        )
    }

    companion object {
        fun purgeStaleSandboxFiles(dir: File, maxAgeMs: Long = 24 * 60 * 60 * 1000L): Int {
            if (!dir.exists() || !dir.isDirectory) return 0
            val cutoff = System.currentTimeMillis() - maxAgeMs
            var purged = 0
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    if (file.delete()) purged++
                }
            }
            return purged
        }
    }
}
