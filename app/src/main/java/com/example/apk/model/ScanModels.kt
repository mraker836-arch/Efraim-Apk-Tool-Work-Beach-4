package com.example.apk.model

import com.example.security.model.SecurityFinding

enum class ScanStatus {
    QUEUED,
    READING,
    HASHING,
    INSPECTING_ARCHIVE,
    ANALYZING_DEX,
    ANALYZING_MANIFEST,
    ANALYZING_PERMISSIONS,
    ANALYZING_NATIVE_LIBRARIES,
    ANALYZING_CERTIFICATE,
    ANALYZING_SECURITY,
    COMPLETED,
    FAILED
}

enum class ProtectionCategory {
    NORMAL,
    DANGEROUS,
    SPECIAL,
    SIGNATURE,
    UNKNOWN
}

enum class CertificateStatus {
    SIGNATURE_FILES_PRESENT,
    CERTIFICATE_READ,
    CERTIFICATE_VERIFIED,
    SIGNATURE_UNAVAILABLE,
    SIGNATURE_INVALID
}

data class ApkFileInfo(
    val scanId: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val md5: String,
    val mimeType: String,
    val uri: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: ScanStatus = ScanStatus.QUEUED,
    val errorMessage: String? = null,
    val totalZipEntries: Int = 0,
    val compressedSize: Long = 0L,
    val uncompressedSize: Long = 0L,
    val compressionRatio: Double = 0.0,
    val dexCount: Int = 0,
    val nativeLibraryCount: Int = 0,
    val assetCount: Int = 0,
    val resourcePresence: Boolean = false,
    val signingRelatedFiles: List<String> = emptyList()
)

data class DexInfo(
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val magic: String,
    val version: String,
    val adler32Checksum: String,
    val sha1Signature: String,
    val classDefsCount: Int,
    val methodIdsEstimate: Int,
    val stringIdsCount: Int,
    val typeIdsCount: Int,
    val protoIdsCount: Int,
    val fieldIdsCount: Int,
    val classNames: List<String> = emptyList(),
    val semanticAnalysisStatus: String = "Advanced DEX semantic parsing unavailable"
)

data class ManifestInfo(
    val packageName: String,
    val appName: String,
    val versionCode: Long,
    val versionName: String,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int?,
    val permissions: List<String>,
    val usesFeatures: List<String>,
    val activities: List<ComponentInfo>,
    val services: List<ComponentInfo>,
    val receivers: List<ComponentInfo>,
    val providers: List<ComponentInfo>,
    val intentFiltersCount: Int,
    val exportedComponentsCount: Int,
    val isDebuggable: Boolean,
    val allowBackup: Boolean,
    val usesCleartextTraffic: Boolean,
    val networkSecurityConfig: String?,
    val theme: String?,
    val supportedArchitectures: List<String>,
    val rawXmlText: String
)

data class ScanPermissionInfo(
    val name: String,
    val simpleName: String = name.substringAfterLast("."),
    val protectionCategory: ProtectionCategory,
    val riskIndicator: RiskLevel,
    val reason: String,
    val description: String
)

data class NativeLibraryInfo(
    val abi: String,
    val libraryName: String,
    val fileSize: Long,
    val sha256: String
)

data class ScanCertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val validFrom: String,
    val validUntil: String,
    val signatureAlgorithm: String,
    val publicKeyAlgorithm: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val md5Fingerprint: String,
    val status: CertificateStatus,
    val verificationDetails: String,
    val isSelfSigned: Boolean,
    val keySizeBits: Int = 2048
)

data class ApkScanResult(
    val scanId: String,
    val fileInfo: ApkFileInfo,
    val manifestInfo: ManifestInfo,
    val dexList: List<DexInfo>,
    val permissionsList: List<ScanPermissionInfo>,
    val nativeLibrariesList: List<NativeLibraryInfo>,
    val abiCoverage: List<String>,
    val certificatesList: List<ScanCertificateInfo>,
    val securityFindings: List<SecurityFinding>,
    val status: ScanStatus,
    val errorMessage: String? = null,
    val completedAt: Long = System.currentTimeMillis()
)
