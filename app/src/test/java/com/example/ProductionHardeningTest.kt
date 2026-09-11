package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.apk.model.CustomAssetItem
import com.example.apk.model.RebuildConfig
import com.example.apk.rebuilder.ApkRebuildPipeline
import com.example.apk.scanner.ApkScanPipeline
import com.example.apk.scanner.ApkScannerService
import com.example.apk.signing.SigningManager
import com.example.core.CryptoUtils
import com.example.core.SecurityManager
import com.example.database.AppDatabase
import com.example.database.BuildHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductionHardeningTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var scannerService: ApkScannerService
    private lateinit var scanPipeline: ApkScanPipeline
    private lateinit var signingManager: SigningManager
    private lateinit var rebuildPipeline: ApkRebuildPipeline

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        scannerService = ApkScannerService(context)
        scanPipeline = ApkScanPipeline(database.apkScanDao())
        signingManager = SigningManager(context)
        rebuildPipeline = ApkRebuildPipeline(context, scannerService, signingManager)
    }

    @Test
    fun testRejectMissingApkFile() {
        runBlocking {
            val nonExistentFile = File(context.cacheDir, "does_not_exist_${UUID.randomUUID()}.apk")
            val result = scanPipeline.scanFromFile(nonExistentFile)
            assertTrue("Scan should fail on non-existent file", result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull(exception)
            assertTrue(exception?.message?.contains("does not exist") == true)
        }
    }

    @Test
    fun testRejectZeroByteApkFile() {
        runBlocking {
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
    }

    @Test
    fun testRejectCorruptedZipArchive() {
        runBlocking {
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
    }

    @Test
    fun testAcceptValidApkFileAndInspectAllComponents() {
        runBlocking {
            val validApk = scannerService.generateSampleApk(
                appName = "HardeningTestApp",
                packageName = "com.workbench.hardening"
            )
            try {
                // 1. Scan pipeline execution
                val result = scanPipeline.scanFromFile(validApk)
                assertTrue("Scan should succeed on valid generated APK", result.isSuccess)
                val scanData = result.getOrNull()
                assertNotNull(scanData)

                // 2. Structural & manifest checks
                assertEquals("com.workbench.hardening", scanData?.manifestInfo?.packageName)
                assertEquals("HardeningTestApp", scanData?.manifestInfo?.appName)
                assertTrue(scanData!!.fileInfo.sha256.isNotEmpty())
                assertTrue(scanData.fileInfo.fileSize > 0)
                assertTrue(scanData.fileInfo.dexCount >= 1)
                assertTrue(scanData.dexList.isNotEmpty())
                assertTrue(scanData.permissionsList.isNotEmpty())

                // 3. Direct SHA-256 calculation match
                val directSha = CryptoUtils.calculateSha256(validApk)
                assertEquals(directSha, scanData.fileInfo.sha256)

                // 4. Archive inspection
                var foundManifest = false
                var foundDex = false
                ZipFile(validApk).use { zip ->
                    foundManifest = zip.getEntry("AndroidManifest.xml") != null
                    foundDex = zip.getEntry("classes.dex") != null
                }
                assertTrue(foundManifest)
                assertTrue(foundDex)
            } finally {
                validApk.delete()
            }
        }
    }

    @Test
    fun testRebuildPipelineExecutionAndOutputVerification() {
        runBlocking {
            val sourceApk = scannerService.generateSampleApk(
                appName = "OriginalApp",
                packageName = "com.workbench.orig"
            )
            try {
                val apkInfo = scannerService.scanApk(sourceApk)
                val availableKeys = signingManager.listKeys()
                assertTrue(availableKeys.isNotEmpty())
                val keyAlias = availableKeys.first().alias

                val config = RebuildConfig(
                    newAppName = "HardenedRebuiltApp",
                    newVersionName = "3.1.0",
                    newVersionCode = 310,
                    newMinSdk = 24,
                    newTargetSdk = 35,
                    customAssets = listOf(CustomAssetItem("security/build_meta.json", "{\"hardened\":true}")),
                    keyAlias = keyAlias
                )

                val plan = rebuildPipeline.createBuildPlan(apkInfo, config)
                val buildResult = rebuildPipeline.executeRebuild(sourceApk, config, plan)

                assertTrue("Rebuild must succeed", buildResult.isSuccess)
                assertNotNull(buildResult.outputApkFile)

                val outputApk = buildResult.outputApkFile!!
                assertTrue("Output APK must exist", outputApk.exists())
                assertTrue("Output APK size must be > 0", outputApk.length() > 0)

                // Direct SHA-256 validation
                val directSha = CryptoUtils.calculateSha256(outputApk)
                assertEquals(directSha, buildResult.outputSha256)

                // Zip structure validation
                var hasAsset = false
                var hasCertSf = false
                var hasCertRsa = false
                ZipFile(outputApk).use { zip ->
                    hasAsset = zip.getEntry("assets/security/build_meta.json") != null
                    hasCertSf = zip.getEntry("META-INF/CERT.SF") != null
                    hasCertRsa = zip.getEntry("META-INF/CERT.RSA") != null
                }
                assertTrue("Injected asset must exist", hasAsset)
                assertTrue("CERT.SF must exist", hasCertSf)
                assertTrue("CERT.RSA must exist", hasCertRsa)

                // Signature verification
                val sigResult = signingManager.verifyApk(outputApk)
                assertTrue("Signature must be valid", sigResult.isValid)
                assertEquals("APK Signature Scheme v1 (JAR)", sigResult.signingScheme)

                // Persistence check
                val historyEntity = BuildHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    projectName = "HardenedRebuiltApp",
                    buildType = "REBUILD",
                    isSuccess = true,
                    durationMs = buildResult.durationMs,
                    outputApkPath = outputApk.absolutePath,
                    outputSha256 = directSha,
                    logSummary = "Verified Hardened Rebuild"
                )
                database.buildHistoryDao().insertBuildHistory(historyEntity)
                val historyList = database.buildHistoryDao().getAllBuildHistory().first()
                val persisted = historyList.firstOrNull { it.id == historyEntity.id }
                assertNotNull(persisted)
                assertEquals(directSha, persisted?.outputSha256)

                outputApk.delete()
            } finally {
                sourceApk.delete()
            }
        }
    }

    @Test
    fun testSecurityManagerPathTraversalProtection() {
        val parentDir = File(context.cacheDir, "safe_dir").apply { mkdirs() }
        val safeFile = File(parentDir, "sub/file.txt")
        val result = SecurityManager.validateSafePath(parentDir, safeFile)
        assertEquals(safeFile.canonicalPath, result.canonicalPath)

        val evilFile = File(parentDir, "../../../etc/passwd")
        try {
            SecurityManager.validateSafePath(parentDir, evilFile)
            fail("Expected SecurityException on path traversal attempt")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Path traversal attempt detected") == true)
        } finally {
            parentDir.deleteRecursively()
        }
    }
}
