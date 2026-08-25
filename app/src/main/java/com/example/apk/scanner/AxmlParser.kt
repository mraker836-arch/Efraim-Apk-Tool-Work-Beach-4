package com.example.apk.scanner

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High performance, zero-dependency Android Binary XML (AXML) decoder and editor helper.
 */
class AxmlParser {

    data class ParsedManifest(
        val packageName: String,
        val appName: String,
        val versionName: String,
        val versionCode: Long,
        val minSdk: Int,
        val targetSdk: Int,
        val compileSdk: Int?,
        val isDebuggable: Boolean,
        val allowsBackup: Boolean,
        val supportsRtl: Boolean,
        val permissions: List<String>,
        val activities: List<String>,
        val services: List<String>,
        val receivers: List<String>,
        val providers: List<String>,
        val rawXmlText: String
    )

    fun parse(bytes: ByteArray): ParsedManifest {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read header
        val headerType = buffer.short.toInt() and 0xFFFF
        val headerSize = buffer.short.toInt() and 0xFFFF
        val fileSize = buffer.int

        var stringPool: List<String> = emptyList()

        var packageName = ""
        var appName = ""
        var versionName = "1.0"
        var versionCode = 1L
        var minSdk = 21
        var targetSdk = 34
        var compileSdk: Int? = null
        var isDebuggable = false
        var allowsBackup = true
        var supportsRtl = true

        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()
        val services = mutableListOf<String>()
        val receivers = mutableListOf<String>()
        val providers = mutableListOf<String>()

        val xmlBuilder = StringBuilder()
        var currentElement = ""

        while (buffer.hasRemaining()) {
            val chunkStart = buffer.position()
            if (chunkStart + 8 > bytes.size) break

            val chunkType = buffer.short.toInt() and 0xFFFF
            val chunkHeaderSize = buffer.short.toInt() and 0xFFFF
            val chunkSize = buffer.int

            if (chunkSize <= 0 || chunkStart + chunkSize > bytes.size) {
                break
            }

            when (chunkType) {
                0x0001 -> { // String Pool
                    stringPool = parseStringPool(buffer, chunkStart, chunkSize)
                    buffer.position(chunkStart + chunkSize)
                }
                0x0180 -> { // Resource IDs map
                    buffer.position(chunkStart + chunkSize)
                }
                0x0100 -> { // Start Namespace
                    buffer.position(chunkStart + chunkSize)
                }
                0x0101 -> { // End Namespace
                    buffer.position(chunkStart + chunkSize)
                }
                0x0102 -> { // Start Element
                    val lineNumber = buffer.int
                    val commentIdx = buffer.int
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    val attrStart = buffer.short.toInt() and 0xFFFF
                    val attrSize = buffer.short.toInt() and 0xFFFF
                    val attrCount = buffer.short.toInt() and 0xFFFF
                    val idIndex = buffer.short.toInt() and 0xFFFF
                    val classIndex = buffer.short.toInt() and 0xFFFF
                    val styleIndex = buffer.short.toInt() and 0xFFFF

                    val tagName = stringPool.getOrNull(nameIdx) ?: "unknown"
                    currentElement = tagName
                    xmlBuilder.append("<$tagName")

                    val attributes = mutableMapOf<String, String>()

                    for (i in 0 until attrCount) {
                        val attrNsIdx = buffer.int
                        val attrNameIdx = buffer.int
                        val attrRawValueIdx = buffer.int
                        val attrType = (buffer.int ushr 24)
                        val attrData = buffer.int

                        val attrName = stringPool.getOrNull(attrNameIdx) ?: "attr_$i"
                        val attrValue = if (attrRawValueIdx >= 0 && attrRawValueIdx < stringPool.size) {
                            stringPool[attrRawValueIdx]
                        } else {
                            attrData.toString()
                        }

                        attributes[attrName] = attrValue
                        xmlBuilder.append(" $attrName=\"$attrValue\"")

                        // Extract core manifest properties
                        when (tagName) {
                            "manifest" -> {
                                if (attrName == "package") packageName = attrValue
                                if (attrName == "versionName") versionName = attrValue
                                if (attrName == "versionCode") versionCode = attrData.toLong().let { if (it > 0) it else (attrValue.toLongOrNull() ?: 1L) }
                                if (attrName == "compileSdkVersion") compileSdk = attrData.let { if (it > 0) it else (attrValue.toIntOrNull()) }
                            }
                            "uses-sdk" -> {
                                if (attrName == "minSdkVersion") minSdk = attrData.let { if (it > 0) it else (attrValue.toIntOrNull() ?: 21) }
                                if (attrName == "targetSdkVersion") targetSdk = attrData.let { if (it > 0) it else (attrValue.toIntOrNull() ?: 34) }
                            }
                            "application" -> {
                                if (attrName == "label") appName = attrValue
                                if (attrName == "debuggable") isDebuggable = (attrData != 0 || attrValue.equals("true", ignoreCase = true))
                                if (attrName == "allowBackup") allowsBackup = (attrData != 0 || attrValue.equals("true", ignoreCase = true))
                                if (attrName == "supportsRtl") supportsRtl = (attrData != 0 || attrValue.equals("true", ignoreCase = true))
                            }
                            "uses-permission" -> {
                                if (attrName == "name" && attrValue.isNotBlank()) permissions.add(attrValue)
                            }
                            "activity", "activity-alias" -> {
                                if (attrName == "name" && attrValue.isNotBlank()) activities.add(attrValue)
                            }
                            "service" -> {
                                if (attrName == "name" && attrValue.isNotBlank()) services.add(attrValue)
                            }
                            "receiver" -> {
                                if (attrName == "name" && attrValue.isNotBlank()) receivers.add(attrValue)
                            }
                            "provider" -> {
                                if (attrName == "name" && attrValue.isNotBlank()) providers.add(attrValue)
                            }
                        }
                    }
                    xmlBuilder.append(">\n")
                    buffer.position(chunkStart + chunkSize)
                }
                0x0103 -> { // End Element
                    val lineNumber = buffer.int
                    val commentIdx = buffer.int
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    val tagName = stringPool.getOrNull(nameIdx) ?: "unknown"
                    xmlBuilder.append("</$tagName>\n")
                    buffer.position(chunkStart + chunkSize)
                }
                0x0104 -> { // CDATA
                    buffer.position(chunkStart + chunkSize)
                }
                else -> {
                    buffer.position(chunkStart + chunkSize)
                }
            }
        }

        if (appName.isBlank()) {
            appName = packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        }

        return ParsedManifest(
            packageName = if (packageName.isNotBlank()) packageName else "com.example.app",
            appName = appName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            isDebuggable = isDebuggable,
            allowsBackup = allowsBackup,
            supportsRtl = supportsRtl,
            permissions = permissions.distinct(),
            activities = activities.distinct(),
            services = services.distinct(),
            receivers = receivers.distinct(),
            providers = providers.distinct(),
            rawXmlText = xmlBuilder.toString()
        )
    }

