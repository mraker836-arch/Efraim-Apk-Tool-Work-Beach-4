package com.example.apk.model

import com.example.security.model.FindingCategory
import com.example.security.model.SecurityFinding
import com.example.security.model.SecuritySeverity

/**
 * Structural category for APK file entries within the ZIP archive.
 */
enum class EntryCategory {
    MANIFEST,
    DEX,
    RESOURCE,
    ASSET,
    NATIVE_LIBRARY,
    SIGNATURE,
    KOTLIN_METADATA,
    CONFIGURATION,
    OTHER
}

/**
 * Detailed information about a single file entry in the APK archive.
 */
data class ApkFileEntry(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val crc32: Long,
    val isDirectory: Boolean,
    val category: EntryCategory,
    val compressionMethod: String = "DEFLATED",
    val isSuspicious: Boolean = false,
    val anomalyReason: String? = null
) {
    fun toArchiveEntryDetail(): ArchiveEntryDetail {
        return ArchiveEntryDetail(
            name = name,
            compressedSize = compressedSizeBytes,
            uncompressedSize = sizeBytes,
            compressionMethod = compressionMethod,
            isDirectory = isDirectory,
            crc = crc32
        )
    }
}

/**
 * Cryptographic and signature scheme information extracted from the APK.
 */
data class SignatureInfo(
    val isSigned: Boolean,
    val signerCount: Int,
    val signatureSchemes: List<String>,
    val certificateSha256List: List<String>,
    val certificates: List<ScanCertificateInfo>,
    val isDebugCertificate: Boolean,
    val signerNames: List<String>,
    val warnings: List<String> = emptyList(),
    val status: SigningStatus = if (isSigned) SigningStatus.SIGNED_V1 else SigningStatus.UNSIGNED
)

/**
 * Observable risk categories for static analysis.
 */
enum class StaticRiskCategory {
    SECURITY,
    PRIVACY,
    INTEGRITY,
    COMPONENT_EXPOSURE,
    NETWORK_SECURITY,
    CRYPTOGRAPHY,
    SYSTEM_RESTRICTION,
    ARCHIVE_ANOMALY
}

/**
 * Severity grading for static risk findings.
 */
enum class StaticRiskSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * A transparent, evidence-based finding produced by the static risk engine.
 * Note: Static analysis never claims confirmed malware without external threat telemetry.
 */
data class RiskFinding(
    val id: String,
    val title: String,
    val category: StaticRiskCategory,
    val severity: StaticRiskSeverity,
    val description: String,
    val evidence: String,
    val recommendation: String,
    val affectedComponentOrFile: String,
    val isMalwareConfirmed: Boolean = false
) {
    fun toSecurityFinding(): SecurityFinding {
        val mappedCategory = when (category) {
            StaticRiskCategory.SECURITY -> FindingCategory.DEPENDENCIES
            StaticRiskCategory.PRIVACY -> FindingCategory.APK_PERMISSIONS
            StaticRiskCategory.INTEGRITY, StaticRiskCategory.ARCHIVE_ANOMALY -> FindingCategory.SIGNING_INTEGRITY
            StaticRiskCategory.COMPONENT_EXPOSURE -> FindingCategory.EXPORTED_COMPONENTS
            StaticRiskCategory.NETWORK_SECURITY -> FindingCategory.NETWORK_SECURITY
            StaticRiskCategory.CRYPTOGRAPHY -> FindingCategory.CRYPTOGRAPHY
            StaticRiskCategory.SYSTEM_RESTRICTION -> FindingCategory.SECRETS
        }
        val mappedSeverity = when (severity) {
            StaticRiskSeverity.INFO -> SecuritySeverity.INFO
            StaticRiskSeverity.LOW -> SecuritySeverity.LOW
            StaticRiskSeverity.MEDIUM -> SecuritySeverity.MEDIUM
            StaticRiskSeverity.HIGH -> SecuritySeverity.HIGH
            StaticRiskSeverity.CRITICAL -> SecuritySeverity.CRITICAL
        }
        return SecurityFinding(
            id = id,
            category = mappedCategory,
            severity = mappedSeverity,
            title = title,
            description = description,
            evidence = evidence,
            recommendation = recommendation,
            module = "StaticRiskEngine",
            affectedFile = affectedComponentOrFile
        )
    }
}

/**
 * High-level summary of the static APK inspection.
 */
