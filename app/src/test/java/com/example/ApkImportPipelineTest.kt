package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.apk.scanner.ApkScannerService
import com.example.ui.apk.scanner.ApkImportErrorCode
import com.example.ui.apk.scanner.ApkImportPipeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkImportPipelineTest {

    private lateinit var context: Context
    private lateinit var pipeline: ApkImportPipeline
    private lateinit var scannerService: ApkScannerService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pipeline = ApkImportPipeline(context)
        scannerService = ApkScannerService(context)
    }

    @Test
    fun testValidApkImport_SuccessAndMetadataExtraction() = runBlocking {
        // Generate a real binary APK with valid manifest, dex, and resources
        val apkFile = scannerService.generateSampleApk(
            appName = "PipelineTestApp",
            packageName = "com.example.pipeline.test"
        )
        assertNotNull("Sample APK generation must produce a file", apkFile)
        assertTrue("Generated APK file must exist", apkFile.exists())

        val result = pipeline.importAndValidateFile(apkFile, "PipelineTestApp.apk")

        assertTrue("Valid APK import must succeed", result.success)
        assertEquals(ApkImportErrorCode.NONE, result.errorCode)
        assertEquals("PipelineTestApp.apk", result.fileName)
        assertTrue("File size must be greater than 0", result.fileSize > 0)
        assertEquals("SHA-256 must be 64 characters hex", 64, result.sha256.length)

        // Metadata extraction verification
        assertNotNull("Package name must not be null", result.packageName)
        assertTrue("Package name must match expected", result.packageName?.contains("pipeline") == true || result.packageName?.contains("test") == true)
        assertNotNull("Version name must be extracted", result.versionName)
        assertNotNull("Version code must be extracted", result.versionCode)

        // Structural entry flags
        assertTrue("Must detect AndroidManifest.xml", result.hasManifest)
        assertTrue("Must detect classes.dex", result.hasDex)

        // Permission extraction verification
        assertTrue("Permissions list must be extracted", result.permissions.isNotEmpty())
        assertTrue("Must extract INTERNET permission", result.permissions.any { it.contains("INTERNET") })

        // Staged file validity
        assertNotNull("Staged file should be available", result.stagedFile)
    }

    @Test
    fun testSignedApkImport_SignaturePresenceDetection() = runBlocking {
        val signingManager = com.example.apk.signing.SigningManager(context)
        val rawApk = scannerService.generateSampleApk(
            appName = "SignedPipelineApp",
            packageName = "com.example.signed.pipeline"
        )
        val signedApkTarget = File(context.cacheDir, "signed_pipeline_test.apk")
        val signedApk = signingManager.signApk(rawApk, signedApkTarget)
        assertTrue("Signed APK must exist on disk", signedApk.exists())

        val result = pipeline.importAndValidateFile(signedApk, "SignedPipelineApp.apk")

        assertTrue("Signed APK import must succeed", result.success)
        assertTrue("Must detect signature presence in signed APK", result.hasSignature)
        assertNotNull("Package name must be extracted", result.packageName)
    }

    @Test
    fun testNonApkRejection_InvalidExtension() = runBlocking {
        val textFile = File(context.cacheDir, "sample_document.txt").apply {
            writeText("This is plain text and definitely not an APK.")
        }

        val result = pipeline.importAndValidateFile(textFile)

        assertFalse("Non-APK file must be rejected", result.success)
        assertEquals(ApkImportErrorCode.INVALID_FILE, result.errorCode)
        assertTrue("Errors must explain the file rejection", result.errors.any { it.contains(".apk") })
    }

    @Test
    fun testCorruptedApkHandling_InvalidZipHeader() = runBlocking {
        // Create an .apk file with arbitrary non-ZIP corrupted binary bytes
        val corruptedFile = File(context.cacheDir, "corrupted_archive.apk").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07))
        }

        val result = pipeline.importAndValidateFile(corruptedFile)

        assertFalse("Corrupted APK without ZIP magic header must be rejected", result.success)
        assertEquals(ApkImportErrorCode.INVALID_APK, result.errorCode)
        assertTrue("Errors must mention invalid archive or magic header", result.errors.any { it.contains("magic header") || it.contains("valid APK") })
    }

    @Test
    fun testMissingManifestDetection_ZipWithoutManifest() = runBlocking {
        // Create a valid ZIP archive with an .apk extension, but omit AndroidManifest.xml
        val noManifestFile = File(context.cacheDir, "no_manifest.apk")
        ZipOutputStream(FileOutputStream(noManifestFile)).use { zipOut ->
            val entry = ZipEntry("classes.dex")
            zipOut.putNextEntry(entry)
            zipOut.write("dummy dex content".toByteArray())
            zipOut.closeEntry()
        }

        val result = pipeline.importAndValidateFile(noManifestFile)

        assertFalse("APK missing AndroidManifest.xml must be rejected", result.success)
        assertEquals(ApkImportErrorCode.MANIFEST_MISSING, result.errorCode)
        assertTrue("Errors must state AndroidManifest.xml is missing", result.errors.any { it.contains("AndroidManifest.xml") })
    }

    @Test
    fun testFileNotFoundHandling() = runBlocking {
        val nonExistentFile = File(context.cacheDir, "does_not_exist_anywhere.apk")
        if (nonExistentFile.exists()) nonExistentFile.delete()

        val result = pipeline.importAndValidateFile(nonExistentFile)

        assertFalse("Non-existent file must fail with FILE_NOT_FOUND", result.success)
        assertEquals(ApkImportErrorCode.FILE_NOT_FOUND, result.errorCode)
    }

    @Test
    fun testEmptyApkHandling() = runBlocking {
        val emptyFile = File(context.cacheDir, "zero_bytes.apk").apply {
            writeBytes(ByteArray(0))
        }

        val result = pipeline.importAndValidateFile(emptyFile)

        assertFalse("Empty APK file must fail with INVALID_FILE", result.success)
        assertEquals(ApkImportErrorCode.INVALID_FILE, result.errorCode)
        assertTrue("Error must mention 0 bytes or empty file", result.errors.any { it.contains("empty") || it.contains("0 bytes") })
    }

    @Test
    fun testUriImportFlow() = runBlocking {
        val apkFile = scannerService.generateSampleApk(
            appName = "UriTestApp",
            packageName = "com.example.uritest"
        )
        val fileUri = Uri.fromFile(apkFile)

        val result = pipeline.importAndValidate(fileUri, "UriTestApp.apk")

        assertTrue("Import via URI must succeed", result.success)
        assertEquals("UriTestApp.apk", result.fileName)
        assertEquals(64, result.sha256.length)
        assertTrue("Must extract package info", result.packageName?.contains("uritest") == true || result.packageName?.isNotBlank() == true)
        assertNotNull("Staged file must be created", result.stagedFile)
        assertTrue("Staged file must exist on disk", result.stagedFile?.exists() == true)
    }

    @Test
    fun testUnsignedApkDetection_WarningsProvided() = runBlocking {
        // Create an APK with manifest and dex, but completely unsigned (no META-INF signatures)
        val unsignedFile = File(context.cacheDir, "unsigned_sample.apk")
        
        // Grab manifest and dex from a real generated sample
        val sampleApk = scannerService.generateSampleApk("UnsignedApp", "com.example.unsigned")
        var manifestBytes: ByteArray? = null
        var dexBytes: ByteArray? = null

        java.util.zip.ZipFile(sampleApk).use { zip ->
            zip.getEntry("AndroidManifest.xml")?.let { entry ->
                manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
            }
            zip.getEntry("classes.dex")?.let { entry ->
                dexBytes = zip.getInputStream(entry).use { it.readBytes() }
            }
        }

        assertNotNull("Sample must have manifest bytes", manifestBytes)

        ZipOutputStream(FileOutputStream(unsignedFile)).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zipOut.write(manifestBytes!!)
            zipOut.closeEntry()

            if (dexBytes != null) {
                zipOut.putNextEntry(ZipEntry("classes.dex"))
                zipOut.write(dexBytes!!)
                zipOut.closeEntry()
            }
        }

        val result = pipeline.importAndValidateFile(unsignedFile)

        assertTrue("Valid structure should still parse manifest", result.success)
        assertFalse("Unsigned APK must report hasSignature as false", result.hasSignature)
        assertTrue("Unsigned APK must produce a warning about missing signature", result.warnings.any { it.contains("Unsigned") || it.contains("signature") })
    }
}
