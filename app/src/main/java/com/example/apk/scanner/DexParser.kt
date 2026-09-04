package com.example.apk.scanner

import com.example.apk.model.DexFileInfo
import com.example.apk.model.DexInfo
import com.example.core.CryptoUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DexParser {

    fun parseDex(name: String, bytes: ByteArray): DexInfo {
        val sha256 = CryptoUtils.calculateSha256(bytes)
        val fileSize = bytes.size.toLong()

        if (bytes.size < 112) {
            return DexInfo(
                fileName = name,
                fileSize = fileSize,
                sha256 = sha256,
                magic = "Unavailable",
                version = "Unavailable",
                adler32Checksum = "Unavailable",
                sha1Signature = "Unavailable",
                classDefsCount = 0,
                methodIdsEstimate = 0,
                stringIdsCount = 0,
                typeIdsCount = 0,
                protoIdsCount = 0,
                fieldIdsCount = 0,
                classNames = emptyList(),
                semanticAnalysisStatus = "Advanced DEX semantic parsing unavailable"
            )
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magicBytes = ByteArray(8)
        buffer.get(magicBytes)
        val magicStr = String(magicBytes, Charsets.US_ASCII)

        val isDexMagic = magicStr.startsWith("dex\n")
        val version = if (isDexMagic && magicStr.length >= 7) {
            magicStr.substring(4, 7).trim()
        } else {
            "Unavailable"
        }

        val magicDisplay = if (isDexMagic) "dex\\n$version\\0" else "Unknown magic (${CryptoUtils.bytesToHex(magicBytes.take(4).toByteArray())})"

        // Checksum (4 bytes little endian uint32)
        val checksumInt = buffer.int
        val adler32Hex = "0x" + Integer.toHexString(checksumInt).uppercase().padStart(8, '0')

        // SHA-1 signature (20 bytes)
        val sigBytes = ByteArray(20)
        buffer.get(sigBytes)
        val sha1SigHex = CryptoUtils.bytesToHex(sigBytes)

        // file_size(4) + header_size(4) + endian_tag(4) + link_size(4) + link_off(4) + map_off(4)
        val declaredFileSize = buffer.int
        val headerSize = buffer.int
        val endianTag = buffer.int
        val linkSize = buffer.int
        val linkOff = buffer.int
        val mapOff = buffer.int

        val stringIdsSize = buffer.int.coerceAtLeast(0)
        val stringIdsOff = buffer.int
        val typeIdsSize = buffer.int.coerceAtLeast(0)
        val typeIdsOff = buffer.int
        val protoIdsSize = buffer.int.coerceAtLeast(0)
        val protoIdsOff = buffer.int
        val fieldIdsSize = buffer.int.coerceAtLeast(0)
        val fieldIdsOff = buffer.int
        val methodIdsSize = buffer.int.coerceAtLeast(0)
        val methodIdsOff = buffer.int
        val classDefsSize = buffer.int.coerceAtLeast(0)
        val classDefsOff = buffer.int

        val classNames = mutableListOf<String>()

        try {
            fun readString(stringIdx: Int): String? {
                if (stringIdx < 0 || stringIdx >= stringIdsSize) return null
                val strOffPos = stringIdsOff + (stringIdx * 4)
                if (strOffPos + 4 > bytes.size || strOffPos < 0) return null
                buffer.position(strOffPos)
                val dataOff = buffer.int
                if (dataOff < 0 || dataOff >= bytes.size) return null

                // Read ULEB128 utf16_size
                var p = dataOff
                var utf16Len = 0
                var shift = 0
                while (p < bytes.size) {
                    val b = bytes[p++].toInt() and 0xFF
                    utf16Len = utf16Len or ((b and 0x7F) shl shift)
                    if ((b and 0x80) == 0) break
                    shift += 7
                }

                val strBytesStart = p
                while (p < bytes.size && bytes[p] != 0.toByte()) {
                    p++
                }
                return String(bytes, strBytesStart, (p - strBytesStart).coerceAtLeast(0), Charsets.UTF_8)
            }

            fun getTypeName(typeIdx: Int): String? {
                if (typeIdx < 0 || typeIdx >= typeIdsSize) return null
                val typeOffPos = typeIdsOff + (typeIdx * 4)
                if (typeOffPos + 4 > bytes.size || typeOffPos < 0) return null
                buffer.position(typeOffPos)
                val descriptorIdx = buffer.int
                return readString(descriptorIdx)
            }

            // Parse class_defs up to 50 real classes
            val maxClassesToExtract = minOf(classDefsSize, 50)
            for (i in 0 until maxClassesToExtract) {
                val classDefPos = classDefsOff + (i * 32)
                if (classDefPos + 32 > bytes.size || classDefPos < 0) break
                buffer.position(classDefPos)
                val classIdx = buffer.int
                val rawDescriptor = getTypeName(classIdx)
                if (!rawDescriptor.isNullOrBlank()) {
                    val formatted = if (rawDescriptor.startsWith("L") && rawDescriptor.endsWith(";")) {
                        rawDescriptor.substring(1, rawDescriptor.length - 1).replace('/', '.')
                    } else {
                        rawDescriptor
                    }
                    classNames.add(formatted)
                }
            }
        } catch (_: Exception) {
            // Graceful fallback for corrupted DEX tables
        }

        return DexInfo(
            fileName = name,
            fileSize = fileSize,
            sha256 = sha256,
            magic = magicDisplay,
            version = version,
            adler32Checksum = adler32Hex,
            sha1Signature = sha1SigHex,
            classDefsCount = classDefsSize,
            methodIdsEstimate = methodIdsSize,
            stringIdsCount = stringIdsSize,
            typeIdsCount = typeIdsSize,
            protoIdsCount = protoIdsSize,
            fieldIdsCount = fieldIdsSize,
            classNames = classNames,
            semanticAnalysisStatus = "Advanced DEX semantic parsing unavailable"
        )
    }

    /**
     * Backward-compatibility helper for legacy DexFileInfo callers.
     */
    fun parseDexHeader(name: String, bytes: ByteArray): DexFileInfo {
        val dex = parseDex(name, bytes)
        return DexFileInfo(
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
}
