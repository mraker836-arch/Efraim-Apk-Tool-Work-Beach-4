package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.apk.scanner.ApkScanPipeline
import com.example.apk.scanner.ApkScannerService
import com.example.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductionHardeningTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var scannerService: ApkScannerService
    private lateinit var scanPipeline: ApkScanPipeline

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        scannerService = ApkScannerService(context)
        scanPipeline = ApkScanPipeline(database.apkScanDao())
    }

    @Test
    fun testRejectZeroByteApkFile() = runBlocking {
        val emptyFile = File(context.cacheDir, "empty_test.apk").apply {
            createNewFile()
        }
        try {
            val result = scanPipeline.scanFromFile(emptyFile)
            assertTrue("Scan should fail on empty file", result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue("Error message should identify empty/0 bytes file", exception?.message?.contains("0 bytes") == true)
        } finally {
            emptyFile.delete()
        }
    }

    @Test
    fun testRejectCorruptedZipArchive() = runBlocking {
        val corruptFile = File(context.cacheDir, "corrupt_test.apk").apply {
            writeBytes("THIS IS NOT A VALID ZIP ARCHIVE".toByteArray())
        }
        try {
            val result = scanPipeline.scanFromFile(corruptFile)
            assertTrue("Scan should fail on non-zip file", result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue("Error should mention magic header mismatch", exception?.message?.contains("magic header") == true)
        } finally {
            corruptFile.delete()
        }
    }

    @Test
    fun testAcceptValidApkFile() = runBlocking {
        val validApk = scannerService.generateSampleApk(
            appName = "HardeningTestApp",
            packageName = "com.workbench.hardening"
        )
        try {
            val result = scanPipeline.scanFromFile(validApk)
            assertTrue("Scan should succeed on valid generated APK", result.isSuccess)
            val scanData = result.getOrNull()
            assertNotNull(scanData)
            assertEquals("com.workbench.hardening", scanData?.manifestInfo?.packageName)
            assertTrue(scanData!!.fileInfo.sha256.isNotEmpty())
            assertTrue(scanData.fileInfo.fileSize > 0)
        } finally {
            validApk.delete()
        }
    }
}
