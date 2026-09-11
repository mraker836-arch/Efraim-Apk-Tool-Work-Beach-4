package com.example.apk.diff

import com.example.apk.model.ApkScanResult
import java.util.Locale

data class DiffEntry<T>(
    val key: String,
    val oldValue: T?,
    val newValue: T?,
    val changeType: DiffChangeType
)

enum class DiffChangeType {
    ADDED,
    REMOVED,
    MODIFIED,
    UNCHANGED
}

data class ApkDiffReport(
    val apk1Name: String,
    val apk2Name: String,
    val apk1Size: Long,
    val apk2Size: Long,
    val sizeDeltaBytes: Long,
    val apk1Sha256: String,
    val apk2Sha256: String,
    val isSameFile: Boolean,
    val packageDiff: DiffEntry<String>,
    val versionNameDiff: DiffEntry<String>,
    val versionCodeDiff: DiffEntry<Long>,
    val minSdkDiff: DiffEntry<Int>,
    val targetSdkDiff: DiffEntry<Int>,
    val permissionDiffs: List<DiffEntry<String>>,
    val dexCountDiff: DiffEntry<Int>,
    val nativeLibDiffs: List<DiffEntry<String>>,
    val securityFindingsDelta: Int,
    val totalAddedPermissions: Int,
    val totalRemovedPermissions: Int,
    val totalAddedNativeLibs: Int,
    val totalRemovedNativeLibs: Int
) {
    val sizeDeltaFormatted: String get() {
        val sign = if (sizeDeltaBytes > 0) "+" else if (sizeDeltaBytes < 0) "-" else ""
        val absBytes = Math.abs(sizeDeltaBytes)
        val formatted = when {
            absBytes < 1024 -> "$absBytes B"
            absBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", absBytes.toDouble() / 1024)
            else -> String.format(Locale.US, "%.2f MB", absBytes.toDouble() / (1024 * 1024))
        }
        return "$sign$formatted"
    }
}

object ApkDiffEngine {

    /**
     * Compares two parsed ApkScanResults structurally, cryptographically, and functionally.
     * Computes permission changes, native ABI differences, manifest evolution, and byte deltas.
     */
    fun compare(apk1: ApkScanResult, apk2: ApkScanResult): ApkDiffReport {
        val sizeDelta = apk2.fileInfo.fileSize - apk1.fileInfo.fileSize
        val isSame = apk1.fileInfo.sha256 == apk2.fileInfo.sha256 && apk1.fileInfo.sha256.isNotBlank()

        val packageDiff = DiffEntry(
            key = "Package Name",
            oldValue = apk1.manifestInfo.packageName,
            newValue = apk2.manifestInfo.packageName,
            changeType = if (apk1.manifestInfo.packageName == apk2.manifestInfo.packageName) DiffChangeType.UNCHANGED else DiffChangeType.MODIFIED
        )

        val versionNameDiff = DiffEntry(
            key = "Version Name",
            oldValue = apk1.manifestInfo.versionName,
            newValue = apk2.manifestInfo.versionName,
            changeType = if (apk1.manifestInfo.versionName == apk2.manifestInfo.versionName) DiffChangeType.UNCHANGED else DiffChangeType.MODIFIED
        )

        val versionCodeDiff = DiffEntry(
            key = "Version Code",
            oldValue = apk1.manifestInfo.versionCode,
            newValue = apk2.manifestInfo.versionCode,
            changeType = if (apk1.manifestInfo.versionCode == apk2.manifestInfo.versionCode) DiffChangeType.UNCHANGED else DiffChangeType.MODIFIED
        )

        val minSdkDiff = DiffEntry(
            key = "Min SDK",
            oldValue = apk1.manifestInfo.minSdk,
            newValue = apk2.manifestInfo.minSdk,
            changeType = if (apk1.manifestInfo.minSdk == apk2.manifestInfo.minSdk) DiffChangeType.UNCHANGED else DiffChangeType.MODIFIED
        )

        val targetSdkDiff = DiffEntry(
            key = "Target SDK",
            oldValue = apk1.manifestInfo.targetSdk,
            newValue = apk2.manifestInfo.targetSdk,
            changeType = if (apk1.manifestInfo.targetSdk == apk2.manifestInfo.targetSdk) DiffChangeType.UNCHANGED else DiffChangeType.MODIFIED
        )

        // Permissions diff
        val perms1 = apk1.permissionsList.map { it.name }.toSet()
        val perms2 = apk2.permissionsList.map { it.name }.toSet()
        val allPerms = (perms1 + perms2).sorted()

        val permDiffs = allPerms.mapNotNull { perm ->
            when {
                perm in perms1 && perm in perms2 -> DiffEntry(perm, perm, perm, DiffChangeType.UNCHANGED)
                perm in perms2 -> DiffEntry(perm, null, perm, DiffChangeType.ADDED)
                perm in perms1 -> DiffEntry(perm, perm, null, DiffChangeType.REMOVED)
                else -> null
            }
        }

        // Native libs diff
        val libs1 = apk1.nativeLibrariesList.map { "${it.abi}/${it.libraryName}" }.toSet()
        val libs2 = apk2.nativeLibrariesList.map { "${it.abi}/${it.libraryName}" }.toSet()
        val allLibs = (libs1 + libs2).sorted()

        val libDiffs = allLibs.mapNotNull { lib ->
            when {
                lib in libs1 && lib in libs2 -> DiffEntry(lib, lib, lib, DiffChangeType.UNCHANGED)
                lib in libs2 -> DiffEntry(lib, null, lib, DiffChangeType.ADDED)
                lib in libs1 -> DiffEntry(lib, lib, null, DiffChangeType.REMOVED)
                else -> null
            }
        }

        val dexCountDiff = DiffEntry(
            key = "DEX Files Count",
            oldValue = apk1.fileInfo.dexCount,
            newValue = apk2.fileInfo.dexCount,
            changeType = if (apk1.fileInfo.dexCount == apk2.fileInfo.dexCount) DiffChangeType.UNCHANGED else DiffChangeType.MODIFIED
        )

        val findingsDelta = apk2.securityFindings.size - apk1.securityFindings.size

        return ApkDiffReport(
            apk1Name = apk1.fileInfo.fileName,
            apk2Name = apk2.fileInfo.fileName,
            apk1Size = apk1.fileInfo.fileSize,
            apk2Size = apk2.fileInfo.fileSize,
            sizeDeltaBytes = sizeDelta,
            apk1Sha256 = apk1.fileInfo.sha256,
            apk2Sha256 = apk2.fileInfo.sha256,
            isSameFile = isSame,
            packageDiff = packageDiff,
            versionNameDiff = versionNameDiff,
            versionCodeDiff = versionCodeDiff,
            minSdkDiff = minSdkDiff,
            targetSdkDiff = targetSdkDiff,
            permissionDiffs = permDiffs,
            dexCountDiff = dexCountDiff,
            nativeLibDiffs = libDiffs,
            securityFindingsDelta = findingsDelta,
            totalAddedPermissions = permDiffs.count { it.changeType == DiffChangeType.ADDED },
            totalRemovedPermissions = permDiffs.count { it.changeType == DiffChangeType.REMOVED },
            totalAddedNativeLibs = libDiffs.count { it.changeType == DiffChangeType.ADDED },
            totalRemovedNativeLibs = libDiffs.count { it.changeType == DiffChangeType.REMOVED }
        )
    }
}
