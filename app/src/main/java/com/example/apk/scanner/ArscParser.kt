package com.example.apk.scanner

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real on-device parser for Android compiled resource table (resources.arsc).
 * Decodes the root resource chunk, PackageChunk, and the global / package StringPools.
 * Zero-dependency, pure Kotlin implementation.
 */
object ArscParser {

    data class ResourceStringEntry(
        val index: Int,
        val value: String
    )

    data class ParsedArsc(
        val packageCount: Int,
        val packageNames: List<String>,
        val stringPoolCount: Int,
        val strings: List<ResourceStringEntry>,
        val isValid: Boolean,
        val statusMessage: String
    )

    private const val RES_NULL_TYPE = 0x0000
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200

    /**
     * Parses resources.arsc bytes into a structured representation.
     * Safely bounds-checks every offset to protect against out-of-bounds or corrupted files.
     */
    fun parse(bytes: ByteArray): ParsedArsc {
        if (bytes.size < 12) {
            return ParsedArsc(
                packageCount = 0,
                packageNames = emptyList(),
                stringPoolCount = 0,
                strings = emptyList(),
                isValid = false,
                statusMessage = "File too small for valid resources.arsc header (${bytes.size} bytes)"
            )
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val rootType = buffer.short.toInt() and 0xFFFF
        val rootHeaderSize = buffer.short.toInt() and 0xFFFF
        val rootChunkSize = buffer.int
        val packageCount = buffer.int

        if (rootType != RES_TABLE_TYPE) {
            return ParsedArsc(
                packageCount = 0,
                packageNames = emptyList(),
                stringPoolCount = 0,
                strings = emptyList(),
                isValid = false,
                statusMessage = "Invalid root chunk type 0x${Integer.toHexString(rootType).uppercase()} (expected 0x0002 RES_TABLE_TYPE)"
            )
        }

        val packageNames = mutableListOf<String>()
        val extractedStrings = mutableListOf<ResourceStringEntry>()
        var totalGlobalStrings = 0

        var offset = rootHeaderSize
        while (offset + 8 <= bytes.size && offset < rootChunkSize) {
            buffer.position(offset)
            val chunkType = buffer.short.toInt() and 0xFFFF
            val headerSize = buffer.short.toInt() and 0xFFFF
            val chunkSize = buffer.int

            if (chunkSize <= 0 || offset + chunkSize > bytes.size) {
                break
            }

            when (chunkType) {
                RES_STRING_POOL_TYPE -> {
                    // Global String Pool
                    val poolStrings = parseStringPool(bytes, offset, chunkSize)
                    totalGlobalStrings = poolStrings.size
                    // Take up to 200 strings for memory-safe previewing and searching
                    poolStrings.take(200).forEachIndexed { idx, s ->
                        extractedStrings.add(ResourceStringEntry(idx, s))
                    }
                }
                RES_TABLE_PACKAGE_TYPE -> {
                    // Package Chunk: id (4), name (128 utf16 chars / 256 bytes)
                    if (chunkSize >= 288 && offset + 288 <= bytes.size) {
                        buffer.position(offset + 8 + 4) // skip header (8) + package id (4)
                        val nameBytes = ByteArray(256)
                        buffer.get(nameBytes)
                        val pkgName = decodeUtf16ZeroTerminated(nameBytes)
                        if (pkgName.isNotBlank()) {
                            packageNames.add(pkgName)
                        }
                    }
                }
            }

            offset += chunkSize
        }

        return ParsedArsc(
            packageCount = packageCount.coerceAtLeast(packageNames.size),
            packageNames = packageNames,
            stringPoolCount = totalGlobalStrings,
            strings = extractedStrings,
            isValid = true,
            statusMessage = "Parsed ${packageNames.size} package(s), $totalGlobalStrings global resource string(s)"
        )
    }

    private fun parseStringPool(bytes: ByteArray, chunkStart: Int, chunkSize: Int): List<String> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (chunkStart + 28 > bytes.size) return emptyList()

        buffer.position(chunkStart + 8)
        val stringCount = buffer.int.coerceAtLeast(0)
        val styleCount = buffer.int.coerceAtLeast(0)
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        val isUtf8 = (flags and (1 shl 8)) != 0
        if (stringCount <= 0 || stringCount > 50000) return emptyList()

        val stringOffsets = IntArray(stringCount)
        val offsetsStart = chunkStart + 28
        if (offsetsStart + (stringCount * 4) > bytes.size) return emptyList()

        buffer.position(offsetsStart)
        for (i in 0 until stringCount) {
            stringOffsets[i] = buffer.int
        }

        val stringsBase = chunkStart + stringsStart
        val strings = mutableListOf<String>()

        for (i in 0 until stringCount) {
            val sOffset = stringsBase + stringOffsets[i]
            if (sOffset < 0 || sOffset >= chunkStart + chunkSize || sOffset >= bytes.size) {
                strings.add("")
                continue
            }
            buffer.position(sOffset)
            val str = try {
                if (isUtf8) {
                    readUtf8(buffer, bytes)
                } else {
                    readUtf16(buffer, bytes)
                }
            } catch (_: Exception) {
                ""
            }
            strings.add(str)
        }
        return strings
    }

    private fun readUtf8(buffer: ByteBuffer, bytes: ByteArray): String {
        var charLen = buffer.get().toInt() and 0xFF
        if ((charLen and 0x80) != 0) {
            charLen = ((charLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        var byteLen = buffer.get().toInt() and 0xFF
        if ((byteLen and 0x80) != 0) {
            byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        val safeLen = byteLen.coerceAtMost(buffer.remaining()).coerceAtLeast(0)
        val b = ByteArray(safeLen)
        buffer.get(b)
        return String(b, Charsets.UTF_8)
    }

    private fun readUtf16(buffer: ByteBuffer, bytes: ByteArray): String {
        var charLen = buffer.short.toInt() and 0xFFFF
        if ((charLen and 0x8000) != 0) {
            charLen = ((charLen and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
        }
        val byteLen = (charLen * 2).coerceAtMost(buffer.remaining()).coerceAtLeast(0)
        val b = ByteArray(byteLen)
        buffer.get(b)
        return String(b, Charsets.UTF_16LE)
    }

    private fun decodeUtf16ZeroTerminated(bytes: ByteArray): String {
        val chars = CharArray(bytes.size / 2)
        var length = 0
        for (i in 0 until bytes.size step 2) {
            val code = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
            if (code == 0) break
            chars[length++] = code.toChar()
        }
        return String(chars, 0, length)
    }
}
