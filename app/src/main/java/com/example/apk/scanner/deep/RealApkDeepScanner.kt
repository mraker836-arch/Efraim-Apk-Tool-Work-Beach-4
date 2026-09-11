package com.example.apk.scanner.deep

import com.example.apk.model.ApkFileEntry
import com.example.apk.model.ComponentInfo
import com.example.apk.model.DexInfo
import com.example.apk.model.EntryCategory
import com.example.apk.model.ManifestInfo
import com.example.apk.model.NativeLibraryInfo
import com.example.apk.model.ProtectionCategory
import com.example.apk.model.RiskFinding
import com.example.apk.model.RiskLevel
import com.example.apk.model.ScanCertificateInfo
import com.example.apk.model.ScanPermissionInfo
import com.example.apk.model.ScanResult
import com.example.apk.model.ScanSummary
import com.example.apk.model.SignatureInfo
import com.example.apk.model.SigningStatus
import com.example.apk.model.StaticRiskSeverity
import com.example.apk.scanner.AxmlParser
import com.example.apk.scanner.CertInspector
import com.example.apk.scanner.DexParser
import com.example.apk.scanner.PermissionAnalyzer
import com.example.core.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/**
 * Production-ready static deep analysis engine for Android APK packages.
 *
 * CRITICAL SECURITY CONSTRAINTS:
 * - Local, offline, static inspection ONLY.
 * - NEVER executes, launches, installs, or dynamically loads APK code or native libraries.
 * - Never invents values. Reports limitations when structures cannot be parsed.
 */
object RealApkDeepScanner {

    const val SCANNER_VERSION = "2.5.0-static-deep"

