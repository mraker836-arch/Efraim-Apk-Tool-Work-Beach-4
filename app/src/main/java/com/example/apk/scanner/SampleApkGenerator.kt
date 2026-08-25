package com.example.apk.scanner

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SampleApkGenerator {

    fun buildBinaryManifest(
        packageName: String = "com.example.sampledemo",
        appName: String = "DemoSampleApp",
        versionName: String = "1.0.0",
        versionCode: Long = 100,
        minSdk: Int = 24,
        targetSdk: Int = 35
    ): ByteArray {
        val strings = listOf(
            "manifest",
            "package",
            packageName,
            "versionCode",
            versionCode.toString(),
            "versionName",
            versionName,
            "uses-sdk",
            "minSdkVersion",
            minSdk.toString(),
            "targetSdkVersion",
            targetSdk.toString(),
            "uses-permission",
            "name",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "application",
            "label",
            appName,
            "allowBackup",
            "true",
            "debuggable",
            "false",
            "activity",
            "com.example.sampledemo.MainActivity",
            "exported"
        )

        val baos = ByteArrayOutputStream()

        // String pool chunk
        val strPoolBaos = ByteArrayOutputStream()
        val strOffsets = mutableListOf<Int>()
        val strDataBaos = ByteArrayOutputStream()

        for (s in strings) {
            val utf8 = s.toByteArray(Charsets.UTF_8)
            strOffsets.add(strDataBaos.size())
            strDataBaos.write(utf8.size and 0x7F) // len
            strDataBaos.write(utf8.size and 0x7F) // byte len
            strDataBaos.write(utf8)
            strDataBaos.write(0) // null terminator
        }

        val poolHeaderSize = 28
        val strOffsetsSize = strings.size * 4
        val stringsStart = poolHeaderSize + strOffsetsSize
        val poolChunkSize = stringsStart + strDataBaos.size()

        val poolHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        poolHeader.putShort(0x0001.toShort()) // RES_STRING_POOL_TYPE
        poolHeader.putShort(28.toShort()) // header size
        poolHeader.putInt(poolChunkSize)
        poolHeader.putInt(strings.size)
        poolHeader.putInt(0) // style count
        poolHeader.putInt(1 shl 8) // UTF-8 flag
        poolHeader.putInt(stringsStart)
        poolHeader.putInt(0) // styles start

        strPoolBaos.write(poolHeader.array())
        for (off in strOffsets) {
            val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(off).array()
            strPoolBaos.write(buf)
        }
        strPoolBaos.write(strDataBaos.toByteArray())

        // Assemble full XML body
        val bodyBaos = ByteArrayOutputStream()
        bodyBaos.write(strPoolBaos.toByteArray())

        // Elements
        fun writeElement(tagIdx: Int, attrs: List<Triple<Int, Int, Int>>) {
            val chunkSize = 36 + attrs.size * 20
            val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(0x0102.toShort()) // RES_XML_START_ELEMENT_TYPE
            buf.putShort(16.toShort())
            buf.putInt(chunkSize)
            buf.putInt(1) // line
            buf.putInt(-1) // comment
            buf.putInt(-1) // ns
            buf.putInt(tagIdx)
            buf.putShort(20.toShort()) // attrStart
            buf.putShort(20.toShort()) // attrSize
            buf.putShort(attrs.size.toShort())
            buf.putShort(0.toShort())
            buf.putShort(0.toShort())
            buf.putShort(0.toShort())

            for (attr in attrs) {
                buf.putInt(-1) // ns
                buf.putInt(attr.first) // nameIdx
                buf.putInt(attr.second) // rawValueIdx
                buf.putInt((0x03 shl 24)) // TYPE_STRING
                buf.putInt(attr.third) // data
            }
            bodyBaos.write(buf.array())

            // End element
            val endBuf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            endBuf.putShort(0x0103.toShort()) // RES_XML_END_ELEMENT_TYPE
            endBuf.putShort(16.toShort())
            endBuf.putInt(24)
            endBuf.putInt(1)
            endBuf.putInt(-1)
            endBuf.putInt(-1)
            endBuf.putInt(tagIdx)
            bodyBaos.write(endBuf.array())
        }

        // manifest element
        writeElement(0, listOf(
            Triple(1, 2, 2), // package
            Triple(3, 4, versionCode.toInt()), // versionCode
            Triple(5, 6, 6) // versionName
        ))

        // uses-sdk
        writeElement(7, listOf(
            Triple(8, 9, minSdk), // minSdk
            Triple(10, 11, targetSdk) // targetSdk
        ))

        // permission 1
        writeElement(12, listOf(Triple(13, 14, 14)))
        // permission 2
        writeElement(12, listOf(Triple(13, 15, 15)))

        // application
        writeElement(16, listOf(
            Triple(17, 18, 18), // label
            Triple(19, 20, 1), // allowBackup
            Triple(21, 22, 0) // debuggable
        ))

        // activity
        writeElement(23, listOf(
            Triple(13, 24, 24), // name
            Triple(25, 20, 1) // exported
        ))

        // Full XML Header
        val totalSize = 8 + bodyBaos.size()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0x0003.toShort()) // RES_XML_TYPE
        header.putShort(8.toShort())
        header.putInt(totalSize)

        baos.write(header.array())
        baos.write(bodyBaos.toByteArray())

        return baos.toByteArray()
    }

    fun buildSampleDex(): ByteArray {
        val size = 0x70 + 64
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        // DEX magic
        buf.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        buf.putInt(0x12345678) // checksum
        buf.put(ByteArray(20) { 0x01 }) // signature
        buf.putInt(size) // file size
        buf.putInt(0x70) // header size
        buf.putInt(0x12345678) // endian tag
        buf.putInt(0) // link size
        buf.putInt(0) // link off
        buf.putInt(0) // map off
        buf.putInt(4) // string ids size
        buf.putInt(0x70) // string ids off
        buf.putInt(2) // type ids size
        buf.putInt(0x70) // type ids off
        buf.putInt(1) // proto ids size
        buf.putInt(0x70) // proto ids off
        buf.putInt(1) // field ids size
        buf.putInt(0x70) // field ids off
        buf.putInt(3) // method ids size
        buf.putInt(0x70) // method ids off
        buf.putInt(2) // class defs size (2 classes)
        buf.putInt(0x70) // class defs off
        buf.putInt(0) // data size
        buf.putInt(0x70) // data off

        return buf.array()
    }
}
