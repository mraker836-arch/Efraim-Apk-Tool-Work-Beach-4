package com.example.apk.inspection

import com.example.apk.model.ApkScanResult
import com.example.apk.model.ArchiveEntryDetail
import com.example.apk.model.ComponentInfo
import com.example.apk.model.DexInfo
import com.example.apk.model.NativeLibraryInfo
import com.example.apk.model.ScanCertificateInfo
import com.example.apk.model.ScanPermissionInfo
import com.example.security.model.SecurityFinding

data class InspectionSearchResults(
    val query: String,
    val totalMatches: Int,
    val matchingComponents: List<ComponentInfo>,
    val matchingPermissions: List<ScanPermissionInfo>,
    val matchingDex: List<DexInfo>,
    val matchingNativeLibs: List<NativeLibraryInfo>,
    val matchingCertificates: List<ScanCertificateInfo>,
    val matchingArchiveEntries: List<ArchiveEntryDetail>,
    val matchingFindings: List<SecurityFinding>
) {
    val isEmpty: Boolean get() = totalMatches == 0
}

object ApkInspectionSearchEngine {

    /**
     * Performs fast, local, in-memory search across all inspected APK structures.
     * Operates purely on client-side data without sending any APK contents off-device.
     */
    fun search(
        scanResult: ApkScanResult?,
        archiveEntries: List<ArchiveEntryDetail>,
        rawQuery: String
    ): InspectionSearchResults {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty() || scanResult == null) {
            return InspectionSearchResults(
                query = rawQuery,
                totalMatches = 0,
                matchingComponents = emptyList(),
                matchingPermissions = emptyList(),
                matchingDex = emptyList(),
                matchingNativeLibs = emptyList(),
                matchingCertificates = emptyList(),
                matchingArchiveEntries = emptyList(),
                matchingFindings = emptyList()
            )
        }

        // Search components
        val allComponents = scanResult.manifestInfo.activities +
                scanResult.manifestInfo.services +
                scanResult.manifestInfo.receivers +
                scanResult.manifestInfo.providers
        val matchComponents = allComponents.filter { c ->
            c.name.lowercase().contains(q) ||
                    c.simpleName.lowercase().contains(q) ||
                    c.type.lowercase().contains(q) ||
                    (c.permission?.lowercase()?.contains(q) == true) ||
                    c.intentActions.any { it.lowercase().contains(q) } ||
                    (c.exported && ("exported".contains(q) || q.contains("export")))
        }

        // Search permissions
        val matchPermissions = scanResult.permissionsList.filter { p ->
            p.name.lowercase().contains(q) ||
                    p.simpleName.lowercase().contains(q) ||
                    p.description.lowercase().contains(q) ||
                    p.reason.lowercase().contains(q) ||
                    p.protectionCategory.name.lowercase().contains(q)
        }

        // Search DEX files
        val matchDex = scanResult.dexList.filter { d ->
            d.fileName.lowercase().contains(q) ||
                    d.magic.lowercase().contains(q) ||
                    d.version.lowercase().contains(q) ||
                    d.adler32Checksum.lowercase().contains(q) ||
                    d.sha256.lowercase().contains(q) ||
                    d.classNames.any { it.lowercase().contains(q) }
        }

        // Search Native Libs
        val matchNative = scanResult.nativeLibrariesList.filter { lib ->
            lib.libraryName.lowercase().contains(q) ||
                    lib.abi.lowercase().contains(q) ||
                    lib.sha256.lowercase().contains(q)
        }

        // Search Certificates
        val matchCerts = scanResult.certificatesList.filter { cert ->
            cert.subject.lowercase().contains(q) ||
                    cert.issuer.lowercase().contains(q) ||
                    cert.serialNumber.lowercase().contains(q) ||
                    cert.sha256Fingerprint.lowercase().contains(q) ||
                    cert.sha1Fingerprint.lowercase().contains(q) ||
                    cert.signatureAlgorithm.lowercase().contains(q) ||
                    cert.publicKeyAlgorithm.lowercase().contains(q)
        }

        // Search Archive Entries
        val matchArchive = archiveEntries.filter { entry ->
            entry.name.lowercase().contains(q) ||
                    entry.compressionMethod.lowercase().contains(q)
        }

        // Search Findings
        val matchFindings = scanResult.securityFindings.filter { f ->
            f.title.lowercase().contains(q) ||
                    f.description.lowercase().contains(q) ||
                    f.evidence.lowercase().contains(q) ||
                    f.recommendation.lowercase().contains(q) ||
                    f.category.name.lowercase().contains(q) ||
                    f.severity.name.lowercase().contains(q)
        }

        val total = matchComponents.size +
                matchPermissions.size +
                matchDex.size +
                matchNative.size +
                matchCerts.size +
                matchArchive.size +
                matchFindings.size

        return InspectionSearchResults(
            query = rawQuery,
            totalMatches = total,
            matchingComponents = matchComponents,
            matchingPermissions = matchPermissions,
            matchingDex = matchDex,
            matchingNativeLibs = matchNative,
            matchingCertificates = matchCerts,
            matchingArchiveEntries = matchArchive,
            matchingFindings = matchFindings
        )
    }
}
