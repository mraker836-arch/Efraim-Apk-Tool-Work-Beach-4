package com.example.apk.scanner

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.apk.model.ApkFileInfo
import com.example.apk.model.ApkScanResult
import com.example.apk.model.CertificateStatus
import com.example.apk.model.ComponentInfo
import com.example.apk.model.DexInfo
import com.example.apk.model.ManifestInfo
import com.example.apk.model.NativeLibraryInfo
import com.example.apk.model.ProtectionCategory
import com.example.apk.model.RiskLevel
import com.example.apk.model.ScanCertificateInfo
import com.example.apk.model.ScanPermissionInfo
import com.example.apk.model.ScanStatus
import com.example.core.CryptoUtils
import com.example.database.ApkScanDao
import com.example.database.ApkScanEntity
import com.example.security.model.SecurityFinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ApkScanPipeline(
    private val apkScanDao: ApkScanDao? = null,
    private val maxApkSizeBytes: Long = 500L * 1024L * 1024L // 500 MB default max
) {
    private val _scanStatus = MutableStateFlow(ScanStatus.QUEUED)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _currentResult = MutableStateFlow<ApkScanResult?>(null)
    val currentResult: StateFlow<ApkScanResult?> = _currentResult.asStateFlow()

    fun setCurrentResult(result: ApkScanResult?) {
        _currentResult.value = result
        if (result != null) {
            _scanStatus.value = result.status
            _statusMessage.value = "Loaded scan: ${result.scanId}"
        }
    }

    suspend fun scanFromUri(
        context: Context,
        uri: Uri,
        fileNameHint: String? = null,
        isSample: Boolean = false
    ): Result<ApkScanResult> = withContext(Dispatchers.IO) {
        val scanId = "SCAN-${UUID.randomUUID().toString().take(8).uppercase()}"
        _scanStatus.value = ScanStatus.QUEUED
        _statusMessage.value = "Preparing input stream..."

        // 1. Take persistable URI permissions if possible
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Permission grant might not be persistable, continue with active read
        }

        // 2. Resolve display name and declared size
        var resolvedFileName = fileNameHint ?: "unknown.apk"
        var declaredSize = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
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
            // Fallback to hint
        }

        if (declaredSize > maxApkSizeBytes) {
            _scanStatus.value = ScanStatus.FAILED
            _statusMessage.value = "File exceeds maximum size limit (500 MB)"
            return@withContext Result.failure(IllegalArgumentException("File size ($declaredSize bytes) exceeds maximum limit of $maxApkSizeBytes bytes."))
        }

        // 3. Stage APK to isolated sandbox cache for random-access Zip parsing
        val tempDir = File(context.cacheDir, "apk_scans").apply { mkdirs() }
        purgeStaleSandboxFiles(tempDir)
        val tempApkFile = File(tempDir, "$scanId.apk")

        try {
            _scanStatus.value = ScanStatus.READING
            _statusMessage.value = "Reading APK from Storage Access Framework..."
            currentCoroutineContext().ensureActive()

            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val md5Digest = MessageDigest.getInstance("MD5")

            var totalBytesRead = 0L
            val headerBuffer = ByteArray(4)
            var headerRead = 0

            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open input stream for URI: $uri")

            inputStream.use { input ->
                FileOutputStream(tempApkFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()

                        if (headerRead < 4) {
                            val toCopy = minOf(4 - headerRead, bytesRead)
                            System.arraycopy(buffer, 0, headerBuffer, headerRead, toCopy)
                            headerRead += toCopy
                        }

                        totalBytesRead += bytesRead
                        if (totalBytesRead > maxApkSizeBytes) {
                            throw IllegalArgumentException("APK size exceeded maximum limit of $maxApkSizeBytes bytes during transfer.")
                        }

                        sha256Digest.update(buffer, 0, bytesRead)
                        md5Digest.update(buffer, 0, bytesRead)
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            // Validate ZIP magic header: 0x50 0x4B 0x03 0x04 (PK\x03\x04)
            if (headerRead < 4 || headerBuffer[0] != 0x50.toByte() || headerBuffer[1] != 0x4B.toByte() ||
                headerBuffer[2] != 0x03.toByte() || headerBuffer[3] != 0x04.toByte()
            ) {
                _scanStatus.value = ScanStatus.FAILED
                _statusMessage.value = "Invalid file: Not a valid ZIP/APK archive"
                tempApkFile.delete()
                return@withContext Result.failure(IllegalArgumentException("File is not a valid APK/ZIP archive (magic header mismatch)."))
            }

            _scanStatus.value = ScanStatus.HASHING
            _statusMessage.value = "Calculating SHA-256 and MD5 checksums..."
            val sha256 = CryptoUtils.bytesToHex(sha256Digest.digest())
            val md5 = CryptoUtils.bytesToHex(md5Digest.digest())

            // 4. Run pipeline on staged file
            val result = executeAnalysis(
                apkFile = tempApkFile,
                scanId = scanId,
                fileName = resolvedFileName,
                fileSize = totalBytesRead,
                sha256 = sha256,
                md5 = md5,
                uriString = uri.toString(),
                mimeType = "application/vnd.android.package-archive",
                isSample = isSample
            )

            // Persist to Room
            apkScanDao?.let { dao ->
                try {
                    val scanEntity = ApkScanEntity(
                        scanId = result.scanId,
                        fileName = result.fileInfo.fileName,
                        fileSize = result.fileInfo.fileSize,
                        sha256 = result.fileInfo.sha256,
                        md5 = result.fileInfo.md5,
                        timestamp = result.completedAt,
                        status = result.status.name,
                        manifestSummary = "${result.manifestInfo.packageName} v${result.manifestInfo.versionName} (${result.manifestInfo.versionCode})",
                        dexCount = result.fileInfo.dexCount,
                        abiSummary = if (result.abiCoverage.isEmpty()) "Pure Java / DEX" else result.abiCoverage.joinToString(),
                        certificateSummary = result.certificatesList.firstOrNull()?.let { "${it.subject} [${it.status}]" } ?: "No Signature",
                        securityFindingCount = result.securityFindings.size,
                        errorMessage = result.errorMessage,
                        permissionCount = result.permissionsList.size,
                        certificateStatus = result.certificatesList.firstOrNull()?.status?.name ?: if (result.fileInfo.signingRelatedFiles.isNotEmpty()) CertificateStatus.SIGNATURE_FILES_PRESENT.name else CertificateStatus.SIGNATURE_UNAVAILABLE.name,
                        packageName = result.manifestInfo.packageName,
                        uriString = result.fileInfo.uri,
                        rawJson = com.example.apk.export.ScanReportExporter.generateJsonReport(result)
                    )
                    dao.insertScan(scanEntity)
                } catch (_: Exception) {
                    // Fail gracefully on persistence error
                }
            }

            _currentResult.value = result
            _scanStatus.value = ScanStatus.COMPLETED
            _statusMessage.value = "Analysis completed successfully"
            Result.success(result)
        } catch (e: CancellationException) {
            _scanStatus.value = ScanStatus.FAILED
            _statusMessage.value = "Analysis cancelled by user"
            apkScanDao?.let { dao ->
                try {
                    dao.insertScan(
                        ApkScanEntity(
                            scanId = scanId,
                            fileName = fileNameHint ?: uri.lastPathSegment ?: "unknown.apk",
                            fileSize = 0L,
                            sha256 = "",
                            md5 = "",
                            timestamp = System.currentTimeMillis(),
                            status = "CANCELLED",
                            manifestSummary = "Scan cancelled",
                            dexCount = 0,
                            abiSummary = "Unknown",
                            certificateSummary = "Unknown",
                            securityFindingCount = 0,
                            errorMessage = "Analysis cancelled by user",
                            permissionCount = 0,
                            certificateStatus = CertificateStatus.SIGNATURE_UNAVAILABLE.name,
                            packageName = "",
                            uriString = uri.toString(),
                            rawJson = null
                        )
                    )
                } catch (_: Exception) {}
            }
            tempApkFile.delete()
            throw e
        } catch (e: Exception) {
            _scanStatus.value = ScanStatus.FAILED
            _statusMessage.value = "Analysis failed: ${e.localizedMessage ?: e.message}"
            apkScanDao?.let { dao ->
                try {
                    dao.insertScan(
                        ApkScanEntity(
                            scanId = scanId,
                            fileName = fileNameHint ?: uri.lastPathSegment ?: "unknown.apk",
                            fileSize = 0L,
                            sha256 = "",
                            md5 = "",
                            timestamp = System.currentTimeMillis(),
                            status = ScanStatus.FAILED.name,
                            manifestSummary = "Analysis failed",
                            dexCount = 0,
                            abiSummary = "Unknown",
                            certificateSummary = "Unknown",
                            securityFindingCount = 0,
                            errorMessage = e.localizedMessage ?: e.message ?: "Analysis failed",
                            permissionCount = 0,
                            certificateStatus = CertificateStatus.SIGNATURE_UNAVAILABLE.name,
                            packageName = "",
                            uriString = uri.toString(),
                            rawJson = null
                        )
                    )
                } catch (_: Exception) {}
            }
            tempApkFile.delete()
            Result.failure(e)
        }
    }

    suspend fun scanFromFile(
        file: File,
        scanId: String = "SCAN-${UUID.randomUUID().toString().take(8).uppercase()}",
        fileNameHint: String? = null,
        isSample: Boolean = false
    ): Result<ApkScanResult> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist or is unreadable: ${file.absolutePath}"))
            }

            val fileSize = file.length()
            if (fileSize == 0L) {
                _scanStatus.value = ScanStatus.FAILED
                _statusMessage.value = "Invalid file: APK file is empty (0 bytes)"
                return@withContext Result.failure(IllegalArgumentException("Target APK file is empty (0 bytes)"))
            }
            if (fileSize > maxApkSizeBytes) {
                _scanStatus.value = ScanStatus.FAILED
                _statusMessage.value = "File exceeds maximum size limit (500 MB)"
                return@withContext Result.failure(IllegalArgumentException("File size exceeds maximum limit."))
            }

            // Validate ZIP magic header: 0x50 0x4B 0x03 0x04 (PK\x03\x04)
            val headerBuffer = ByteArray(4)
            val headerRead = file.inputStream().use { it.read(headerBuffer) }
            if (headerRead < 4 || headerBuffer[0] != 0x50.toByte() || headerBuffer[1] != 0x4B.toByte() ||
                headerBuffer[2] != 0x03.toByte() || headerBuffer[3] != 0x04.toByte()
            ) {
                _scanStatus.value = ScanStatus.FAILED
                _statusMessage.value = "Invalid file: Not a valid ZIP/APK archive"
                return@withContext Result.failure(IllegalArgumentException("File is not a valid APK/ZIP archive (magic header mismatch)."))
            }

            _scanStatus.value = ScanStatus.HASHING
            _statusMessage.value = "Calculating checksums..."

            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val md5Digest = MessageDigest.getInstance("MD5")

            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    sha256Digest.update(buffer, 0, bytesRead)
                    md5Digest.update(buffer, 0, bytesRead)
                }
            }

            val sha256 = CryptoUtils.bytesToHex(sha256Digest.digest())
            val md5 = CryptoUtils.bytesToHex(md5Digest.digest())

            val result = executeAnalysis(
                apkFile = file,
                scanId = scanId,
                fileName = fileNameHint ?: file.name,
                fileSize = fileSize,
                sha256 = sha256,
                md5 = md5,
                uriString = file.toURI().toString(),
                mimeType = "application/vnd.android.package-archive",
                isSample = isSample
            )

            // Persist to Room
            apkScanDao?.let { dao ->
                try {
                    val scanEntity = ApkScanEntity(
                        scanId = result.scanId,
                        fileName = result.fileInfo.fileName,
                        fileSize = result.fileInfo.fileSize,
                        sha256 = result.fileInfo.sha256,
                        md5 = result.fileInfo.md5,
                        timestamp = result.completedAt,
                        status = result.status.name,
                        manifestSummary = "${result.manifestInfo.packageName} v${result.manifestInfo.versionName} (${result.manifestInfo.versionCode})",
                        dexCount = result.fileInfo.dexCount,
                        abiSummary = if (result.abiCoverage.isEmpty()) "Pure Java / DEX" else result.abiCoverage.joinToString(),
                        certificateSummary = result.certificatesList.firstOrNull()?.let { "${it.subject} [${it.status}]" } ?: "No Signature",
                        securityFindingCount = result.securityFindings.size,
                        errorMessage = result.errorMessage,
                        permissionCount = result.permissionsList.size,
                        certificateStatus = result.certificatesList.firstOrNull()?.status?.name ?: if (result.fileInfo.signingRelatedFiles.isNotEmpty()) CertificateStatus.SIGNATURE_FILES_PRESENT.name else CertificateStatus.SIGNATURE_UNAVAILABLE.name,
                        packageName = result.manifestInfo.packageName,
                        uriString = result.fileInfo.uri,
                        rawJson = com.example.apk.export.ScanReportExporter.generateJsonReport(result)
                    )
                    dao.insertScan(scanEntity)
                } catch (_: Exception) {
                    // Fail gracefully on persistence error
                }
            }

            _currentResult.value = result
            _scanStatus.value = ScanStatus.COMPLETED
            _statusMessage.value = "Analysis completed"
            Result.success(result)
        } catch (e: Exception) {
            _scanStatus.value = ScanStatus.FAILED
            _statusMessage.value = "Analysis failed: ${e.message}"
            apkScanDao?.let { dao ->
                try {
                    dao.insertScan(
                        ApkScanEntity(
                            scanId = scanId,
                            fileName = file.name,
                            fileSize = file.length().coerceAtLeast(0L),
                            sha256 = "",
                            md5 = "",
                            timestamp = System.currentTimeMillis(),
                            status = ScanStatus.FAILED.name,
                            manifestSummary = "Analysis failed",
                            dexCount = 0,
                            abiSummary = "Unknown",
                            certificateSummary = "Unknown",
                            securityFindingCount = 0,
                            errorMessage = e.localizedMessage ?: e.message ?: "Analysis failed",
                            permissionCount = 0,
                            certificateStatus = CertificateStatus.SIGNATURE_UNAVAILABLE.name,
                            packageName = "",
                            uriString = file.toURI().toString(),
                            rawJson = null
                        )
                    )
                } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }

    private suspend fun executeAnalysis(
        apkFile: File,
        scanId: String,
        fileName: String,
        fileSize: Long,
        sha256: String,
        md5: String,
        uriString: String,
        mimeType: String,
        isSample: Boolean = false
    ): ApkScanResult {
        currentCoroutineContext().ensureActive()

        val deepScanResult = try {
            com.example.apk.scanner.deep.RealApkDeepScanner.performDeepScan(
                apkFile = apkFile,
                fileNameHint = fileName,
                isSample = isSample
            ).getOrNull()
        } catch (_: Exception) {
            null
        }

        // 1. Inspect archive entries
        _scanStatus.value = ScanStatus.INSPECTING_ARCHIVE
        _statusMessage.value = "Inspecting ZIP archive entries..."

        var totalEntries = 0
        var compressedSize = 0L
        var uncompressedSize = 0L
        var assetCount = 0
        var hasResources = false

        val dexEntries = mutableListOf<String>()
        val nativeLibEntries = mutableListOf<Pair<String, ZipEntry>>()
        val signingFiles = mutableListOf<String>()
        var manifestEntry: ZipEntry? = null
        var arscEntry: ZipEntry? = null
        val signatureBlockBytes = mutableListOf<ByteArray>()

        val zipFile = ZipFile(apkFile)
        try {
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.nextElement()
                totalEntries++

                // Path traversal check
                val name = entry.name
                if (name.contains("..") || name.startsWith("/")) {
                    continue // Skip malicious entry
                }

                if (entry.compressedSize > 0) compressedSize += entry.compressedSize
                if (entry.size > 0) uncompressedSize += entry.size

                when {
                    name == "AndroidManifest.xml" -> manifestEntry = entry
                    name.startsWith("classes") && name.endsWith(".dex") -> dexEntries.add(name)
                    name == "resources.arsc" -> {
                        hasResources = true
                        arscEntry = entry
                    }
                    name.startsWith("assets/") -> assetCount++
                    name.startsWith("lib/") && name.endsWith(".so") -> nativeLibEntries.add(name to entry)
                    name.startsWith("META-INF/") -> {
                        signingFiles.add(name)
                        if (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC")) {
                            zipFile.getInputStream(entry).use { input ->
                                signatureBlockBytes.add(input.readBytes())
                            }
                        }
                    }
                }
            }

            val compressionRatio = if (uncompressedSize > 0) {
                (1.0 - (compressedSize.toDouble() / uncompressedSize.toDouble())).coerceIn(0.0, 1.0) * 100.0
            } else 0.0

            // 2. DEX inspection
            _scanStatus.value = ScanStatus.ANALYZING_DEX
            _statusMessage.value = "Analyzing DEX bytecode headers..."
            currentCoroutineContext().ensureActive()

            // Natural sort classes.dex, classes2.dex, classes3.dex...
            val sortedDexNames = dexEntries.sortedWith(Comparator { a, b ->
                fun extractNum(s: String): Int {
                    val numStr = s.removePrefix("classes").removeSuffix(".dex")
                    return if (numStr.isEmpty()) 1 else numStr.toIntOrNull() ?: 999
                }
                extractNum(a).compareTo(extractNum(b))
            })

            val dexInfoList = mutableListOf<DexInfo>()
            for (dexName in sortedDexNames) {
                currentCoroutineContext().ensureActive()
                val entry = zipFile.getEntry(dexName) ?: continue
                val bytes = zipFile.getInputStream(entry).use { it.readBytes() }
                dexInfoList.add(DexParser.parseDex(dexName, bytes))
            }

            // 3. Manifest inspection
            _scanStatus.value = ScanStatus.ANALYZING_MANIFEST
            _statusMessage.value = "Decoding binary AndroidManifest.xml..."
            currentCoroutineContext().ensureActive()

            val manifestInfo: ManifestInfo
            if (manifestEntry != null) {
                val manifestBytes = zipFile.getInputStream(manifestEntry).use { it.readBytes() }
                val parsed = AxmlParser().parse(manifestBytes)
                manifestInfo = ManifestInfo(
                    packageName = parsed.packageName,
                    appName = parsed.appName,
                    versionCode = parsed.versionCode,
                    versionName = parsed.versionName,
                    minSdk = parsed.minSdk,
                    targetSdk = parsed.targetSdk,
                    compileSdk = parsed.compileSdk,
                    permissions = parsed.permissions,
                    usesFeatures = parsed.usesFeatures,
                    activities = parsed.activities.map {
                        ComponentInfo(
                            name = it.name,
                            type = "Activity",
                            exported = it.exported,
                            permission = it.permission,
                            intentActions = it.intentActions
                        )
                    },
                    services = parsed.services.map {
                        ComponentInfo(
                            name = it.name,
                            type = "Service",
                            exported = it.exported,
                            permission = it.permission,
                            intentActions = it.intentActions
                        )
                    },
                    receivers = parsed.receivers.map {
                        ComponentInfo(
                            name = it.name,
                            type = "Receiver",
                            exported = it.exported,
                            permission = it.permission,
                            intentActions = it.intentActions
                        )
                    },
                    providers = parsed.providers.map {
                        ComponentInfo(
                            name = it.name,
                            type = "Provider",
                            exported = it.exported,
                            permission = it.permission,
                            intentActions = it.intentActions
                        )
                    },
                    intentFiltersCount = parsed.intentFiltersCount,
                    exportedComponentsCount = parsed.exportedComponentsCount,
                    isDebuggable = parsed.isDebuggable,
                    allowBackup = parsed.allowsBackup,
                    usesCleartextTraffic = parsed.usesCleartextTraffic,
                    networkSecurityConfig = parsed.networkSecurityConfig,
                    theme = parsed.theme,
                    supportedArchitectures = emptyList(),
                    rawXmlText = parsed.rawXmlText
                )
            } else {
                manifestInfo = ManifestInfo(
                    packageName = "Unavailable",
                    appName = "Unavailable",
                    versionCode = 0L,
                    versionName = "Unavailable",
                    minSdk = 0,
                    targetSdk = 0,
                    compileSdk = null,
                    permissions = emptyList(),
                    usesFeatures = emptyList(),
                    activities = emptyList(),
                    services = emptyList(),
                    receivers = emptyList(),
                    providers = emptyList(),
                    intentFiltersCount = 0,
                    exportedComponentsCount = 0,
                    isDebuggable = false,
                    allowBackup = false,
                    usesCleartextTraffic = false,
                    networkSecurityConfig = null,
                    theme = null,
                    supportedArchitectures = emptyList(),
                    rawXmlText = "<!-- AndroidManifest.xml was not found in archive -->"
                )
            }

            // 4. Permission analysis
            _scanStatus.value = ScanStatus.ANALYZING_PERMISSIONS
            _statusMessage.value = "Evaluating declared permissions..."
            currentCoroutineContext().ensureActive()

            val permissionsList = manifestInfo.permissions.map { perm ->
                PermissionAnalyzer.analyzePermission(perm)
            }

            // 5. Native library / ABI analysis
            _scanStatus.value = ScanStatus.ANALYZING_NATIVE_LIBRARIES
            _statusMessage.value = "Inspecting native libraries and ABIs..."
            currentCoroutineContext().ensureActive()

            val nativeLibsList = mutableListOf<NativeLibraryInfo>()
            val detectedAbis = mutableSetOf<String>()

            for ((name, entry) in nativeLibEntries) {
                currentCoroutineContext().ensureActive()
                val parts = name.split('/')
                val abi = if (parts.size >= 3) parts[1] else "unknown"
                val libName = parts.last()
                detectedAbis.add(abi)

                val libBytes = zipFile.getInputStream(entry).use { it.readBytes() }
                val libSha256 = CryptoUtils.calculateSha256(libBytes)

                nativeLibsList.add(
                    NativeLibraryInfo(
                        abi = abi,
                        libraryName = libName,
                        fileSize = entry.size,
                        sha256 = libSha256
                    )
                )
            }

            val abiCoverage = detectedAbis.sorted()

            // 6. Certificate inspection
            _scanStatus.value = ScanStatus.ANALYZING_CERTIFICATE
            _statusMessage.value = "Inspecting cryptographic signatures..."
            currentCoroutineContext().ensureActive()

            val certificatesList = mutableListOf<ScanCertificateInfo>()
            val hasSigFiles = signingFiles.isNotEmpty()

            for (sigBlock in signatureBlockBytes) {
                certificatesList.addAll(CertInspector.inspectCertificates(sigBlock, hasSigFiles))
            }

            if (certificatesList.isEmpty()) {
                certificatesList.add(
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
                        status = if (hasSigFiles) CertificateStatus.SIGNATURE_FILES_PRESENT else CertificateStatus.SIGNATURE_UNAVAILABLE,
                        verificationDetails = if (hasSigFiles) "Signature files found in META-INF, but no X.509 certificate block was parsed." else "No signing files detected in META-INF.",
                        isSelfSigned = false,
                        keySizeBits = 0
                    )
                )
            }

            // 7. Security analysis
            _scanStatus.value = ScanStatus.ANALYZING_SECURITY
            _statusMessage.value = "Running deterministic security rules..."
            currentCoroutineContext().ensureActive()

            val securityFindings = ApkSecurityScanner.scan(
                manifest = manifestInfo,
                dexList = dexInfoList,
                permissions = permissionsList,
                nativeLibs = nativeLibsList,
                certificates = certificatesList,
                abiCoverage = abiCoverage
            )

            val fileInfo = ApkFileInfo(
                scanId = scanId,
                fileName = fileName,
                fileSize = fileSize,
                sha256 = sha256,
                md5 = md5,
                mimeType = mimeType,
                uri = uriString,
                createdAt = System.currentTimeMillis(),
                status = ScanStatus.COMPLETED,
                errorMessage = null,
                totalZipEntries = totalEntries,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                compressionRatio = compressionRatio,
                dexCount = dexInfoList.size,
                nativeLibraryCount = nativeLibsList.size,
                assetCount = assetCount,
                resourcePresence = hasResources,
                signingRelatedFiles = signingFiles,
                archiveEntries = deepScanResult?.archiveEntries?.map { it.toArchiveEntryDetail() } ?: emptyList()
            )

            val resourceStrings = if (arscEntry != null) {
                try {
                    val arscBytes = zipFile.getInputStream(arscEntry).use { it.readBytes() }
                    val parsed = ArscParser.parse(arscBytes)
                    parsed.strings.map { it.value }.filter { it.isNotBlank() }
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val combinedSecurityFindings = (securityFindings + (deepScanResult?.riskFindings?.map { it.toSecurityFinding() } ?: emptyList()))
                .distinctBy { it.id to it.title }

            return ApkScanResult(
                scanId = scanId,
                fileInfo = fileInfo,
                manifestInfo = manifestInfo,
                dexList = dexInfoList,
                permissionsList = permissionsList,
                nativeLibrariesList = nativeLibsList,
                abiCoverage = abiCoverage,
                certificatesList = certificatesList,
                securityFindings = combinedSecurityFindings,
                status = ScanStatus.COMPLETED,
                errorMessage = null,
                completedAt = System.currentTimeMillis(),
                resourceStrings = resourceStrings,
                isSample = isSample,
                scanDurationMs = deepScanResult?.scanDurationMs ?: 0L,
                scannerVersion = deepScanResult?.scannerVersion ?: "2.5.0-static-deep",
                signatureInfo = deepScanResult?.signatureInfo,
                scanSummary = deepScanResult?.summary,
                deepRiskFindings = deepScanResult?.riskFindings ?: emptyList(),
                deepScanResult = deepScanResult
            )
        } finally {
            try {
                zipFile.close()
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val STALE_FILE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours

        /**
         * Auto-purges temporary sandbox files older than 24 hours to prevent cache exhaustion.
         */
        fun purgeStaleSandboxFiles(sandboxDir: File, maxAgeMs: Long = STALE_FILE_EXPIRY_MS): Int {
            if (!sandboxDir.exists() || !sandboxDir.isDirectory) return 0
            val now = System.currentTimeMillis()
            var deletedCount = 0
            try {
                sandboxDir.listFiles()?.forEach { file ->
                    if (file.isFile && (now - file.lastModified() > maxAgeMs)) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
            } catch (_: Exception) {}
            return deletedCount
        }
    }
}