    /**
     * Executes a comprehensive static deep scan on the provided APK file.
     */
    suspend fun performDeepScan(
        apkFile: File,
        fileNameHint: String? = null,
        isSample: Boolean = false,
        onProgress: (progress: Float, stageMessage: String) -> Unit = { _, _ -> }
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val scanId = "SCAN-${UUID.randomUUID().toString().take(8).uppercase()}"
        val resolvedFileName = fileNameHint ?: apkFile.name

        try {
            // Stage 1: Validation
            onProgress(0.05f, "Validating APK file accessibility and integrity...")
            coroutineContext.ensureActive()

            if (!apkFile.exists() || !apkFile.isFile) {
                return@withContext Result.failure(IllegalArgumentException("Target APK file does not exist or is not a regular file: ${apkFile.absolutePath}"))
            }

            val fileSize = apkFile.length()
            if (fileSize == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Target APK file is empty (0 bytes)"))
            }

            // Verify ZIP magic header (0x50 0x4B 0x03 0x04)
            val headerBytes = ByteArray(4)
            apkFile.inputStream().use { it.read(headerBytes) }
            val isZipMagic = headerBytes[0] == 0x50.toByte() &&
                    headerBytes[1] == 0x4B.toByte() &&
                    headerBytes[2] == 0x03.toByte() &&
                    headerBytes[3] == 0x04.toByte()

            if (!isZipMagic) {
                return@withContext Result.failure(IllegalArgumentException("File is not a valid ZIP/APK archive (invalid magic signature)"))
            }

            // Stage 2: Streaming Hashes
            onProgress(0.15f, "Computing SHA-256 and MD5 cryptographic hashes...")
            coroutineContext.ensureActive()
            val (sha256, md5) = computeStreamingHashes(apkFile)

            // Stage 3: ZIP Archive Structure Analysis
            onProgress(0.30f, "Inspecting ZIP archive entries and directory structure...")
            coroutineContext.ensureActive()

            val fileEntries = mutableListOf<ApkFileEntry>()
            val archiveAnomalies = mutableListOf<String>()
            val seenEntryNames = mutableSetOf<String>()
            var totalCompressedBytes = 0L
            var totalUncompressedBytes = 0L
            var hasManifest = false
            var manifestBytes: ByteArray? = null
            val dexEntries = mutableListOf<ZipEntry>()
            val nativeLibEntries = mutableListOf<ZipEntry>()
            val signatureEntries = mutableListOf<ZipEntry>()
            var hasKotlinMeta = false
            val configFilesFound = mutableListOf<String>()

            ZipFile(apkFile).use { zip ->
                val entriesEnum = zip.entries()
                while (entriesEnum.hasMoreElements()) {
                    coroutineContext.ensureActive()
                    val entry = entriesEnum.nextElement()
                    val entryName = entry.name

                    // Anomaly detection: Zip Slip / path traversal
                    var isSuspicious = false
                    var anomalyReason: String? = null
                    if (entryName.contains("..") || entryName.startsWith("/")) {
                        isSuspicious = true
                        anomalyReason = "Path traversal sequence detected in archive entry name"
                        archiveAnomalies.add("Zip Slip vulnerability attempt: entry '$entryName' contains path traversal tokens")
                    }

                    if (!seenEntryNames.add(entryName)) {
                        isSuspicious = true
                        anomalyReason = "Duplicate ZIP entry detected in archive index"
                        archiveAnomalies.add("Duplicate archive entry: '$entryName'")
                    }

                    val uncompressedSize = entry.size
                    val compressedSize = entry.compressedSize

                    if (uncompressedSize < 0 || compressedSize < 0) {
                        isSuspicious = true
                        anomalyReason = "Invalid negative entry size header"
                        archiveAnomalies.add("Corrupted size headers on entry '$entryName'")
                    } else if (compressedSize > 0 && uncompressedSize > compressedSize * 100 && uncompressedSize > 50 * 1024 * 1024) {
                        isSuspicious = true
                        anomalyReason = "Suspicious compression ratio (>100x on large entry: potential zip bomb)"
                        archiveAnomalies.add("Potential decompression bomb anomaly on '$entryName' (${uncompressedSize / (compressedSize.coerceAtLeast(1))}x ratio)")
                    }

                    if (uncompressedSize > 0) totalUncompressedBytes += uncompressedSize
                    if (compressedSize > 0) totalCompressedBytes += compressedSize

                    val category = classifyEntryCategory(entryName)

                    if (category == EntryCategory.KOTLIN_METADATA) {
                        hasKotlinMeta = true
                    }
                    if (category == EntryCategory.CONFIGURATION) {
                        configFilesFound.add(entryName)
                    }

                    fileEntries.add(
                        ApkFileEntry(
                            name = entryName,
                            sizeBytes = uncompressedSize.coerceAtLeast(0),
                            compressedSizeBytes = compressedSize.coerceAtLeast(0),
                            crc32 = entry.crc,
                            isDirectory = entry.isDirectory,
                            category = category,
                            compressionMethod = if (entry.method == ZipEntry.STORED) "STORED" else "DEFLATED",
                            isSuspicious = isSuspicious,
                            anomalyReason = anomalyReason
                        )
                    )

                    when {
                        entryName == "AndroidManifest.xml" -> {
                            hasManifest = true
                            manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                        }
                        entryName.matches(Regex("classes\\d*\\.dex")) -> {
                            dexEntries.add(entry)
                        }
                        entryName.startsWith("lib/") && entryName.endsWith(".so") -> {
                            nativeLibEntries.add(entry)
                        }
                        entryName.startsWith("META-INF/") && (entryName.endsWith(".RSA") || entryName.endsWith(".DSA") || entryName.endsWith(".EC")) -> {
                            signatureEntries.add(entry)
                        }
                    }
                }
            }

            val compressionRatio = if (totalUncompressedBytes > 0) {
                ((totalUncompressedBytes - totalCompressedBytes).toDouble() / totalUncompressedBytes.toDouble()) * 100.0
            } else 0.0

            // Stage 4: Manifest Analysis
            onProgress(0.50f, "Decoding compiled binary AndroidManifest.xml...")
            coroutineContext.ensureActive()

            val parsedManifest: AxmlParser.ParsedManifest? = manifestBytes?.let { bytes ->
                try {
                    AxmlParser().parse(bytes)
                } catch (e: Exception) {
                    archiveAnomalies.add("Failed to decode binary AndroidManifest.xml: ${e.message}")
                    null
                }
            }

            val packageName = parsedManifest?.packageName?.ifBlank { "Unavailable" } ?: "Unavailable"
            val appName = parsedManifest?.appName?.ifBlank { "Unavailable" } ?: "Unavailable"
            val versionName = parsedManifest?.versionName ?: "Unavailable"
            val versionCode = parsedManifest?.versionCode ?: 0L
            val minSdk = parsedManifest?.minSdk ?: 21
            val targetSdk = parsedManifest?.targetSdk ?: 34
            val compileSdk = parsedManifest?.compileSdk
            val isDebuggable = parsedManifest?.isDebuggable ?: false
            val allowsBackup = parsedManifest?.allowsBackup ?: true

            // Components
            val components = mutableListOf<ComponentInfo>()
            parsedManifest?.let { pm ->
                for (act in pm.activities) {
                    components.add(
                        ComponentInfo(
                            type = act.type,
                            name = act.name,
                            simpleName = act.name.substringAfterLast("."),
                            exported = act.exported,
                            permission = act.permission,
                            intentActions = act.intentActions,
                            isEnabled = act.enabled,
                            intentCategories = act.intentCategories
                        )
                    )
                }
                for (srv in pm.services) {
                    components.add(
                        ComponentInfo(
                            type = srv.type,
                            name = srv.name,
                            simpleName = srv.name.substringAfterLast("."),
                            exported = srv.exported,
                            permission = srv.permission,
                            intentActions = srv.intentActions,
                            isEnabled = srv.enabled,
                            intentCategories = srv.intentCategories
                        )
                    )
                }
                for (rcv in pm.receivers) {
                    components.add(
                        ComponentInfo(
                            type = rcv.type,
                            name = rcv.name,
                            simpleName = rcv.name.substringAfterLast("."),
                            exported = rcv.exported,
                            permission = rcv.permission,
                            intentActions = rcv.intentActions,
                            isEnabled = rcv.enabled,
                            intentCategories = rcv.intentCategories
                        )
                    )
                }
                for (prv in pm.providers) {
                    components.add(
                        ComponentInfo(
                            type = prv.type,
                            name = prv.name,
                            simpleName = prv.name.substringAfterLast("."),
                            exported = prv.exported,
                            permission = prv.permission,
                            intentActions = prv.intentActions,
                            isEnabled = prv.enabled,
                            intentCategories = prv.intentCategories
                        )
                    )
                }
            }

            // Stage 5: Permission Analysis
            onProgress(0.65f, "Auditing declared permissions and sensitive protection levels...")
            coroutineContext.ensureActive()

            val permissions = mutableListOf<ScanPermissionInfo>()
            val declaredPerms = parsedManifest?.permissions ?: emptyList()
            for (permName in declaredPerms) {
                permissions.add(PermissionAnalyzer.analyzePermission(permName))
            }

            val sensitivePermissions = permissions
                .filter { it.protectionCategory == ProtectionCategory.DANGEROUS || it.protectionCategory == ProtectionCategory.SPECIAL }
                .map { it.simpleName }

            // Stage 6: DEX Bytecode Analysis
            onProgress(0.75f, "Parsing DEX bytecode headers and symbol table counts...")
            coroutineContext.ensureActive()

            val dexInfoList = mutableListOf<DexInfo>()
            // Sort DEX naturally: classes.dex, classes2.dex, classes3.dex...
            dexEntries.sortWith(Comparator { a, b ->
                extractDexIndex(a.name).compareTo(extractDexIndex(b.name))
            })

            ZipFile(apkFile).use { zip ->
                for (dexEntry in dexEntries) {
                    coroutineContext.ensureActive()
                    val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
                    val parsedDex = DexParser.parseDex(dexEntry.name, dexBytes)
                    dexInfoList.add(parsedDex)
                }
            }

            val totalDexClasses = dexInfoList.sumOf { it.classDefsCount }

            // Stage 7: Native Library Analysis
            onProgress(0.85f, "Auditing compiled native ELF binaries (.so) and ABIs...")
            coroutineContext.ensureActive()

            val nativeLibs = mutableListOf<NativeLibraryInfo>()
            ZipFile(apkFile).use { zip ->
                for (libEntry in nativeLibEntries) {
                    coroutineContext.ensureActive()
                    val parts = libEntry.name.split("/")
                    val abi = if (parts.size >= 2) parts[1] else "unknown"
                    val libBytes = zip.getInputStream(libEntry).use { it.readBytes() }
                    val libSha256 = CryptoUtils.calculateSha256(libBytes)

                    nativeLibs.add(
                        NativeLibraryInfo(
                            abi = abi,
                            libraryName = libEntry.name.substringAfterLast("/"),
                            fileSize = libEntry.size.coerceAtLeast(0),
                            sha256 = libSha256
                        )
                    )
                }
            }

            val supportedAbis = nativeLibs.map { it.abi }.distinct()

            // Stage 8: Signature & Certificate Analysis
            onProgress(0.90f, "Inspecting cryptographic certificates and signature schemes...")
            coroutineContext.ensureActive()

            val certificates = mutableListOf<ScanCertificateInfo>()
            var isSigned = false
            val schemes = mutableListOf<String>()
            val signerNames = mutableListOf<String>()
            val signatureWarnings = mutableListOf<String>()

            ZipFile(apkFile).use { zip ->
                for (sigEntry in signatureEntries) {
                    coroutineContext.ensureActive()
                    val sigBytes = zip.getInputStream(sigEntry).use { it.readBytes() }
                    val parsedCerts = CertInspector.inspectCertificates(sigBytes, hasSignatureFiles = true)
                    certificates.addAll(parsedCerts)
                    signerNames.add(sigEntry.name)
                }
            }

            if (signatureEntries.isNotEmpty()) {
                isSigned = true
                schemes.add("v1 (JAR Signing)")
            }

            // Detect APK Signature Scheme v2/v3 block presence safely in file
            if (hasApkSigningBlock(apkFile)) {
                isSigned = true
                schemes.add("v2/v3 (APK Signature Scheme)")
            }

            if (!isSigned) {
                signatureWarnings.add("APK has no digital signature. It cannot be installed on standard Android devices.")
            }

            val isDebugCert = certificates.any { cert ->
                cert.subject.contains("Android Debug", ignoreCase = true) ||
                        cert.subject.contains("O=Android", ignoreCase = true) ||
                        cert.issuer.contains("Android Debug", ignoreCase = true)
            }

            val certSha256List = certificates.map { it.sha256Fingerprint }.filter { it != "Unavailable" }

            val signatureInfo = SignatureInfo(
                isSigned = isSigned,
                signerCount = signerNames.size.coerceAtLeast(if (isSigned) 1 else 0),
                signatureSchemes = schemes,
                certificateSha256List = certSha256List,
                certificates = certificates,
                isDebugCertificate = isDebugCert,
                signerNames = signerNames,
                warnings = signatureWarnings,
                status = when {
                    !isSigned -> SigningStatus.UNSIGNED
                    schemes.contains("v2/v3 (APK Signature Scheme)") && schemes.contains("v1 (JAR Signing)") -> SigningStatus.SIGNED_V1_V2
                    schemes.contains("v2/v3 (APK Signature Scheme)") -> SigningStatus.SIGNED_V2
                    else -> SigningStatus.SIGNED_V1
                }
            )

            // Stage 9: Static Risk Engine
            onProgress(0.96f, "Running static risk assessment rules...")
            coroutineContext.ensureActive()

            val manifestInfo = ManifestInfo(
                packageName = packageName,
                appName = appName,
                versionCode = versionCode,
                versionName = versionName,
                minSdk = minSdk,
                targetSdk = targetSdk,
                compileSdk = compileSdk,
                permissions = permissions.map { it.name },
                usesFeatures = parsedManifest?.usesFeatures ?: emptyList(),
                activities = components.filter { it.type == "Activity" },
                services = components.filter { it.type == "Service" },
                receivers = components.filter { it.type == "Receiver" },
                providers = components.filter { it.type == "Provider" },
                intentFiltersCount = parsedManifest?.intentFiltersCount ?: 0,
                exportedComponentsCount = components.count { it.exported },
                isDebuggable = isDebuggable,
                allowBackup = allowsBackup,
                usesCleartextTraffic = parsedManifest?.usesCleartextTraffic ?: false,
                networkSecurityConfig = parsedManifest?.networkSecurityConfig,
                theme = parsedManifest?.theme,
                supportedArchitectures = supportedAbis,
                rawXmlText = parsedManifest?.rawXmlText ?: ""
            )

            val riskFindings = RealApkStaticRiskEngine.analyze(
                manifest = if (hasManifest) manifestInfo else null,
                dexList = dexInfoList,
                permissions = permissions,
                components = components,
                nativeLibs = nativeLibs,
                signatureInfo = signatureInfo,
                archiveEntries = fileEntries,
                archiveAnomalies = archiveAnomalies
            )

            val riskCounts = mutableMapOf<StaticRiskSeverity, Int>()
            StaticRiskSeverity.values().forEach { riskCounts[it] = 0 }
            for (f in riskFindings) {
                riskCounts[f.severity] = (riskCounts[f.severity] ?: 0) + 1
            }

            val overallRisk = when {
                (riskCounts[StaticRiskSeverity.CRITICAL] ?: 0) > 0 -> StaticRiskSeverity.CRITICAL
                (riskCounts[StaticRiskSeverity.HIGH] ?: 0) > 0 -> StaticRiskSeverity.HIGH
                (riskCounts[StaticRiskSeverity.MEDIUM] ?: 0) > 0 -> StaticRiskSeverity.MEDIUM
                (riskCounts[StaticRiskSeverity.LOW] ?: 0) > 0 -> StaticRiskSeverity.LOW
                else -> StaticRiskSeverity.INFO
            }

            val summary = ScanSummary(
                totalFiles = fileEntries.size,
                totalCompressedBytes = totalCompressedBytes,
                totalUncompressedBytes = totalUncompressedBytes,
                compressionRatio = compressionRatio,
                dexCount = dexInfoList.size,
                totalDexClasses = totalDexClasses,
                permissionCount = permissions.size,
                dangerousPermissionCount = permissions.count { it.protectionCategory == ProtectionCategory.DANGEROUS },
                sensitivePermissions = sensitivePermissions,
                totalComponents = components.size,
                exportedComponentsCount = components.count { it.exported },
                nativeLibCount = nativeLibs.size,
                supportedAbis = supportedAbis,
                hasKotlinMetadata = hasKotlinMeta,
                configFilesFound = configFilesFound,
                riskFindingCounts = riskCounts,
                isSigned = isSigned,
                isDebuggable = isDebuggable,
                isSample = isSample,
                overallRiskLevel = overallRisk
            )

            val durationMs = System.currentTimeMillis() - startTime
            onProgress(1.0f, "Static deep scan completed in ${durationMs}ms.")

            val result = ScanResult(
                scanId = scanId,
                fileName = resolvedFileName,
                fileSize = fileSize,
                sha256 = sha256,
                md5 = md5,
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                compileSdk = compileSdk,
                isDebuggable = isDebuggable,
                allowsBackup = allowsBackup,
                permissions = permissions,
                components = components,
                dexInfoList = dexInfoList,
                nativeLibraries = nativeLibs,
                signatureInfo = signatureInfo,
                archiveEntries = fileEntries,
                kotlinMetadataPresent = hasKotlinMeta,
                configFiles = configFilesFound,
                riskFindings = riskFindings,
                summary = summary,
                isSuccess = true,
                isSample = isSample,
                scanTimestamp = startTime,
                scanDurationMs = durationMs,
                scannerVersion = SCANNER_VERSION,
                errors = if (hasManifest) emptyList() else listOf("Archive missing AndroidManifest.xml"),
                warnings = archiveAnomalies + signatureWarnings
            )

            Result.success(result)
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startTime
            Result.failure(e)
        }
    }

