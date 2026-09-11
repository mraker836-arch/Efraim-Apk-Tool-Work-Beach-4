package com.example.apk.scanner

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High performance, zero-dependency Android Binary XML (AXML) decoder.
 * Decodes AndroidManifest.xml from raw compiled APK binary XML format.
 */
class AxmlParser {

    data class ComponentDetail(
        val type: String,
        val name: String,
        val exported: Boolean,
        val permission: String? = null,
        val intentActions: List<String> = emptyList(),
        val intentCategories: List<String> = emptyList(),
        val enabled: Boolean = true
    )

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
        val usesCleartextTraffic: Boolean = false,
        val networkSecurityConfig: String? = null,
        val theme: String? = null,
        val permissions: List<String>,
        val usesFeatures: List<String> = emptyList(),
        val activities: List<ComponentDetail>,
        val services: List<ComponentDetail>,
        val receivers: List<ComponentDetail>,
        val providers: List<ComponentDetail>,
        val intentFiltersCount: Int = 0,
        val rawXmlText: String
    ) {
        val allComponents: List<ComponentDetail> get() = activities + services + receivers + providers
        val exportedComponentsCount: Int get() = allComponents.count { it.exported }
    }

    fun parse(bytes: ByteArray): ParsedManifest {
        if (bytes.size < 8) {
            return fallbackManifest("Malformed or empty manifest binary")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read header
        val headerType = buffer.short.toInt() and 0xFFFF
        val headerSize = buffer.short.toInt() and 0xFFFF
        val fileSize = buffer.int

        var stringPool: List<String> = emptyList()

        var packageName = ""
        var appName = ""
        var versionName = "Unavailable"
        var versionCode = 1L
        var minSdk = 21
        var targetSdk = 34
        var compileSdk: Int? = null
        var isDebuggable = false
        var allowsBackup = true
        var supportsRtl = true
        var usesCleartextTraffic = false
        var networkSecurityConfig: String? = null
        var theme: String? = null

        val permissions = mutableListOf<String>()
        val usesFeatures = mutableListOf<String>()
        val activities = mutableListOf<ComponentDetail>()
        val services = mutableListOf<ComponentDetail>()
        val receivers = mutableListOf<ComponentDetail>()
        val providers = mutableListOf<ComponentDetail>()

        val xmlBuilder = StringBuilder()

        var currentComponentType: String? = null
        var currentComponentName: String = ""
        var currentComponentExported: Boolean? = null
        var currentComponentEnabled: Boolean = true
        var currentComponentPermission: String? = null
        val currentComponentActions = mutableListOf<String>()
        val currentComponentCategories = mutableListOf<String>()
        var totalIntentFilters = 0

        fun finalizeCurrentComponent() {
            val type = currentComponentType ?: return
            val name = currentComponentName.ifBlank { "Unknown$type" }
            val hasActions = currentComponentActions.isNotEmpty()
            // In Android, if exported is omitted: true if intent filters present, false otherwise
            val isExported = currentComponentExported ?: (hasActions || type == "Activity")
            val detail = ComponentDetail(
                type = type,
                name = name,
                exported = isExported,
                permission = currentComponentPermission,
                intentActions = currentComponentActions.toList(),
                intentCategories = currentComponentCategories.toList(),
                enabled = currentComponentEnabled
            )
            when (type) {
                "Activity" -> activities.add(detail)
                "Service" -> services.add(detail)
                "Receiver" -> receivers.add(detail)
                "Provider" -> providers.add(detail)
            }
            currentComponentType = null
            currentComponentName = ""
            currentComponentExported = null
            currentComponentEnabled = true
            currentComponentPermission = null
            currentComponentActions.clear()
            currentComponentCategories.clear()
        }

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
                0x0180, 0x0080 -> { // Resource IDs map
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
                            when (attrType) {
                                0x03 -> stringPool.getOrNull(attrData) ?: attrData.toString()
                                0x12 -> if (attrData != 0) "true" else "false"
                                0x10, 0x11 -> attrData.toString()
                                0x01 -> "@0x${Integer.toHexString(attrData)}"
                                0x02 -> "?0x${Integer.toHexString(attrData)}"
                                else -> attrData.toString()
                            }
                        }

                        attributes[attrName] = attrValue
                        xmlBuilder.append(" $attrName=\"$attrValue\"")
                    }
                    xmlBuilder.append(">\n")

                    // Extract core manifest properties
                    when (tagName) {
                        "manifest" -> {
                            attributes["package"]?.let { if (it.isNotBlank()) packageName = it }
                            attributes["versionName"]?.let { if (it.isNotBlank()) versionName = it }
                            attributes["versionCode"]?.toLongOrNull()?.let { if (it > 0) versionCode = it }
                            attributes["compileSdkVersion"]?.toIntOrNull()?.let { if (it > 0) compileSdk = it }
                        }
                        "uses-sdk" -> {
                            attributes["minSdkVersion"]?.toIntOrNull()?.let { if (it > 0) minSdk = it }
                            attributes["targetSdkVersion"]?.toIntOrNull()?.let { if (it > 0) targetSdk = it }
                        }
                        "application" -> {
                            attributes["label"]?.let { if (it.isNotBlank()) appName = it }
                            attributes["debuggable"]?.let { isDebuggable = it.equals("true", ignoreCase = true) || it == "1" }
                            attributes["allowBackup"]?.let { allowsBackup = it.equals("true", ignoreCase = true) || it == "1" }
                            attributes["supportsRtl"]?.let { supportsRtl = it.equals("true", ignoreCase = true) || it == "1" }
                            attributes["usesCleartextTraffic"]?.let { usesCleartextTraffic = it.equals("true", ignoreCase = true) || it == "1" }
                            attributes["networkSecurityConfig"]?.let { networkSecurityConfig = it }
                            attributes["theme"]?.let { theme = it }
                        }
                        "uses-permission", "uses-permission-sdk-23" -> {
                            val perm = attributes["name"] ?: attributes["permission"]
                            if (!perm.isNullOrBlank()) permissions.add(perm)
                        }
                        "uses-feature" -> {
                            val feat = attributes["name"] ?: attributes["feature"]
                            if (!feat.isNullOrBlank()) {
                                val req = attributes["required"]?.let { if (it == "false") " (optional)" else " (required)" } ?: " (required)"
                                usesFeatures.add("$feat$req")
                            }
                        }
                        "activity", "activity-alias" -> {
                            finalizeCurrentComponent()
                            currentComponentType = "Activity"
                            currentComponentName = attributes["name"] ?: ""
                            currentComponentExported = attributes["exported"]?.let { it.equals("true", ignoreCase = true) || it == "1" }
                            currentComponentEnabled = attributes["enabled"]?.let { it.equals("true", ignoreCase = true) || it == "1" } ?: true
                            currentComponentPermission = attributes["permission"]
                        }
                        "service" -> {
                            finalizeCurrentComponent()
                            currentComponentType = "Service"
                            currentComponentName = attributes["name"] ?: ""
                            currentComponentExported = attributes["exported"]?.let { it.equals("true", ignoreCase = true) || it == "1" }
                            currentComponentEnabled = attributes["enabled"]?.let { it.equals("true", ignoreCase = true) || it == "1" } ?: true
                            currentComponentPermission = attributes["permission"]
                        }
                        "receiver" -> {
                            finalizeCurrentComponent()
                            currentComponentType = "Receiver"
                            currentComponentName = attributes["name"] ?: ""
                            currentComponentExported = attributes["exported"]?.let { it.equals("true", ignoreCase = true) || it == "1" }
                            currentComponentEnabled = attributes["enabled"]?.let { it.equals("true", ignoreCase = true) || it == "1" } ?: true
                            currentComponentPermission = attributes["permission"]
                        }
                        "provider" -> {
                            finalizeCurrentComponent()
                            currentComponentType = "Provider"
                            currentComponentName = attributes["name"] ?: ""
                            currentComponentExported = attributes["exported"]?.let { it.equals("true", ignoreCase = true) || it == "1" }
                            currentComponentEnabled = attributes["enabled"]?.let { it.equals("true", ignoreCase = true) || it == "1" } ?: true
                            currentComponentPermission = attributes["permission"]
                        }
                        "intent-filter" -> {
                            totalIntentFilters++
                        }
                        "action" -> {
                            val actionName = attributes["name"]
                            if (!actionName.isNullOrBlank()) {
                                currentComponentActions.add(actionName)
                            }
                        }
                        "category" -> {
                            val catName = attributes["name"]
                            if (!catName.isNullOrBlank()) {
                                currentComponentCategories.add(catName)
                            }
                        }
                    }

                    buffer.position(chunkStart + chunkSize)
                }
                0x0103 -> { // End Element
                    val lineNumber = buffer.int
                    val commentIdx = buffer.int
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    val tagName = stringPool.getOrNull(nameIdx) ?: "unknown"
                    xmlBuilder.append("</$tagName>\n")

                    if (tagName in listOf("activity", "activity-alias", "service", "receiver", "provider")) {
                        finalizeCurrentComponent()
                    }

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

        finalizeCurrentComponent()

        if (appName.isBlank()) {
            appName = if (packageName.isNotBlank()) packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() } else "Unavailable"
        }

        return ParsedManifest(
            packageName = if (packageName.isNotBlank()) packageName else "Unavailable",
            appName = appName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            isDebuggable = isDebuggable,
            allowsBackup = allowsBackup,
            supportsRtl = supportsRtl,
            usesCleartextTraffic = usesCleartextTraffic,
            networkSecurityConfig = networkSecurityConfig,
            theme = theme,
            permissions = permissions.distinct(),
            usesFeatures = usesFeatures.distinct(),
            activities = activities.distinctBy { it.name },
            services = services.distinctBy { it.name },
            receivers = receivers.distinctBy { it.name },
            providers = providers.distinctBy { it.name },
            intentFiltersCount = totalIntentFilters,
            rawXmlText = xmlBuilder.toString()
        )
    }

    private fun fallbackManifest(reason: String): ParsedManifest {
        return ParsedManifest(
            packageName = "Unavailable",
            appName = "Unavailable",
            versionName = "Unavailable",
            versionCode = 0L,
            minSdk = 0,
            targetSdk = 0,
            compileSdk = null,
            isDebuggable = false,
            allowsBackup = false,
            supportsRtl = false,
            usesCleartextTraffic = false,
            networkSecurityConfig = null,
            theme = null,
            permissions = emptyList(),
            usesFeatures = emptyList(),
            activities = emptyList(),
            services = emptyList(),
            receivers = emptyList(),
            providers = emptyList(),
            intentFiltersCount = 0,
            rawXmlText = "<!-- $reason -->"
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
            if (offset >= chunkStart + chunkSize || offset < 0) {
                strings.add("")
                continue
            }
            buffer.position(offset)
            val str = try {
                if (isUtf8) {
                    readUtf8String(buffer)
                } else {
                    readUtf16String(buffer)
                }
            } catch (_: Exception) {
                ""
            }
            strings.add(str)
        }
        return strings
    }

    private fun readUtf8String(buffer: ByteBuffer): String {
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
