package com.example.apk.inspection

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.example.apk.batch.BatchItem
import com.example.apk.batch.BatchItemStatus
import com.example.apk.diff.ApkDiffEngine
import com.example.apk.diff.DiffChangeType
import com.example.apk.export.ScanReportExporter
import com.example.apk.model.*
import com.example.apk.scanner.ApkScanPipeline
import com.example.database.ApkScanDao
import com.example.database.ApkScanEntity
import com.example.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AdvancedWorkflowsAndAutomationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var scanDao: ApkScanDao

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scanDao = database.apkScanDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createDummyScanResult(
        scanId: String = "SCAN-1",
        fileName: String = "test1.apk",
        fileSize: Long = 1048576L,
        pkgName: String = "com.test.one",
        versionCode: Long = 10,
        versionName: String = "1.0",
        dexCount: Int = 1,
        nativeLibs: List<Pair<String, String>> = listOf("arm64-v8a" to "libone.so"),
        arscStrings: List<String> = listOf("App Name", "Settings")
    ): ApkScanResult {
        return ApkScanResult(
            scanId = scanId,
            fileInfo = ApkFileInfo(
                scanId = scanId,
                fileName = fileName,
                fileSize = fileSize,
                sha256 = "sha256-$scanId",
                md5 = "md5-$scanId",
                mimeType = "application/vnd.android.package-archive",
                uri = "content://media/external/$fileName",
                totalZipEntries = 50,
                compressedSize = fileSize / 2,
                uncompressedSize = fileSize,
                compressionRatio = 50.0,
                status = ScanStatus.COMPLETED,
                dexCount = dexCount,
                nativeLibraryCount = nativeLibs.size,
                resourcePresence = true
            ),
            manifestInfo = ManifestInfo(
                packageName = pkgName,
                appName = "Test App",
                versionCode = versionCode,
                versionName = versionName,
                minSdk = 24,
                targetSdk = 34,
                compileSdk = 34,
                permissions = listOf("android.permission.INTERNET"),
                usesFeatures = listOf("android.hardware.camera"),
                activities = listOf(ComponentInfo("Activity", "com.test.MainActivity", "MainActivity", true, null, emptyList())),
                services = emptyList(),
                receivers = emptyList(),
                providers = emptyList(),
                intentFiltersCount = 1,
                exportedComponentsCount = 1,
                isDebuggable = false,
                allowBackup = true,
                usesCleartextTraffic = false,
                networkSecurityConfig = null,
                theme = null,
                supportedArchitectures = listOf("arm64-v8a"),
                rawXmlText = ""
            ),
            dexList = (1..dexCount).map { i ->
                DexInfo(
                    fileName = "classes$i.dex",
                    fileSize = 100000L,
                    sha256 = "dexsha$i",
                    magic = "dex\n",
                    version = "035",
                    adler32Checksum = "123",
                    sha1Signature = "sig",
                    classDefsCount = 120,
                    methodIdsEstimate = 2500,
                    fieldIdsCount = 800,
                    stringIdsCount = 1500,
                    typeIdsCount = 400,
                    protoIdsCount = 300,
                    classNames = listOf("com.test.MyClass$i"),
                    semanticAnalysisStatus = "OK",
                    fileOffset = 0L
                )
            },
            permissionsList = listOf(
                ScanPermissionInfo("android.permission.INTERNET", "INTERNET", ProtectionCategory.NORMAL, RiskLevel.INFO, "Network access", "Access the internet")
            ),
            nativeLibrariesList = nativeLibs.map { (abi, lib) ->
                NativeLibraryInfo(abi = abi, libraryName = lib, fileSize = 50000L, sha256 = "libsha")
            },
            abiCoverage = nativeLibs.map { it.first }.distinct(),
            certificatesList = emptyList(),
            securityFindings = emptyList(),
            status = ScanStatus.COMPLETED,
            completedAt = 1700000000000L,
            resourceStrings = arscStrings
        )
    }

    // ==========================================
    // 1. APK Diff Engine Verification
    // ==========================================

    @Test
    fun testApkDiffEngine_DetectsVersionAndSizeChanges() {
        val apk1 = createDummyScanResult(
            scanId = "SCAN-V1",
            fileName = "app-v1.apk",
            fileSize = 2_000_000L,
            versionCode = 10,
            versionName = "1.0.0",
            dexCount = 1,
            nativeLibs = listOf("arm64-v8a" to "libcrypto.so")
        )
        val apk2 = createDummyScanResult(
            scanId = "SCAN-V2",
            fileName = "app-v2.apk",
            fileSize = 2_500_000L,
            versionCode = 11,
            versionName = "1.1.0",
            dexCount = 2,
            nativeLibs = listOf("arm64-v8a" to "libcrypto.so", "armeabi-v7a" to "libcrypto.so")
        )

        val diff = ApkDiffEngine.compare(apk1, apk2)

        assertEquals("app-v1.apk", diff.apk1Name)
        assertEquals("app-v2.apk", diff.apk2Name)
        assertEquals(500_000L, diff.sizeDeltaBytes)
        assertEquals("1.0.0", diff.versionNameDiff.oldValue)
        assertEquals("1.1.0", diff.versionNameDiff.newValue)
        assertEquals(10L, diff.versionCodeDiff.oldValue)
        assertEquals(11L, diff.versionCodeDiff.newValue)
        assertEquals(DiffChangeType.MODIFIED, diff.dexCountDiff.changeType)
        assertEquals(1, diff.totalAddedNativeLibs)
        assertEquals(0, diff.totalRemovedNativeLibs)
    }

    @Test
    fun testApkDiffEngine_IdenticalApks_HasZeroDelta() {
        val apk = createDummyScanResult(scanId = "SAME", fileName = "same.apk")
        val diff = ApkDiffEngine.compare(apk, apk)

        assertEquals(0L, diff.sizeDeltaBytes)
        assertEquals(0, diff.totalAddedPermissions)
        assertEquals(0, diff.totalRemovedPermissions)
        assertEquals(0, diff.totalAddedNativeLibs)
        assertEquals(0, diff.totalRemovedNativeLibs)
    }

    // ==========================================
    // 2. Batch Scan Item State & Serialization
    // ==========================================

    @Test
    fun testBatchScanItem_StatusTransitions() {
        val dummyUri = Uri.parse("content://media/external/downloads/sample.apk")
        val item = BatchItem(
            id = "batch-1",
            uri = dummyUri,
            fileName = "sample.apk",
            fileSize = 1024L,
            status = BatchItemStatus.PENDING
        )
        assertEquals(BatchItemStatus.PENDING, item.status)
        assertNull(item.scanResult)

        val running = item.copy(status = BatchItemStatus.SCANNING)
        assertEquals(BatchItemStatus.SCANNING, running.status)

        val completedResult = createDummyScanResult(scanId = "batch-1", fileName = "sample.apk")
        val finished = running.copy(status = BatchItemStatus.COMPLETED, scanResult = completedResult)
        assertEquals(BatchItemStatus.COMPLETED, finished.status)
        assertNotNull(finished.scanResult)
    }

    // ==========================================
    // 3. Room Database Sort & Query Execution
    // ==========================================

    @Test
    fun testRoomDatabase_InsertAndQueryScans(): Unit = runBlocking {
        val entity1 = ApkScanEntity(
            scanId = "SCAN-01",
            fileName = "alpha.apk",
            fileSize = 1000L,
            sha256 = "sha01",
            md5 = "md501",
            timestamp = 1000L,
            status = "COMPLETED",
            manifestSummary = "Manifest 1",
            dexCount = 1,
            abiSummary = "arm64-v8a",
            certificateSummary = "CN=Test",
            securityFindingCount = 1,
            permissionCount = 2,
            packageName = "com.example.alpha"
        )
        val entity2 = ApkScanEntity(
            scanId = "SCAN-02",
            fileName = "beta.apk",
            fileSize = 5000L,
            sha256 = "sha02",
            md5 = "md502",
            timestamp = 2000L,
            status = "COMPLETED",
            manifestSummary = "Manifest 2",
            dexCount = 2,
            abiSummary = "Pure Java / DEX",
            certificateSummary = "CN=Test",
            securityFindingCount = 0,
            permissionCount = 0,
            packageName = "com.example.beta"
        )

        scanDao.insertScan(entity1)
        scanDao.insertScan(entity2)

        val retrieved1 = scanDao.getScanById("SCAN-01")
        assertNotNull(retrieved1)
        assertEquals("alpha.apk", retrieved1?.fileName)
        assertEquals(1000L, retrieved1?.fileSize)

        val retrieved2 = scanDao.getScanById("SCAN-02")
        assertNotNull(retrieved2)
        assertEquals("beta.apk", retrieved2?.fileName)

        // Test delete
        scanDao.deleteScan("SCAN-01")
        assertNull(scanDao.getScanById("SCAN-01"))
        assertNotNull(scanDao.getScanById("SCAN-02"))
    }

    // ==========================================
    // 4. Report Sanitization & Export Security
    // ==========================================

    @Test
    fun testReportExporter_SanitizesSensitiveLocalPaths() {
        val scanWithSensitivePath = ApkScanResult(
            scanId = "SEC-TEST",
            fileInfo = ApkFileInfo(
                scanId = "SEC-TEST",
                fileName = "secret_bank.apk",
                fileSize = 500000L,
                sha256 = "hash123",
                md5 = "md5123",
                mimeType = "application/vnd.android.package-archive",
                uri = "file:///data/user/0/com.example/cache/apk_scans/SEC-TEST.apk",
                status = ScanStatus.COMPLETED
            ),
            manifestInfo = ManifestInfo(
                packageName = "com.example.bank",
                appName = "Bank",
                versionCode = 1,
                versionName = "1.0",
                minSdk = 24,
                targetSdk = 34,
                compileSdk = 34,
                permissions = emptyList(),
                usesFeatures = emptyList(),
                activities = emptyList(),
                services = emptyList(),
                receivers = emptyList(),
                providers = emptyList(),
                intentFiltersCount = 0,
                exportedComponentsCount = 0,
                isDebuggable = false,
                allowBackup = true,
                usesCleartextTraffic = false,
                networkSecurityConfig = null,
                theme = null,
                supportedArchitectures = emptyList(),
                rawXmlText = ""
            ),
            dexList = emptyList(),
            permissionsList = emptyList(),
            nativeLibrariesList = emptyList(),
            abiCoverage = emptyList(),
            certificatesList = emptyList(),
            securityFindings = emptyList(),
            status = ScanStatus.COMPLETED
        )

        val exportedJson = ScanReportExporter.generateJsonReport(scanWithSensitivePath)
        assertFalse("Exported JSON must not expose internal data paths", exportedJson.contains("/data/user/0/com.example"))
        assertTrue("Exported JSON must contain sanitized filename instead", exportedJson.contains("secret_bank.apk"))

        val exportedMarkdown = ScanReportExporter.generateMarkdownReport(scanWithSensitivePath)
        assertFalse(exportedMarkdown.contains("/data/user/0/com.example"))
        assertTrue(exportedMarkdown.contains("secret_bank.apk"))
    }

    // ==========================================
    // 5. Sandbox Cleanup & Auto-Purge Verification
    // ==========================================

    @Test
    fun testSandboxPurge_RemovesStaleFilesAndKeepsFreshOnes() {
        val tempDir = File(context.cacheDir, "test_sandbox_purge").apply { mkdirs() }
        val staleFile = File(tempDir, "stale_scan.apk").apply {
            writeBytes(ByteArray(128))
            setLastModified(System.currentTimeMillis() - 48 * 60 * 60 * 1000L) // 48h old
        }
        val freshFile = File(tempDir, "fresh_scan.apk").apply {
            writeBytes(ByteArray(128))
            setLastModified(System.currentTimeMillis()) // Brand new
        }

        assertTrue(staleFile.exists())
        assertTrue(freshFile.exists())

        val purged = ApkScanPipeline.purgeStaleSandboxFiles(tempDir, maxAgeMs = 24 * 60 * 60 * 1000L)
        assertEquals(1, purged)
        assertFalse("Stale file should be deleted", staleFile.exists())
        assertTrue("Fresh file should remain intact", freshFile.exists())

        // Cleanup test directory
        tempDir.deleteRecursively()
    }
}