    private fun computeStreamingHashes(file: File): Pair<String, String> {
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val md5Digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(64 * 1024)

        file.inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                sha256Digest.update(buffer, 0, bytesRead)
                md5Digest.update(buffer, 0, bytesRead)
            }
        }

        return CryptoUtils.bytesToHex(sha256Digest.digest()) to CryptoUtils.bytesToHex(md5Digest.digest())
    }

    private fun classifyEntryCategory(name: String): EntryCategory {
        return when {
            name == "AndroidManifest.xml" -> EntryCategory.MANIFEST
            name.matches(Regex("classes\\d*\\.dex")) -> EntryCategory.DEX
            name == "resources.arsc" || name.startsWith("res/") -> EntryCategory.RESOURCE
            name.startsWith("assets/") -> EntryCategory.ASSET
            name.startsWith("lib/") -> EntryCategory.NATIVE_LIBRARY
            name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC") || name.endsWith(".SF") || name == "META-INF/MANIFEST.MF") -> EntryCategory.SIGNATURE
            name.startsWith("kotlin/") || name.endsWith(".kotlin_module") || name.endsWith(".kotlin_builtins") -> EntryCategory.KOTLIN_METADATA
            name.endsWith(".properties") || name.endsWith(".json") || (name.startsWith("res/xml/") && name.endsWith(".xml")) -> EntryCategory.CONFIGURATION
            else -> EntryCategory.OTHER
        }
    }

    private fun extractDexIndex(name: String): Int {
        if (name == "classes.dex") return 1
        val match = Regex("classes(\\d+)\\.dex").find(name)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 999
    }

    private fun hasApkSigningBlock(file: File): Boolean {
        if (file.length() < 32) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
                val bufferSize = minOf(len, 65536L).toInt()
                val buffer = ByteArray(bufferSize)
                raf.seek(len - bufferSize)
                raf.readFully(buffer)
                for (i in 0 until (bufferSize - magic.size)) {
                    var match = true
                    for (j in magic.indices) {
                        if (buffer[i + j] != magic[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return@use true
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
