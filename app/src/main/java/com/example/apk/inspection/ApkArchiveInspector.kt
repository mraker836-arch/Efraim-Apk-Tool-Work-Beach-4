package com.example.apk.inspection

import com.example.apk.model.ApkFileInfo
import com.example.apk.model.ArchiveEntryDetail
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ArchiveInspectionSummary(
    val totalEntries: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val compressionRatio: Double,
    val hasManifest: Boolean,
    val hasResourcesArsc: Boolean,
    val dexFiles: List<String>,
    val metaInfFiles: List<String>,
    val nativeLibFiles: List<String>,
    val assetFiles: List<String>,
    val entries: List<ArchiveEntryDetail>
)

object ApkArchiveInspector {

    /**
     * Inspects the real APK ZIP archive from a local File.
     * Extracts entry names, compressed and uncompressed sizes, compression methods, and structural files.
     */
    fun inspectArchive(apkFile: File): ArchiveInspectionSummary {
        if (!apkFile.exists() || !apkFile.canRead()) {
            return fallbackSummary(apkFile.name)
        }

        var totalEntries = 0
        var compressedSize = 0L
        var uncompressedSize = 0L
        var hasManifest = false
        var hasResourcesArsc = false
        val dexFiles = mutableListOf<String>()
        val metaInfFiles = mutableListOf<String>()
        val nativeLibFiles = mutableListOf<String>()
        val assetFiles = mutableListOf<String>()
        val entriesList = mutableListOf<ArchiveEntryDetail>()

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/")) {
                        continue // Skip dangerous paths
                    }

                    totalEntries++
                    val cSize = entry.compressedSize.coerceAtLeast(0L)
                    val uSize = entry.size.coerceAtLeast(0L)
                    compressedSize += cSize
                    uncompressedSize += uSize

                    val methodStr = when (entry.method) {
                        ZipEntry.DEFLATED -> "Deflated (8)"
                        ZipEntry.STORED -> "Stored (0)"
                        else -> "Method ${entry.method}"
                    }

                    val detail = ArchiveEntryDetail(
                        name = name,
                        compressedSize = cSize,
                        uncompressedSize = uSize,
                        compressionMethod = methodStr,
                        isDirectory = entry.isDirectory,
                        crc = entry.crc
                    )
                    entriesList.add(detail)

                    when {
                        name == "AndroidManifest.xml" -> hasManifest = true
                        name == "resources.arsc" -> hasResourcesArsc = true
                        name.startsWith("classes") && name.endsWith(".dex") -> dexFiles.add(name)
                        name.startsWith("META-INF/") -> metaInfFiles.add(name)
                        name.startsWith("lib/") && name.endsWith(".so") -> nativeLibFiles.add(name)
                        name.startsWith("assets/") -> assetFiles.add(name)
                    }
                }
            }

            // Natural sort classes.dex, classes2.dex, classes3.dex...
            val sortedDex = dexFiles.sortedWith(Comparator { a, b ->
                fun extractNum(s: String): Int {
                    val num = s.removePrefix("classes").removeSuffix(".dex")
                    return if (num.isEmpty()) 1 else num.toIntOrNull() ?: 999
                }
                extractNum(a).compareTo(extractNum(b))
            })

            val ratio = if (uncompressedSize > 0) {
                (1.0 - (compressedSize.toDouble() / uncompressedSize.toDouble())).coerceIn(0.0, 1.0) * 100.0
            } else 0.0

            return ArchiveInspectionSummary(
                totalEntries = totalEntries,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                compressionRatio = ratio,
                hasManifest = hasManifest,
                hasResourcesArsc = hasResourcesArsc,
                dexFiles = sortedDex,
                metaInfFiles = metaInfFiles.sorted(),
                nativeLibFiles = nativeLibFiles.sorted(),
                assetFiles = assetFiles.sorted(),
                entries = entriesList
            )
        } catch (e: Exception) {
            return fallbackSummary(apkFile.name, e.message)
        }
    }

    /**
     * Builds an ArchiveInspectionSummary from persisted ApkFileInfo when the raw file is not present on disk.
     */
    fun fromFileInfo(fileInfo: ApkFileInfo): ArchiveInspectionSummary {
        val dexList = if (fileInfo.dexCount > 0) {
            (1..fileInfo.dexCount).map { if (it == 1) "classes.dex" else "classes$it.dex" }
        } else emptyList()

        return ArchiveInspectionSummary(
            totalEntries = fileInfo.totalZipEntries,
            compressedSize = fileInfo.compressedSize,
            uncompressedSize = fileInfo.uncompressedSize,
            compressionRatio = fileInfo.compressionRatio,
            hasManifest = true,
            hasResourcesArsc = fileInfo.resourcePresence,
            dexFiles = dexList,
            metaInfFiles = fileInfo.signingRelatedFiles,
            nativeLibFiles = emptyList(),
            assetFiles = emptyList(),
            entries = fileInfo.archiveEntries
        )
    }

    private fun fallbackSummary(fileName: String, error: String? = null): ArchiveInspectionSummary {
        return ArchiveInspectionSummary(
            totalEntries = 0,
            compressedSize = 0L,
            uncompressedSize = 0L,
            compressionRatio = 0.0,
            hasManifest = false,
            hasResourcesArsc = false,
            dexFiles = emptyList(),
            metaInfFiles = emptyList(),
            nativeLibFiles = emptyList(),
            assetFiles = emptyList(),
            entries = emptyList()
        )
    }
}
