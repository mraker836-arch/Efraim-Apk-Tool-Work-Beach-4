package com.example.apk.export

import android.content.Context
import android.net.Uri
import com.example.apk.model.ApkFileInfo
import com.example.apk.model.ApkScanResult
import com.example.apk.model.CertificateStatus
import com.example.apk.model.DexInfo
import com.example.apk.model.ManifestInfo
import com.example.apk.model.NativeLibraryInfo
import com.example.apk.model.ProtectionCategory
import com.example.apk.model.RiskLevel
import com.example.apk.model.ScanCertificateInfo
import com.example.apk.model.ScanPermissionInfo
import com.example.apk.model.ScanStatus
import com.example.database.ApkScanEntity
import com.example.security.model.FindingCategory
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScanReportExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)

    /**
     * Generates a human-readable plain text report according to File 5 specifications.
     */
    fun generatePlainTextReport(scan: ApkScanResult): String {
        val sb = StringBuilder()
        val dateStr = dateFormat.format(Date(scan.completedAt))

        sb.appendLine("==================================================")
        sb.appendLine("Efraim APK Workbench - Scan Report")
        sb.appendLine("==================================================")
        sb.appendLine("Scan ID:           ${scan.scanId}")
        sb.appendLine("APK Filename:      ${scan.fileInfo.fileName}")
        sb.appendLine("File Size:         ${scan.fileInfo.fileSize} bytes (${formatBytes(scan.fileInfo.fileSize)})")
        sb.appendLine("Scan Timestamp:    $dateStr")
        sb.appendLine("Scan Status:       ${scan.status.name}")
        scan.errorMessage?.let {
            sb.appendLine("Errors/Limitations: $it")
        }
        sb.appendLine()

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("1. ARCHIVE SUMMARY & STRUCTURAL INTEGRITY")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("Total Entries:       ${scan.fileInfo.totalZipEntries}")
        sb.appendLine("Compressed Size:     ${scan.fileInfo.compressedSize} bytes (${formatBytes(scan.fileInfo.compressedSize)})")
        sb.appendLine("Uncompressed Size:   ${scan.fileInfo.uncompressedSize} bytes (${formatBytes(scan.fileInfo.uncompressedSize)})")
        sb.appendLine("Compression Ratio:   ${String.format(Locale.US, "%.1f", scan.fileInfo.compressionRatio)}%")
        sb.appendLine("AndroidManifest.xml: Present")
        sb.appendLine("resources.arsc:      ${if (scan.fileInfo.resourcePresence) "Present" else "Absent"}")
        sb.appendLine("DEX Files:           ${scan.fileInfo.dexCount} classes artifact(s)")
        sb.appendLine("META-INF Signatures: ${if (scan.fileInfo.signingRelatedFiles.isNotEmpty()) "${scan.fileInfo.signingRelatedFiles.size} signing file(s)" else "Absent"}")
        sb.appendLine("Native Libraries:    ${if (scan.fileInfo.nativeLibraryCount > 0) "${scan.fileInfo.nativeLibraryCount} library binary(s)" else "None (Pure Java/Kotlin)"}")
        sb.appendLine("Assets Directory:    ${if (scan.fileInfo.assetCount > 0) "${scan.fileInfo.assetCount} asset file(s)" else "Empty / None"}")
        sb.appendLine()

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("2. CRYPTOGRAPHIC HASHES")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("SHA-256: ${scan.fileInfo.sha256}")
        sb.appendLine("MD5:     ${scan.fileInfo.md5}")
        sb.appendLine()

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("3. MANIFEST & PACKAGE IDENTITY")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("Package Name:        ${scan.manifestInfo.packageName.ifBlank { "Unavailable" }}")
        sb.appendLine("Application Label:   ${scan.manifestInfo.appName.ifBlank { "Unavailable" }}")
        sb.appendLine("Version Name:        ${scan.manifestInfo.versionName.ifBlank { "Unavailable" }}")
        sb.appendLine("Version Code:        ${scan.manifestInfo.versionCode}")
        sb.appendLine("Min SDK:             ${if (scan.manifestInfo.minSdk > 0) scan.manifestInfo.minSdk.toString() else "Unavailable"}")
        sb.appendLine("Target SDK:          ${if (scan.manifestInfo.targetSdk > 0) scan.manifestInfo.targetSdk.toString() else "Unavailable"}")
        sb.appendLine("Compile SDK:         ${scan.manifestInfo.compileSdk?.toString() ?: "Unavailable"}")
        sb.appendLine("Debuggable:          ${scan.manifestInfo.isDebuggable}")
        sb.appendLine("Allow Backup:        ${scan.manifestInfo.allowBackup}")
        sb.appendLine("Cleartext Traffic:   ${scan.manifestInfo.usesCleartextTraffic}")
        sb.appendLine("Data Extraction:     ${scan.manifestInfo.dataExtractionRules ?: "Unavailable"}")
        sb.appendLine("Clear User Data:     ${scan.manifestInfo.allowClearUserData?.toString() ?: "Unavailable"}")
        sb.appendLine("Legacy Storage:      ${scan.manifestInfo.requestLegacyExternalStorage?.toString() ?: "Unavailable"}")
        sb.appendLine("Exported Components: ${scan.manifestInfo.exportedComponentsCount}")
        sb.appendLine()

        sb.appendLine("Declared Features (${scan.manifestInfo.usesFeatures.size}):")
        if (scan.manifestInfo.usesFeatures.isEmpty()) {
            sb.appendLine("  (None declared)")
        } else {
            scan.manifestInfo.usesFeatures.forEach { sb.appendLine("  • $it") }
        }
        sb.appendLine()

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("3. DECLARED PERMISSIONS (${scan.permissionsList.size})")
        sb.appendLine("--------------------------------------------------")
        if (scan.permissionsList.isEmpty()) {
            sb.appendLine("  (No permissions declared)")
        } else {
            scan.permissionsList.forEach { p ->
                sb.appendLine("  • [${p.riskIndicator.name}] ${p.name}")
                if (p.description.isNotBlank()) {
                    sb.appendLine("    Description: ${p.description}")
                }
                if (p.reason.isNotBlank()) {
                    sb.appendLine("    Assessment:  ${p.reason}")
                }
            }
        }
        sb.appendLine()

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("4. DEX BYTECODE ARTIFACTS (${scan.dexList.size})")
        sb.appendLine("--------------------------------------------------")
        if (scan.dexList.isEmpty()) {
            sb.appendLine("  (No DEX files found)")
        } else {
            scan.dexList.forEach { dex ->
                sb.appendLine("  • ${dex.fileName} (${formatBytes(dex.fileSize)})")
                sb.appendLine("    Magic / Version: ${dex.magic} / v${dex.version}")
                sb.appendLine("    Adler32 Checksum: ${dex.adler32Checksum}")
                sb.appendLine("    SHA-256:          ${dex.sha256}")
                sb.appendLine("    Defined Classes:  ${dex.classDefsCount}")
                sb.appendLine("    Method IDs Est.:  ~${dex.methodIdsEstimate}")
                sb.appendLine("    Status:           ${dex.semanticAnalysisStatus}")
            }
        }
        sb.appendLine()

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("5. NATIVE LIBRARIES & ABI COVERAGE (${scan.nativeLibrariesList.size})")
        sb.appendLine("--------------------------------------------------")
        sb.appendLine("Covered ABIs: ${if (scan.abiCoverage.isEmpty()) "Pure Java/DEX (No native binaries)" else scan.abiCoverage.joinToString(", ")}")
        if (scan.nativeLibrariesList.isNotEmpty()) {
            scan.nativeLibrariesList.forEach { lib ->
                sb.appendLine("  • [${lib.abi}] ${lib.libraryName} (${formatBytes(lib.fileSize)}) SHA-256: ${lib.sha256}")
            }
        }
        sb.appendLine()

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("6. X.509 CERTIFICATE & SIGNING INTEGRITY")
        sb.appendLine("--------------------------------------------------")
        if (scan.certificatesList.isEmpty()) {
            sb.appendLine("  Verification Status: ${if (scan.fileInfo.signingRelatedFiles.isNotEmpty()) "SIGNATURE_FILES_PRESENT" else "SIGNATURE_UNAVAILABLE"}")
            sb.appendLine("  Certificate Information: None parsed or signature absent.")
        } else {
            scan.certificatesList.forEachIndexed { i, cert ->
                sb.appendLine("  Certificate #${i + 1}:")
                sb.appendLine("    Status:             ${cert.status.name}")
                sb.appendLine("    Subject:            ${cert.subject}")
                sb.appendLine("    Issuer:             ${cert.issuer}")
                sb.appendLine("    Serial Number:      ${cert.serialNumber}")
                sb.appendLine("    Validity Period:    ${cert.validFrom} to ${cert.validUntil}")
                sb.appendLine("    Signature Alg:      ${cert.signatureAlgorithm}")
                sb.appendLine("    Public Key Alg:     ${cert.publicKeyAlgorithm} (${cert.keySizeBits} bits)")
                sb.appendLine("    SHA-256 Digest:     ${cert.sha256Fingerprint}")
                sb.appendLine("    SHA-1 Digest:       ${cert.sha1Fingerprint.ifBlank { "Unavailable" }}")
                sb.appendLine("    MD5 Digest:         ${cert.md5Fingerprint.ifBlank { "Unavailable" }}")
                if (cert.verificationDetails.isNotBlank()) {
                    sb.appendLine("    Details:            ${cert.verificationDetails}")
                }
            }
            sb.appendLine("  Notice: Advanced APK signature verification unavailable (Scheme v2/v3/v4)")
        }
        sb.appendLine()

        sb.appendLine("--------------------------------------------------")
        sb.appendLine("7. DETERMINISTIC SECURITY FINDINGS (${scan.securityFindings.size})")
        sb.appendLine("--------------------------------------------------")
        if (scan.securityFindings.isEmpty()) {
            sb.appendLine("  No security issues or critical vulnerabilities identified.")
        } else {
            val grouped = scan.securityFindings.groupBy { it.severity }
            listOf(
                SecuritySeverity.CRITICAL,
                SecuritySeverity.HIGH,
                SecuritySeverity.MEDIUM,
                SecuritySeverity.LOW,
                SecuritySeverity.INFO
            ).forEach { severity ->
                val findings = grouped[severity] ?: emptyList()
                if (findings.isNotEmpty()) {
                    sb.appendLine("  [${severity.name}] (${findings.size})")
                    findings.forEach { f ->
                        sb.appendLine("    • Title:          ${f.title}")
                        sb.appendLine("      Category:       ${f.category.name}")
                        sb.appendLine("      Description:    ${f.description}")
                        sb.appendLine("      Evidence:       ${f.evidence}")
                        sb.appendLine("      Recommendation: ${f.recommendation}")
                    }
                    sb.appendLine()
                }
            }
        }

        sb.appendLine("==================================================")
        sb.appendLine("END OF REPORT - Efraim APK Workbench")
        sb.appendLine("==================================================")
        return sb.toString()
    }

    /**
     * Generates a structured JSON string conforming strictly to File 5 specifications.
     */
    fun generateJsonReport(scan: ApkScanResult): String {
        val root = JSONObject()
        root.put("scanId", scan.scanId)
        root.put("status", scan.status.name)
        root.put("completedAt", scan.completedAt)
        scan.errorMessage?.let { root.put("errorMessage", it) }

        // Archive Summary
        val archiveObj = JSONObject().apply {
            put("totalEntries", scan.fileInfo.totalZipEntries)
            put("compressedSize", scan.fileInfo.compressedSize)
            put("uncompressedSize", scan.fileInfo.uncompressedSize)
            put("compressionRatio", scan.fileInfo.compressionRatio)
            put("hasManifest", true)
            put("hasResourcesArsc", scan.fileInfo.resourcePresence)
            put("dexCount", scan.fileInfo.dexCount)
            put("nativeLibraryCount", scan.fileInfo.nativeLibraryCount)
            put("assetCount", scan.fileInfo.assetCount)
            put("signingRelatedFiles", JSONArray(scan.fileInfo.signingRelatedFiles))
        }
        root.put("archiveSummary", archiveObj)

        // Signature Info
        val sigObj = JSONObject().apply {
            put("signingFiles", JSONArray(scan.fileInfo.signingRelatedFiles))
            put("v2v3SchemeVerification", "Advanced APK signature verification unavailable")
            put("hasV1SignatureBlock", scan.fileInfo.signingRelatedFiles.any { it.endsWith(".RSA") || it.endsWith(".DSA") || it.endsWith(".EC") })
        }
        root.put("signatureInfo", sigObj)

        // File object
        val fileObj = JSONObject().apply {
            put("name", scan.fileInfo.fileName)
            put("size", scan.fileInfo.fileSize)
            put("sha256", scan.fileInfo.sha256)
            put("md5", scan.fileInfo.md5)
            put("mimeType", scan.fileInfo.mimeType)
            put("uri", scan.fileInfo.uri)
            put("totalZipEntries", scan.fileInfo.totalZipEntries)
            put("dexCount", scan.fileInfo.dexCount)
            put("nativeLibraryCount", scan.fileInfo.nativeLibraryCount)
        }
        root.put("file", fileObj)

        // Manifest object
        val manifestObj = JSONObject().apply {
            put("packageName", scan.manifestInfo.packageName)
            put("appName", scan.manifestInfo.appName)
            put("versionName", scan.manifestInfo.versionName)
            put("versionCode", scan.manifestInfo.versionCode)
            put("minSdk", scan.manifestInfo.minSdk)
            put("targetSdk", scan.manifestInfo.targetSdk)
            scan.manifestInfo.compileSdk?.let { put("compileSdk", it) }
            put("isDebuggable", scan.manifestInfo.isDebuggable)
            put("allowBackup", scan.manifestInfo.allowBackup)
            put("usesCleartextTraffic", scan.manifestInfo.usesCleartextTraffic)
            scan.manifestInfo.dataExtractionRules?.let { put("dataExtractionRules", it) }
            scan.manifestInfo.allowClearUserData?.let { put("allowClearUserData", it) }
            scan.manifestInfo.requestLegacyExternalStorage?.let { put("requestLegacyExternalStorage", it) }
            put("exportedComponentsCount", scan.manifestInfo.exportedComponentsCount)
            put("features", JSONArray(scan.manifestInfo.usesFeatures))
            put("activitiesCount", scan.manifestInfo.activities.size)
            put("servicesCount", scan.manifestInfo.services.size)
            put("receiversCount", scan.manifestInfo.receivers.size)
            put("providersCount", scan.manifestInfo.providers.size)
        }
        root.put("manifest", manifestObj)

        // Permissions array
        val permsArray = JSONArray()
        scan.permissionsList.forEach { p ->
            val pObj = JSONObject().apply {
                put("name", p.name)
                put("simpleName", p.simpleName)
                put("protectionCategory", p.protectionCategory.name)
                put("riskIndicator", p.riskIndicator.name)
                put("reason", p.reason)
                put("description", p.description)
            }
            permsArray.put(pObj)
        }
        root.put("permissions", permsArray)

        // Dex files array
        val dexArray = JSONArray()
        scan.dexList.forEach { d ->
            val dObj = JSONObject().apply {
                put("fileName", d.fileName)
                put("fileSize", d.fileSize)
                put("sha256", d.sha256)
                put("magic", d.magic)
                put("version", d.version)
                put("adler32Checksum", d.adler32Checksum)
                put("sha1Signature", d.sha1Signature)
                put("classDefsCount", d.classDefsCount)
                put("methodIdsEstimate", d.methodIdsEstimate)
                put("semanticAnalysisStatus", d.semanticAnalysisStatus)
            }
            dexArray.put(dObj)
        }
        root.put("dexFiles", dexArray)

        // Native libraries array
        val nativeLibsArray = JSONArray()
        scan.nativeLibrariesList.forEach { lib ->
            val lObj = JSONObject().apply {
                put("abi", lib.abi)
                put("libraryName", lib.libraryName)
                put("fileSize", lib.fileSize)
                put("sha256", lib.sha256)
            }
            nativeLibsArray.put(lObj)
        }
        root.put("nativeLibraries", nativeLibsArray)
        root.put("abiCoverage", JSONArray(scan.abiCoverage))

        // Certificate info object or array
        val certsArray = JSONArray()
        scan.certificatesList.forEach { c ->
            val cObj = JSONObject().apply {
                put("subject", c.subject)
                put("issuer", c.issuer)
                put("serialNumber", c.serialNumber)
                put("validFrom", c.validFrom)
                put("validUntil", c.validUntil)
                put("signatureAlgorithm", c.signatureAlgorithm)
                put("publicKeyAlgorithm", c.publicKeyAlgorithm)
                put("keySizeBits", c.keySizeBits)
                put("sha256Fingerprint", c.sha256Fingerprint)
                put("sha1Fingerprint", c.sha1Fingerprint)
                put("status", c.status.name)
                put("verificationDetails", c.verificationDetails)
                put("isSelfSigned", c.isSelfSigned)
            }
            certsArray.put(cObj)
        }
        root.put("certificate", if (certsArray.length() > 0) certsArray.getJSONObject(0) else JSONObject())
        root.put("certificates", certsArray)

        // Security findings array
        val findingsArray = JSONArray()
        scan.securityFindings.forEach { f ->
            val fObj = JSONObject().apply {
                put("id", f.id)
                put("category", f.category.name)
                put("severity", f.severity.name)
                put("title", f.title)
                put("description", f.description)
                put("evidence", f.evidence)
                put("recommendation", f.recommendation)
                put("affectedFile", f.affectedFile)
            }
            findingsArray.put(fObj)
        }
        root.put("securityFindings", findingsArray)

        return root.toString(2)
    }

    /**
     * Safely writes text content to a destination Uri using Android ContentResolver.
     */
    suspend fun writeReportToUri(context: Context, uri: Uri, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext Result.failure(IllegalStateException("Unable to open output stream for URI: $uri"))
            outputStream.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reconstructs an ApkScanResult from its serialized JSON format.
     */
    fun parseJsonToScanResult(jsonStr: String): ApkScanResult? {
        return try {
            val root = JSONObject(jsonStr)
            val scanId = root.optString("scanId", "")
            val statusStr = root.optString("status", "COMPLETED")
            val status = try { ScanStatus.valueOf(statusStr) } catch (_: Exception) { ScanStatus.COMPLETED }
            val completedAt = root.optLong("completedAt", System.currentTimeMillis())
            val errorMessage = if (root.has("errorMessage")) root.optString("errorMessage") else null

            // File
            val fileObj = root.optJSONObject("file") ?: JSONObject()
            val fileInfo = ApkFileInfo(
                scanId = scanId,
                fileName = fileObj.optString("name", "app.apk"),
                fileSize = fileObj.optLong("size", 0L),
                sha256 = fileObj.optString("sha256", ""),
                md5 = fileObj.optString("md5", ""),
                mimeType = fileObj.optString("mimeType", "application/vnd.android.package-archive"),
                uri = fileObj.optString("uri", ""),
                totalZipEntries = fileObj.optInt("totalZipEntries", 0),
                dexCount = fileObj.optInt("dexCount", 0),
                nativeLibraryCount = fileObj.optInt("nativeLibraryCount", 0)
            )

            // Manifest
            val manObj = root.optJSONObject("manifest") ?: JSONObject()
            val featuresList = mutableListOf<String>()
            val featuresArr = manObj.optJSONArray("features")
            if (featuresArr != null) {
                for (i in 0 until featuresArr.length()) {
                    featuresList.add(featuresArr.getString(i))
                }
            }
            val manifestInfo = ManifestInfo(
                packageName = manObj.optString("packageName", ""),
                appName = manObj.optString("appName", ""),
                versionCode = manObj.optLong("versionCode", 0L),
                versionName = manObj.optString("versionName", ""),
                minSdk = manObj.optInt("minSdk", 0),
                targetSdk = manObj.optInt("targetSdk", 0),
                compileSdk = if (manObj.has("compileSdk")) manObj.optInt("compileSdk") else null,
                permissions = emptyList(),
                usesFeatures = featuresList,
                activities = emptyList(),
                services = emptyList(),
                receivers = emptyList(),
                providers = emptyList(),
                intentFiltersCount = 0,
                exportedComponentsCount = manObj.optInt("exportedComponentsCount", 0),
                isDebuggable = manObj.optBoolean("isDebuggable", false),
                allowBackup = manObj.optBoolean("allowBackup", true),
                usesCleartextTraffic = manObj.optBoolean("usesCleartextTraffic", false),
                networkSecurityConfig = null,
                theme = null,
                supportedArchitectures = emptyList(),
                rawXmlText = "",
                dataExtractionRules = if (manObj.has("dataExtractionRules")) manObj.optString("dataExtractionRules") else null,
                allowClearUserData = if (manObj.has("allowClearUserData")) manObj.optBoolean("allowClearUserData") else null,
                requestLegacyExternalStorage = if (manObj.has("requestLegacyExternalStorage")) manObj.optBoolean("requestLegacyExternalStorage") else null
            )

            // Permissions
            val permissionsList = mutableListOf<ScanPermissionInfo>()
            val permsArr = root.optJSONArray("permissions")
            if (permsArr != null) {
                for (i in 0 until permsArr.length()) {
                    val p = permsArr.getJSONObject(i)
                    val pName = p.optString("name", "")
                    val protCatStr = p.optString("protectionCategory", "UNKNOWN")
                    val protCat = try { ProtectionCategory.valueOf(protCatStr) } catch (_: Exception) { ProtectionCategory.UNKNOWN }
                    val riskStr = p.optString("riskIndicator", "INFO")
                    val risk = try { RiskLevel.valueOf(riskStr) } catch (_: Exception) { RiskLevel.INFO }
                    permissionsList.add(
                        ScanPermissionInfo(
                            name = pName,
                            simpleName = p.optString("simpleName", pName.substringAfterLast(".")),
                            protectionCategory = protCat,
                            riskIndicator = risk,
                            reason = p.optString("reason", ""),
                            description = p.optString("description", "")
                        )
                    )
                }
            }

            // DEX
            val dexList = mutableListOf<DexInfo>()
            val dexArr = root.optJSONArray("dexFiles")
            if (dexArr != null) {
                for (i in 0 until dexArr.length()) {
                    val d = dexArr.getJSONObject(i)
                    dexList.add(
                        DexInfo(
                            fileName = d.optString("fileName", "classes.dex"),
                            fileSize = d.optLong("fileSize", 0L),
                            sha256 = d.optString("sha256", ""),
                            magic = d.optString("magic", "dex"),
                            version = d.optString("version", "035"),
                            adler32Checksum = d.optString("adler32Checksum", ""),
                            sha1Signature = d.optString("sha1Signature", ""),
                            classDefsCount = d.optInt("classDefsCount", 0),
                            methodIdsEstimate = d.optInt("methodIdsEstimate", 0),
                            stringIdsCount = 0,
                            typeIdsCount = 0,
                            protoIdsCount = 0,
                            fieldIdsCount = 0,
                            semanticAnalysisStatus = d.optString("semanticAnalysisStatus", "Advanced DEX semantic parsing unavailable")
                        )
                    )
                }
            }

            // Native Libraries
            val nativeList = mutableListOf<NativeLibraryInfo>()
            val natArr = root.optJSONArray("nativeLibraries")
            if (natArr != null) {
                for (i in 0 until natArr.length()) {
                    val n = natArr.getJSONObject(i)
                    nativeList.add(
                        NativeLibraryInfo(
                            abi = n.optString("abi", "unknown"),
                            libraryName = n.optString("libraryName", ""),
                            fileSize = n.optLong("fileSize", 0L),
                            sha256 = n.optString("sha256", "")
                        )
                    )
                }
            }

            val abiCoverage = mutableListOf<String>()
            val abiArr = root.optJSONArray("abiCoverage")
            if (abiArr != null) {
                for (i in 0 until abiArr.length()) {
                    abiCoverage.add(abiArr.getString(i))
                }
            }

            // Certificates
            val certsList = mutableListOf<ScanCertificateInfo>()
            val certsArr = root.optJSONArray("certificates")
            if (certsArr != null) {
                for (i in 0 until certsArr.length()) {
                    val c = certsArr.getJSONObject(i)
                    val statusC = try { CertificateStatus.valueOf(c.optString("status", "CERTIFICATE_READ")) } catch (_: Exception) { CertificateStatus.CERTIFICATE_READ }
                    certsList.add(
                        ScanCertificateInfo(
                            subject = c.optString("subject", "Unknown"),
                            issuer = c.optString("issuer", "Unknown"),
                            serialNumber = c.optString("serialNumber", ""),
                            validFrom = c.optString("validFrom", ""),
                            validUntil = c.optString("validUntil", ""),
                            signatureAlgorithm = c.optString("signatureAlgorithm", "SHA256withRSA"),
                            publicKeyAlgorithm = c.optString("publicKeyAlgorithm", "RSA"),
                            keySizeBits = c.optInt("keySizeBits", 2048),
                            sha256Fingerprint = c.optString("sha256Fingerprint", ""),
                            sha1Fingerprint = c.optString("sha1Fingerprint", ""),
                            md5Fingerprint = "",
                            status = statusC,
                            verificationDetails = c.optString("verificationDetails", ""),
                            isSelfSigned = c.optBoolean("isSelfSigned", false)
                        )
                    )
                }
            }

            // Security Findings
            val findingsList = mutableListOf<SecurityFinding>()
            val findArr = root.optJSONArray("securityFindings")
            if (findArr != null) {
                for (i in 0 until findArr.length()) {
                    val f = findArr.getJSONObject(i)
                    val sev = try { SecuritySeverity.valueOf(f.optString("severity", "MEDIUM")) } catch (_: Exception) { SecuritySeverity.MEDIUM }
                    val cat = try { FindingCategory.valueOf(f.optString("category", "APK_PERMISSIONS")) } catch (_: Exception) { FindingCategory.APK_PERMISSIONS }
                    findingsList.add(
                        SecurityFinding(
                            id = f.optString("id", "FND-$i"),
                            category = cat,
                            severity = sev,
                            title = f.optString("title", "Finding"),
                            description = f.optString("description", ""),
                            evidence = f.optString("evidence", ""),
                            recommendation = f.optString("recommendation", ""),
                            affectedFile = f.optString("affectedFile", "")
                        )
                    )
                }
            }

            ApkScanResult(
                scanId = scanId,
                fileInfo = fileInfo,
                manifestInfo = manifestInfo,
                dexList = dexList,
                permissionsList = permissionsList,
                nativeLibrariesList = nativeList,
                abiCoverage = abiCoverage,
                certificatesList = certsList,
                securityFindings = findingsList,
                status = status,
                errorMessage = errorMessage,
                completedAt = completedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fallback reconstruction directly from ApkScanEntity columns if JSON is missing.
     */
    fun fromEntity(entity: ApkScanEntity): ApkScanResult {
        if (!entity.rawJson.isNullOrBlank()) {
            val parsed = parseJsonToScanResult(entity.rawJson)
            if (parsed != null) return parsed
        }

        val status = try { ScanStatus.valueOf(entity.status) } catch (_: Exception) { ScanStatus.COMPLETED }
        val certStatus = try { CertificateStatus.valueOf(entity.certificateStatus) } catch (_: Exception) { CertificateStatus.SIGNATURE_UNAVAILABLE }

        return ApkScanResult(
            scanId = entity.scanId,
            fileInfo = ApkFileInfo(
                scanId = entity.scanId,
                fileName = entity.fileName,
                fileSize = entity.fileSize,
                sha256 = entity.sha256,
                md5 = entity.md5,
                mimeType = "application/vnd.android.package-archive",
                uri = entity.uriString ?: "",
                status = status,
                dexCount = entity.dexCount
            ),
            manifestInfo = ManifestInfo(
                packageName = entity.packageName,
                appName = entity.fileName.removeSuffix(".apk"),
                versionCode = 1L,
                versionName = "1.0",
                minSdk = 21,
                targetSdk = 34,
                compileSdk = 34,
                permissions = emptyList(),
                usesFeatures = emptyList(),
                activities = emptyList(),
                services = emptyList(),
                receivers = emptyList(),
                providers = emptyList(),
                intentFiltersCount = 0,
                exportedComponentsCount = 0,
                isDebuggable = false,
                allowBackup = true,
                usesCleartextTraffic = false,
                networkSecurityConfig = null,
                theme = null,
                supportedArchitectures = emptyList(),
                rawXmlText = ""
            ),
            dexList = emptyList(),
            permissionsList = emptyList(),
            nativeLibrariesList = emptyList(),
            abiCoverage = if (entity.abiSummary.isNotBlank() && entity.abiSummary != "Pure Java / DEX") entity.abiSummary.split(", ") else emptyList(),
            certificatesList = listOf(
                ScanCertificateInfo(
                    subject = entity.certificateSummary,
                    issuer = entity.certificateSummary,
                    serialNumber = "Unavailable",
                    validFrom = "Unavailable",
                    validUntil = "Unavailable",
                    signatureAlgorithm = "SHA256withRSA",
                    publicKeyAlgorithm = "RSA",
                    sha256Fingerprint = "Unavailable",
                    sha1Fingerprint = "Unavailable",
                    md5Fingerprint = "Unavailable",
                    status = certStatus,
                    verificationDetails = "",
                    isSelfSigned = true
                )
            ),
            securityFindings = emptyList(),
            status = status,
            errorMessage = entity.errorMessage,
            completedAt = entity.timestamp
        )
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> String.format(Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
        }
    }
}