    private fun parseStringPool(buffer: ByteBuffer, chunkStart: Int, chunkSize: Int): List<String> {
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val stylesStart = buffer.int

        val isUtf8 = (flags and (1 shl 8)) != 0

        val stringOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            stringOffsets[i] = buffer.int
        }

        val strings = mutableListOf<String>()
        val stringsBase = chunkStart + stringsStart

        for (i in 0 until stringCount) {
            val offset = stringsBase + stringOffsets[i]
            if (offset >= chunkStart + chunkSize) {
                strings.add("")
                continue
            }
            buffer.position(offset)
            val str = if (isUtf8) {
                readUtf8String(buffer)
            } else {
                readUtf16String(buffer)
            }
            strings.add(str)
        }
        return strings
    }

    private fun readUtf8String(buffer: ByteBuffer): String {
        // Read length (length may be encoded in 1 or 2 bytes)
        var charLen = buffer.get().toInt() and 0xFF
        if ((charLen and 0x80) != 0) {
            charLen = ((charLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        var byteLen = buffer.get().toInt() and 0xFF
        if ((byteLen and 0x80) != 0) {
            byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }

        val bytes = ByteArray(byteLen)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16String(buffer: ByteBuffer): String {
        var charLen = buffer.short.toInt() and 0xFFFF
        if ((charLen and 0x8000) != 0) {
            charLen = ((charLen and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
        }
        val chars = CharArray(charLen)
        for (i in 0 until charLen) {
            chars[i] = buffer.char
        }
        return String(chars)
    }
}
