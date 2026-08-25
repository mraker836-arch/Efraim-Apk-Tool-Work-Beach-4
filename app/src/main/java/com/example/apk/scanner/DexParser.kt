package com.example.apk.scanner

import com.example.apk.model.DexFileInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DexParser {

    fun parseDexHeader(name: String, bytes: ByteArray): DexFileInfo {
        if (bytes.size < 112) {
            return DexFileInfo(name, bytes.size.toLong(), 0, 0, "unknown")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = ByteArray(8)
        buffer.get(magic)
        val magicStr = String(magic, Charsets.US_ASCII)
        val version = if (magicStr.startsWith("dex\n")) {
            magicStr.substring(4, 7).trim()
        } else {
            "035"
        }

        // Skip checksum(4) + signature(20) + file_size(4) + header_size(4) + endian_tag(4) + link_size(4) + link_off(4) + map_off(4)
        buffer.position(8 + 4 + 20 + 4 + 4 + 4 + 4 + 4 + 4)

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
            // Helper to read string by string_id index
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

                // Read null-terminated MUTF-8 string
                val strBytesStart = p
                while (p < bytes.size && bytes[p] != 0.toByte()) {
                    p++
                }
                val rawStr = String(bytes, strBytesStart, (p - strBytesStart).coerceAtLeast(0), Charsets.UTF_8)
                return rawStr
            }

            // Helper to get type descriptor by type_id index
            fun getTypeName(typeIdx: Int): String? {
                if (typeIdx < 0 || typeIdx >= typeIdsSize) return null
                val typeOffPos = typeIdsOff + (typeIdx * 4)
                if (typeOffPos + 4 > bytes.size || typeOffPos < 0) return null
                buffer.position(typeOffPos)
                val descriptorIdx = buffer.int
                return readString(descriptorIdx)
            }

            // Parse class_defs
            val maxClassesToExtract = minOf(classDefsSize, 100)
            for (i in 0 until maxClassesToExtract) {
                val classDefPos = classDefsOff + (i * 32)
                if (classDefPos + 32 > bytes.size || classDefPos < 0) break
                buffer.position(classDefPos)
                val classIdx = buffer.int
                val rawDescriptor = getTypeName(classIdx)
                if (!rawDescriptor.isNullOrBlank()) {
                    // Convert Lcom/example/MyClass; -> com.example.MyClass
                    val formatted = if (rawDescriptor.startsWith("L") && rawDescriptor.endsWith(";")) {
                        rawDescriptor.substring(1, rawDescriptor.length - 1).replace('/', '.')
                    } else {
                        rawDescriptor
                    }
                    classNames.add(formatted)
                }
            }
        } catch (_: Exception) {
            // Safe fallback if corrupted DEX
        }

        return DexFileInfo(
            name = name,
            sizeBytes = bytes.size.toLong(),
            classDefsCount = classDefsSize,
            methodIdsEstimate = methodIdsSize,
            dexVersion = version,
            stringIdsCount = stringIdsSize,
            typeIdsCount = typeIdsSize,
            protoIdsCount = protoIdsSize,
            fieldIdsCount = fieldIdsSize,
            classNames = classNames
        )
    }
}