data class ScanSummary(
    val totalFiles: Int,
    val totalCompressedBytes: Long,
    val totalUncompressedBytes: Long,
    val compressionRatio: Double,
    val dexCount: Int,
    val totalDexClasses: Int,
    val permissionCount: Int,
    val dangerousPermissionCount: Int,
    val sensitivePermissions: List<String>,
    val totalComponents: Int,
    val exportedComponentsCount: Int,
    val nativeLibCount: Int,
    val supportedAbis: List<String>,
    val hasKotlinMetadata: Boolean,
    val configFilesFound: List<String>,
    val riskFindingCounts: Map<StaticRiskSeverity, Int>,
    val isSigned: Boolean,
    val isDebuggable: Boolean,
    val isSample: Boolean,
    val overallRiskLevel: StaticRiskSeverity
)

/**
 * Complete, strongly typed result of a static deep scan on an APK file.
 */
data class ScanResult(
    val scanId: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val md5: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int?,
    val isDebuggable: Boolean,
    val allowsBackup: Boolean,
    val permissions: List<ScanPermissionInfo>,
    val components: List<ComponentInfo>,
    val dexInfoList: List<DexInfo>,
    val nativeLibraries: List<NativeLibraryInfo>,
    val signatureInfo: SignatureInfo,
    val archiveEntries: List<ApkFileEntry>,
    val kotlinMetadataPresent: Boolean,
    val configFiles: List<String>,
    val riskFindings: List<RiskFinding>,
    val summary: ScanSummary,
    val isSuccess: Boolean,
    val isSample: Boolean,
    val scanTimestamp: Long = System.currentTimeMillis(),
    val scanDurationMs: Long = 0L,
    val scannerVersion: String = "2.5.0-static-deep",
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    fun toApkScanResult(uriString: String = fileName): ApkScanResult {
        val abiCoverage = nativeLibraries.map { it.abi }.distinct()
        val fileInfo = ApkFileInfo(
            scanId = scanId,
            fileName = fileName,
            fileSize = fileSize,
            sha256 = sha256,
            md5 = md5,
            mimeType = "application/vnd.android.package-archive",
            uri = uriString,
            createdAt = scanTimestamp,
            status = if (isSuccess) ScanStatus.COMPLETED else ScanStatus.FAILED,
            errorMessage = errors.firstOrNull(),
            totalZipEntries = archiveEntries.size,
            compressedSize = summary.totalCompressedBytes,
            uncompressedSize = summary.totalUncompressedBytes,
            compressionRatio = summary.compressionRatio,
            dexCount = dexInfoList.size,
            nativeLibraryCount = nativeLibraries.size,
            assetCount = archiveEntries.count { it.category == EntryCategory.ASSET },
            resourcePresence = archiveEntries.any { it.category == EntryCategory.RESOURCE },
            signingRelatedFiles = archiveEntries.filter { it.category == EntryCategory.SIGNATURE }.map { it.name },
            archiveEntries = archiveEntries.map { it.toArchiveEntryDetail() }
        )

        val manifestInfo = ManifestInfo(
            packageName = packageName,
            appName = appName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            permissions = permissions.map { it.name },
            usesFeatures = emptyList(),
            activities = components.filter { it.type == "Activity" },
            services = components.filter { it.type == "Service" },
            receivers = components.filter { it.type == "Receiver" },
            providers = components.filter { it.type == "Provider" },
            intentFiltersCount = 0,
            exportedComponentsCount = components.count { it.exported },
            isDebuggable = isDebuggable,
            allowBackup = allowsBackup,
            usesCleartextTraffic = riskFindings.any { it.id.startsWith("SEC-NET-001") || it.id.startsWith("RISK-MAN-002") },
            networkSecurityConfig = null,
            theme = null,
            supportedArchitectures = abiCoverage,
            rawXmlText = ""
        )

        return ApkScanResult(
            scanId = scanId,
            fileInfo = fileInfo,
            manifestInfo = manifestInfo,
            dexList = dexInfoList,
            permissionsList = permissions,
            nativeLibrariesList = nativeLibraries,
            abiCoverage = abiCoverage,
            certificatesList = signatureInfo.certificates,
            securityFindings = riskFindings.map { it.toSecurityFinding() },
            status = if (isSuccess) ScanStatus.COMPLETED else ScanStatus.FAILED,
            errorMessage = errors.firstOrNull(),
            completedAt = scanTimestamp + scanDurationMs,
            isSample = isSample,
            scanDurationMs = scanDurationMs,
            scannerVersion = scannerVersion,
            signatureInfo = signatureInfo,
            scanSummary = summary,
            deepRiskFindings = riskFindings,
            deepScanResult = this
        )
    }
}
