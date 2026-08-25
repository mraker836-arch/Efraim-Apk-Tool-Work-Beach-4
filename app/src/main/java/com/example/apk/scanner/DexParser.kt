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

        val stringIdsSize = buffer.int
        val stringIdsOff = buffer.int
        val typeIdsSize = buffer.int
        val typeIdsOff = buffer.int
        val protoIdsSize = buffer.int
        val protoIdsOff = buffer.int
        val fieldIdsSize = buffer.int
        val fieldIdsOff = buffer.int
        val methodIdsSize = buffer.int
        val methodIdsOff = buffer.int
        val classDefsSize = buffer.int
        val classDefsOff = buffer.int

        return DexFileInfo(
            name = name,
            sizeBytes = bytes.size.toLong(),
            classDefsCount = classDefsSize.coerceAtLeast(0),
            methodIdsEstimate = methodIdsSize.coerceAtLeast(0),
            dexVersion = version
        )
    }
}
