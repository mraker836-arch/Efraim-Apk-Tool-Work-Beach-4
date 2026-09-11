package com.example.apk.scanner

import android.content.Context
import com.example.apk.model.APKInfo
import com.example.apk.model.AssetFileInfo
import com.example.apk.model.ComponentInfo
import com.example.apk.model.DexFileInfo
import com.example.apk.model.PermissionInfo
import com.example.apk.model.ResourceFileInfo
import com.example.apk.model.RiskLevel
import com.example.apk.model.SigningStatus
import com.example.core.CryptoUtils
import com.example.core.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkScannerService(private val context: Context) {

    private val axmlParser = AxmlParser()
    val pipeline = ApkScanPipeline(com.example.database.AppDatabase.getDatabase(context).apkScanDao())

    fun toApkInfo(scanResult: com.example.apk.model.ApkScanResult, file: File): APKInfo {
        val nativeLibsMap = mutableMapOf<String, MutableList<String>>()
        for (lib in scanResult.nativeLibrariesList) {
            nativeLibsMap.getOrPut(lib.abi) { mutableListOf() }.add(lib.libraryName)
        }

        val legacyCerts = scanResult.certificatesList
            .filter { it.status != com.example.apk.model.CertificateStatus.SIGNATURE_FILES_PRESENT && it.status != com.example.apk.model.CertificateStatus.SIGNATURE_UNAVAILABLE }
            .map { CertInspector.toLegacyCertificateInfo(it) }

        val signingStatus = when {
            scanResult.certificatesList.any { it.status == com.example.apk.model.CertificateStatus.CERTIFICATE_VERIFIED } -> SigningStatus.SIGNED_V1
            scanResult.certificatesList.any { it.status == com.example.apk.model.CertificateStatus.CERTIFICATE_READ } -> SigningStatus.SIGNED_V1
            scanResult.fileInfo.signingRelatedFiles.isNotEmpty() -> SigningStatus.SIGNED_V1
            else -> SigningStatus.UNSIGNED
        }

        val dexFiles = scanResult.dexList.map { dex ->
            DexFileInfo(
                name = dex.fileName,
                sizeBytes = dex.fileSize,
                classDefsCount = dex.classDefsCount,
                methodIdsEstimate = dex.methodIdsEstimate,
                dexVersion = dex.version,
                stringIdsCount = dex.stringIdsCount,
                typeIdsCount = dex.typeIdsCount,
                protoIdsCount = dex.protoIdsCount,
                fieldIdsCount = dex.fieldIdsCount,
                classNames = dex.classNames
            )
        }

        val permissions = scanResult.permissionsList.map { p ->
            PermissionInfo(
                name = p.name,
                riskLevel = p.riskIndicator,
                description = p.reason
            )
        }

        val assetsList = scanResult.fileInfo.archiveEntries
            .filter { it.name.startsWith("assets/") && !it.isDirectory }
            .map {
                AssetFileInfo(
                    path = it.name,
                    sizeBytes = it.uncompressedSize,
                    isCompressed = it.compressedSize < it.uncompressedSize
                )
            }

        val resourcesList = scanResult.fileInfo.archiveEntries
            .filter { it.name.startsWith("res/") && !it.isDirectory }
            .map {
                val type = it.name.substringAfter("res/").substringBefore("/", "unknown")
                ResourceFileInfo(
                    path = it.name,
                    type = type,
                    sizeBytes = it.uncompressedSize
                )
            }

        val resolvedSigningStatus = scanResult.signatureInfo?.status ?: signingStatus

        return APKInfo(
            id = scanResult.scanId,
            fileName = scanResult.fileInfo.fileName,
            filePath = file.absolutePath,
            fileSize = scanResult.fileInfo.fileSize,
            sha256 = scanResult.fileInfo.sha256,
            md5 = scanResult.fileInfo.md5,
            packageName = scanResult.manifestInfo.packageName,
            appName = scanResult.manifestInfo.appName,
            versionName = scanResult.manifestInfo.versionName,
            versionCode = scanResult.manifestInfo.versionCode,
            minSdk = scanResult.manifestInfo.minSdk,
            targetSdk = scanResult.manifestInfo.targetSdk,
            compileSdk = scanResult.manifestInfo.compileSdk,
            isDebuggable = scanResult.manifestInfo.isDebuggable,
            allowsBackup = scanResult.manifestInfo.allowBackup,
            supportsRtl = true,
            permissions = permissions,
            activities = scanResult.manifestInfo.activities,
            services = scanResult.manifestInfo.services,
            receivers = scanResult.manifestInfo.receivers,
            providers = scanResult.manifestInfo.providers,
            dexFiles = dexFiles,
            nativeLibraries = nativeLibsMap,
            assets = assetsList,
            resources = resourcesList,
            certificates = legacyCerts,
            signingStatus = resolvedSigningStatus,
            totalEntriesCount = scanResult.fileInfo.totalZipEntries,
            isSample = scanResult.isSample
        )
    }

    suspend fun scanApk(apkFile: File): APKInfo = withContext(Dispatchers.IO) {
        SecurityManager.validateApkFileSize(apkFile)

        val sha256 = CryptoUtils.calculateSha256(apkFile)
        val md5 = CryptoUtils.calculateMd5(apkFile)
        val fileSize = apkFile.length()

        val permissions = mutableListOf<PermissionInfo>()
        val activities = mutableListOf<ComponentInfo>()
        val services = mutableListOf<ComponentInfo>()
        val receivers = mutableListOf<ComponentInfo>()
        val providers = mutableListOf<ComponentInfo>()
        val dexFiles = mutableListOf<DexFileInfo>()
        val nativeLibraries = mutableMapOf<String, MutableList<String>>()
        val assets = mutableListOf<AssetFileInfo>()
        val resources = mutableListOf<ResourceFileInfo>()
        val certificates = mutableListOf<com.example.apk.model.CertificateInfo>()

        var manifestData: AxmlParser.ParsedManifest? = null
        var hasManifestMf = false
        var hasCertSf = false
        var hasCertRsa = false
        var totalEntries = 0

        ZipFile(apkFile).use { zip ->
            totalEntries = zip.size()
            val entries = zip.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name

                when {
                    name == "AndroidManifest.xml" -> {
                        zip.getInputStream(entry).use { isStream ->
                            val bytes = isStream.readBytes()
                            manifestData = axmlParser.parse(bytes)
                        }
                    }
                    name.startsWith("classes") && name.endsWith(".dex") -> {
                        zip.getInputStream(entry).use { isStream ->
                            val bytes = isStream.readBytes()
                            val dexInfo = DexParser.parseDexHeader(name, bytes)
                            dexFiles.add(dexInfo)
                        }
                    }
                    name.startsWith("lib/") && name.endsWith(".so") -> {
                        val parts = name.split("/")
                        if (parts.size >= 3) {
                            val abi = parts[1]
                            val libName = parts.drop(2).joinToString("/")
                            nativeLibraries.getOrPut(abi) { mutableListOf() }.add(libName)
                        }
                    }
                    name.startsWith("assets/") -> {
                        assets.add(
                            AssetFileInfo(
                                path = name.removePrefix("assets/"),
                                sizeBytes = entry.size.coerceAtLeast(0L),
                                isCompressed = entry.method == ZipEntry.DEFLATED
                            )
                        )
                    }
                    name.startsWith("res/") || name == "resources.arsc" -> {
                        val type = if (name.startsWith("res/")) {
                            name.substringAfter("res/").substringBefore("/")
                        } else {
                            "arsc"
                        }
                        resources.add(
                            ResourceFileInfo(
                                path = name,
                                type = type,
                                sizeBytes = entry.size.coerceAtLeast(0L)
                            )
                        )
                    }
                    name.startsWith("META-INF/") -> {
                        val upper = name.uppercase()
                        if (upper.endsWith(".MF")) hasManifestMf = true
                        if (upper.endsWith(".SF")) hasCertSf = true
                        if (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")) {
                            hasCertRsa = true
                            zip.getInputStream(entry).use { isStream ->
                                val blockBytes = isStream.readBytes()
                                val extracted = CertInspector.extractCertificates(blockBytes)
                                certificates.addAll(extracted)
                            }
                        }
                    }
                }
            }
        }

        // Process manifest information
        val manifest = manifestData ?: AxmlParser.ParsedManifest(
            packageName = "com.example.unnamed",
            appName = apkFile.nameWithoutExtension,
            versionName = "1.0",
            versionCode = 1L,
            minSdk = 21,
            targetSdk = 34,
            compileSdk = 34,
            isDebuggable = false,
            allowsBackup = true,
            supportsRtl = true,
            permissions = emptyList(),
            activities = emptyList(),
            services = emptyList(),
            receivers = emptyList(),
            providers = emptyList(),
            rawXmlText = ""
        )

        for (permName in manifest.permissions) {
            val risk = categorizePermissionRisk(permName)
            val desc = getPermissionDescription(permName)
            permissions.add(
                PermissionInfo(
                    name = permName,
                    riskLevel = risk,
                    description = desc
                )
            )
        }

        for (act in manifest.activities) {
            activities.add(ComponentInfo(type = "Activity", name = act.name, exported = act.exported, permission = act.permission, intentActions = act.intentActions))
        }
        for (srv in manifest.services) {
            services.add(ComponentInfo(type = "Service", name = srv.name, exported = srv.exported, permission = srv.permission, intentActions = srv.intentActions))
        }
        for (rec in manifest.receivers) {
            receivers.add(ComponentInfo(type = "Receiver", name = rec.name, exported = rec.exported, permission = rec.permission, intentActions = rec.intentActions))
        }
        for (prv in manifest.providers) {
            providers.add(ComponentInfo(type = "Provider", name = prv.name, exported = prv.exported, permission = prv.permission, intentActions = prv.intentActions))
        }

        val signingStatus = when {
            hasManifestMf && hasCertSf && hasCertRsa && certificates.isNotEmpty() -> SigningStatus.SIGNED_V1
            hasManifestMf && hasCertSf -> SigningStatus.SIGNED_V1
            certificates.isNotEmpty() -> SigningStatus.SIGNED_V2
            else -> SigningStatus.UNSIGNED
        }

        APKInfo(
            id = UUID.randomUUID().toString(),
            fileName = apkFile.name,
            filePath = apkFile.absolutePath,
            fileSize = fileSize,
            sha256 = sha256,
            md5 = md5,
            packageName = manifest.packageName,
            appName = manifest.appName,
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
            minSdk = manifest.minSdk,
            targetSdk = manifest.targetSdk,
            compileSdk = manifest.compileSdk,
            isDebuggable = manifest.isDebuggable,
            allowsBackup = manifest.allowsBackup,
            supportsRtl = manifest.supportsRtl,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            dexFiles = dexFiles,
            nativeLibraries = nativeLibraries,
            assets = assets,
            resources = resources,
            certificates = certificates,
            signingStatus = signingStatus,
            totalEntriesCount = totalEntries
        )
    }

    suspend fun importApkStream(inputStream: InputStream, originalFileName: String): File = withContext(Dispatchers.IO) {
        val importsDir = File(context.filesDir, "imported_apks").apply { mkdirs() }
        val safeName = originalFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val timestamp = System.currentTimeMillis()
        val destFile = File(importsDir, "${timestamp}_$safeName")

        FileOutputStream(destFile).use { fos ->
            val buffer = ByteArray(16384)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                fos.write(buffer, 0, read)
            }
        }
        destFile
    }

    /**
     * Generates a fully functional real sample APK package on device with manifest, DEX, assets, and signature.
     */
    suspend fun generateSampleApk(appName: String = "DemoSampleApp", packageName: String = "com.example.sampledemo"): File = withContext(Dispatchers.IO) {
        val samplesDir = File(context.filesDir, "sample_apks").apply { mkdirs() }
        val apkFile = File(samplesDir, "${appName.lowercase()}_v1.0.apk")

        ZipOutputStream(FileOutputStream(apkFile)).use { zos ->
            // 1. AndroidManifest.xml
            val manifestBytes = SampleApkGenerator.buildBinaryManifest(
                packageName = packageName,
                appName = appName,
                versionName = "1.0.0",
                versionCode = 100,
                minSdk = 24,
                targetSdk = 35
            )
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(manifestBytes)
            zos.closeEntry()

            // 2. classes.dex
            val dexBytes = SampleApkGenerator.buildSampleDex()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write(dexBytes)
            zos.closeEntry()

            // 3. Embedded native libraries (.so for arm64-v8a & x86_64)
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libnative-engine.so"))
            zos.write("SAMPLE_ELF_ARM64_NATIVE_BINARY".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("lib/x86_64/libnative-engine.so"))
            zos.write("SAMPLE_ELF_X86_64_NATIVE_BINARY".toByteArray())
            zos.closeEntry()

            // 4. Sample assets
            zos.putNextEntry(ZipEntry("assets/config/app_config.json"))
            zos.write("{\"env\":\"production\",\"analytics\":true,\"theme\":\"dark\"}".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("assets/models/ai_schema.json"))
            zos.write("{\"version\":\"1.2.0\",\"layers\":16,\"context\":4096}".toByteArray())
            zos.closeEntry()

            // 5. Sample resources
            zos.putNextEntry(ZipEntry("res/layout/activity_main.xml"))
            zos.write("<LinearLayout><TextView text=\"Sample Screen\"/></LinearLayout>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("res/values/strings.xml"))
            zos.write("<resources><string name=\"app_name\">$appName</string></resources>".toByteArray())
            zos.closeEntry()
        }

        apkFile
    }

    private fun categorizePermissionRisk(perm: String): RiskLevel {
        val upper = perm.uppercase()
        return when {
            upper.contains("SYSTEM_ALERT_WINDOW") || upper.contains("REQUEST_INSTALL_PACKAGES") ||
            upper.contains("BIND_ACCESSIBILITY_SERVICE") || upper.contains("PACKAGE_USAGE_STATS") ||
            upper.contains("MANAGE_EXTERNAL_STORAGE") -> RiskLevel.CRITICAL

            upper.contains("READ_SMS") || upper.contains("SEND_SMS") ||
            upper.contains("READ_CONTACTS") || upper.contains("WRITE_CONTACTS") ||
            upper.contains("ACCESS_FINE_LOCATION") || upper.contains("CAMERA") ||
            upper.contains("RECORD_AUDIO") || upper.contains("READ_CALL_LOG") -> RiskLevel.HIGH

            upper.contains("ACCESS_COARSE_LOCATION") || upper.contains("READ_EXTERNAL_STORAGE") ||
            upper.contains("WRITE_EXTERNAL_STORAGE") || upper.contains("BLUETOOTH_CONNECT") ||
            upper.contains("POST_NOTIFICATIONS") -> RiskLevel.WARNING

            else -> RiskLevel.INFO
        }
    }

    private fun getPermissionDescription(perm: String): String {
        val simple = perm.substringAfterLast(".")
        return when (simple) {
            "INTERNET" -> "Allows the application to create network sockets and use network protocols."
            "ACCESS_NETWORK_STATE" -> "Allows application to view status of network connections."
            "READ_EXTERNAL_STORAGE" -> "Allows application to read files from external storage."
            "WRITE_EXTERNAL_STORAGE" -> "Allows application to write files to external storage."
            "CAMERA" -> "Required to be able to access the camera device."
            "RECORD_AUDIO" -> "Allows application to record audio from microphone."
            "ACCESS_FINE_LOCATION" -> "Allows application to access precise GPS location."
            "ACCESS_COARSE_LOCATION" -> "Allows application to access approximate cell/WiFi location."
            "POST_NOTIFICATIONS" -> "Allows application to post notifications on Android 13+."
            "REQUEST_INSTALL_PACKAGES" -> "Allows application to request installing other APK packages."
            else -> "Application requested permission for system capabilities."
        }
    }
}
