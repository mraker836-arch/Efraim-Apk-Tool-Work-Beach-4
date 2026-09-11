package com.example.apk.model

enum class RiskLevel {
    INFO,
    WARNING,
    HIGH,
    CRITICAL
}

enum class SigningStatus {
    SIGNED_V1,
    SIGNED_V2,
    SIGNED_V1_V2,
    UNSIGNED,
    TAMPERED,
    INVALID
}

data class PermissionInfo(
    val name: String,
    val simpleName: String = name.substringAfterLast("."),
    val protectionLevel: String = "normal",
    val riskLevel: RiskLevel = RiskLevel.INFO,
    val description: String = ""
)

data class ComponentInfo(
    val type: String, // Activity, Service, Receiver, Provider
    val name: String,
    val simpleName: String = name.substringAfterLast("."),
    val exported: Boolean = false,
    val permission: String? = null,
    val intentActions: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    val intentCategories: List<String> = emptyList()
)

data class DexFileInfo(
    val name: String,
    val sizeBytes: Long,
    val classDefsCount: Int,
    val methodIdsEstimate: Int,
    val dexVersion: String,
    val stringIdsCount: Int = 0,
    val typeIdsCount: Int = 0,
    val protoIdsCount: Int = 0,
    val fieldIdsCount: Int = 0,
    val classNames: List<String> = emptyList()
)

data class AssetFileInfo(
    val path: String,
    val sizeBytes: Long,
    val isCompressed: Boolean = false
)

data class ResourceFileInfo(
    val path: String,
    val type: String, // layout, drawable, values, mipmap, raw, etc.
    val sizeBytes: Long
)

data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val md5Fingerprint: String,
    val algorithm: String,
    val validFrom: String,
    val validUntil: String,
    val isSelfSigned: Boolean,
    val keySizeBits: Int = 2048
)

data class APKInfo(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val sha256: String,
    val md5: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int? = null,
    val isDebuggable: Boolean = false,
    val allowsBackup: Boolean = true,
    val supportsRtl: Boolean = true,
    val permissions: List<PermissionInfo> = emptyList(),
    val activities: List<ComponentInfo> = emptyList(),
    val services: List<ComponentInfo> = emptyList(),
    val receivers: List<ComponentInfo> = emptyList(),
    val providers: List<ComponentInfo> = emptyList(),
    val dexFiles: List<DexFileInfo> = emptyList(),
    val nativeLibraries: Map<String, List<String>> = emptyMap(), // ABI -> list of .so files
    val assets: List<AssetFileInfo> = emptyList(),
    val resources: List<ResourceFileInfo> = emptyList(),
    val certificates: List<CertificateInfo> = emptyList(),
    val signingStatus: SigningStatus = SigningStatus.UNSIGNED,
    val importedAt: Long = System.currentTimeMillis(),
    val totalEntriesCount: Int = 0,
    val isSample: Boolean = false
) {
    val totalDexClasses: Int get() = dexFiles.sumOf { it.classDefsCount }
    val supportedAbis: List<String> get() = nativeLibraries.keys.toList()
    val hasNativeLibs: Boolean get() = nativeLibraries.isNotEmpty()
    val dangerousPermissionsCount: Int get() = permissions.count { it.riskLevel == RiskLevel.HIGH || it.riskLevel == RiskLevel.CRITICAL }
}
