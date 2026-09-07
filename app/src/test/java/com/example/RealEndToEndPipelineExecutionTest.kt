package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.apk.model.CustomAssetItem
import com.example.apk.model.RebuildConfig
import com.example.apk.rebuilder.ApkRebuildPipeline
import com.example.apk.scanner.ApkScannerService
import com.example.apk.signing.SigningManager
import com.example.core.CryptoUtils
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
class RealEndToEndPipelineExecutionTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var scannerService: ApkScannerService
    private lateinit var signingManager: SigningManager
    private lateinit var rebuildPipeline: ApkRebuildPipeline

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        scannerService = ApkScannerService(context)
        signingManager = SigningManager(context)
        rebuildPipeline = ApkRebuildPipeline(context, scannerService, signingManager)
    }

    @Test
    fun executeRealEndToEndPipelineStages() {
        runBlocking {
            System.err.println("=== STAGE 1: REAL APK INPUT ===")
            val sourceApk = scannerService.generateSampleApk(
                appName = "Stage1SourceApp",
                packageName = "com.workbench.testapp"
            )
            System.err.println("SOURCE APK PATH: ${sourceApk.absolutePath}")
            System.err.println("SOURCE APK SIZE: ${sourceApk.length()} bytes")
            assertTrue(sourceApk.exists())
            assertTrue(sourceApk.length() > 0)

            System.err.println("=== STAGE 2: IMPORT & STAGE 3: STRUCTURAL VALIDATION ===")
            var entryCount = 0
            var hasManifest = false
            var hasDex = false
            ZipFile(sourceApk).use { zip ->
                entryCount = zip.size()
                hasManifest = zip.getEntry("AndroidManifest.xml") != null
                hasDex = zip.getEntry("classes.dex") != null
            }
            System.err.println("ZIP VALID: true, Total entries: $entryCount, HasManifest: $hasManifest, HasDex: $hasDex")
            assertTrue(entryCount > 0)
            assertTrue(hasManifest)
            assertTrue(hasDex)

            System.err.println("=== STAGE 4: SHA-256 INPUT CALCULATION ===")
            val inputSha256 = CryptoUtils.calculateSha256(sourceApk)
            System.err.println("INPUT SHA-256: $inputSha256")
            assertEquals(64, inputSha256.length)

            System.err.println("=== STAGE 5: MANIFEST INSPECTION ===")
            val scanResult = scannerService.scanApk(sourceApk)
            System.err.println("MANIFEST INSPECTION: package=${scanResult.packageName}, app=${scanResult.appName}, version=${scanResult.versionName}(${scanResult.versionCode}), minSdk=${scanResult.minSdk}, targetSdk=${scanResult.targetSdk}")
            System.err.println("MANIFEST PERMISSIONS: ${scanResult.permissions.map { it.name }}")
            assertEquals("com.workbench.testapp", scanResult.packageName)
            assertEquals("Stage1SourceApp", scanResult.appName)

            System.err.println("=== STAGE 6: DEX INSPECTION ===")
            System.err.println("DEX FILES DETECTED: ${scanResult.dexFiles.size}")
            for (dex in scanResult.dexFiles) {
                System.err.println("DEX: name=${dex.name}, size=${dex.sizeBytes} bytes, classDefs=${dex.classDefsCount}, methodsEstimate=${dex.methodIdsEstimate}, version=${dex.dexVersion}")
            }
            assertTrue(scanResult.dexFiles.isNotEmpty())

            System.err.println("=== STAGE 7: NATIVE LIBRARY INSPECTION ===")
            System.err.println("NATIVE LIBRARIES MAP: ${scanResult.nativeLibraries}")
            for ((abi, libs) in scanResult.nativeLibraries) {
                System.err.println("ABI: $abi -> $libs")
            }

            System.err.println("=== STAGE 8: CERTIFICATE / SIGNATURE INSPECTION ===")
            val availableKeys = signingManager.listKeys()
            System.err.println("KEYSTORE KEYS COUNT: ${availableKeys.size}")
            for (key in availableKeys) {
                System.err.println("KEY ALIAS: ${key.alias}, ALGO: ${key.algorithm}, SHA-256: ${key.sha256Fingerprint}, SUBJECT: ${key.subject}")
            }
            val initialSigVerify = signingManager.verifyApk(sourceApk)
            System.err.println("INPUT APK SIGNATURE VERIFICATION: valid=${initialSigVerify.isValid}, scheme=${initialSigVerify.signingScheme}, issues=${initialSigVerify.issues}")

            System.err.println("=== STAGE 9: REAL REBUILD EXECUTION ===")
            val rebuildConfig = RebuildConfig(
                newAppName = "Stage9RebuiltApp",
                newVersionName = "2.0.0",
                newVersionCode = 200,
                newMinSdk = 26,
                newTargetSdk = 35,
                customAssets = listOf(CustomAssetItem("config/rebuild_metadata.json", "{\"rebuilt_by\":\"efraim_pipeline\",\"real\":true}")),
                keyAlias = availableKeys.first().alias
            )
            val plan = rebuildPipeline.createBuildPlan(scanResult, rebuildConfig)
            val buildResult = rebuildPipeline.executeRebuild(sourceApk, rebuildConfig, plan)
            System.err.println("REBUILD SUCCESS: ${buildResult.isSuccess}")
            System.err.println("REBUILD DURATION: ${buildResult.durationMs}ms")
            System.err.println("REBUILD ERROR: ${buildResult.errorMessage}")
            assertTrue(buildResult.isSuccess)
            assertNotNull(buildResult.outputApkFile)

            System.err.println("=== STAGE 10 & 11: REAL OUTPUT APK VALIDATION ===")
            val outputApk = buildResult.outputApkFile!!
            System.err.println("OUTPUT APK PATH: ${outputApk.absolutePath}")
            System.err.println("OUTPUT APK SIZE: ${outputApk.length()} bytes")
            assertTrue(outputApk.exists())
            assertTrue(outputApk.length() > 0)

            System.err.println("=== STAGE 12: OUTPUT APK STRUCTURAL VALIDATION ===")
            var outputEntryCount = 0
            var outputHasAsset = false
            var outputHasCertSf = false
            var outputHasCertRsa = false
            ZipFile(outputApk).use { zip ->
                outputEntryCount = zip.size()
                outputHasAsset = zip.getEntry("assets/config/rebuild_metadata.json") != null
                outputHasCertSf = zip.getEntry("META-INF/CERT.SF") != null
                outputHasCertRsa = zip.getEntry("META-INF/CERT.RSA") != null
            }
            System.err.println("OUTPUT ZIP VALID: true, Total entries: $outputEntryCount, InjectedAsset: $outputHasAsset, CERT.SF: $outputHasCertSf, CERT.RSA: $outputHasCertRsa")
            assertTrue(outputEntryCount > 0)
            assertTrue(outputHasAsset)
            assertTrue(outputHasCertSf)
            assertTrue(outputHasCertRsa)

            System.err.println("=== STAGE 13: OUTPUT SHA-256 DIRECT CALCULATION ===")
            val directOutputSha256 = CryptoUtils.calculateSha256(outputApk)
            System.err.println("OUTPUT DIRECT SHA-256: $directOutputSha256")
            System.err.println("PIPELINE REPORTED SHA-256: ${buildResult.outputSha256}")
            assertEquals(directOutputSha256, buildResult.outputSha256)

            System.err.println("=== STAGE 14: REAL SIGNING VERIFICATION ===")
            val outputVerifyResult = signingManager.verifyApk(outputApk)
            System.err.println("OUTPUT VERIFY IS_VALID: ${outputVerifyResult.isValid}")
            System.err.println("OUTPUT VERIFY SCHEME: ${outputVerifyResult.signingScheme}")
            System.err.println("OUTPUT VERIFY ENTRIES COUNT: ${outputVerifyResult.verifiedEntriesCount}")
            System.err.println("OUTPUT VERIFY CERTS: ${outputVerifyResult.certificates.map { it.sha256Fingerprint }}")
            System.err.println("OUTPUT VERIFY ISSUES: ${outputVerifyResult.issues}")
            assertTrue(outputVerifyResult.isValid)
            assertEquals("APK Signature Scheme v1 (JAR)", outputVerifyResult.signingScheme)

            System.err.println("=== STAGE 15: ROOM PERSISTENCE ===")
            val historyEntity = BuildHistoryEntity(
                id = UUID.randomUUID().toString(),
                projectName = "Stage9RebuiltApp",
                buildType = "REBUILD",
                isSuccess = true,
                durationMs = buildResult.durationMs,
                outputApkPath = outputApk.absolutePath,
                outputSha256 = directOutputSha256,
                logSummary = "Real E2E rebuild verified with SHA-256: $directOutputSha256"
            )
            database.buildHistoryDao().insertBuildHistory(historyEntity)
            val historyList = database.buildHistoryDao().getAllBuildHistory().first()
            val persisted = historyList.firstOrNull { it.id == historyEntity.id }
            assertNotNull(persisted)
            System.err.println("PERSISTED ROOM ID: ${persisted?.id}")
            System.err.println("PERSISTED PROJECT: ${persisted?.projectName}")
            System.err.println("PERSISTED SHA-256: ${persisted?.outputSha256}")
            assertEquals(directOutputSha256, persisted?.outputSha256)

            // Cleanup
            sourceApk.delete()
            outputApk.delete()
            System.err.println("=== PIPELINE EXECUTION COMPLETED ===")
        }
    }
}
