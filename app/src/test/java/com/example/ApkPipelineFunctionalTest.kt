package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.diana.DianaApkAnalyzer
import com.example.ai.inference.AiMode
import com.example.ai.inference.InferenceConfig
import com.example.ai.inference.InferenceService
import com.example.ai.models.ModelManager
import com.example.apk.builder.ApkBuildManager
import com.example.apk.builder.ProjectBuildConfig
import com.example.apk.model.AbiOption
import com.example.apk.model.BuildStatus
import com.example.apk.model.BuildType
import com.example.apk.model.CustomAssetItem
import com.example.apk.model.RebuildConfig
import com.example.apk.rebuilder.ApkRebuildPipeline
import com.example.apk.scanner.ApkScannerService
import com.example.apk.signing.SigningManager
import com.example.core.CryptoUtils
import com.example.core.SecurityManager
import com.example.core.SystemMonitor
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkPipelineFunctionalTest {

    private lateinit var context: Context
    private lateinit var scannerService: ApkScannerService
    private lateinit var signingManager: SigningManager
    private lateinit var rebuildPipeline: ApkRebuildPipeline
    private lateinit var buildManager: ApkBuildManager
    private lateinit var inferenceService: InferenceService
    private lateinit var dianaAnalyzer: DianaApkAnalyzer
    private lateinit var modelManager: ModelManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scannerService = ApkScannerService(context)
        signingManager = SigningManager(context)
        rebuildPipeline = ApkRebuildPipeline(context, scannerService, signingManager)
        buildManager = ApkBuildManager(context, signingManager)
        inferenceService = InferenceService(context)
        dianaAnalyzer = DianaApkAnalyzer()
        modelManager = ModelManager(context, inferenceService)
    }

    @Test
    fun testFullApkPipeline_Import_Scan_Analyze_Rebuild_Sign_Verify_Output() {
        runBlocking {
            // 1. APK IMPORT & GENERATION
            val tempApkFile = scannerService.generateSampleApk(
                appName = "InitialDemoApp",
                packageName = "com.example.initialapp"
            )
            assertNotNull("Generated sample APK must not be null", tempApkFile)
            assertTrue("Temporary APK file must exist on sandbox disk", tempApkFile.exists())
            assertTrue("Generated sample APK must have valid non-empty byte size", tempApkFile.length() > 500)

            // 2. APK SHA-256 AND MD5 CALCULATION
            val initialSha256 = CryptoUtils.calculateSha256(tempApkFile)
            val initialMd5 = CryptoUtils.calculateMd5(tempApkFile)
            assertEquals("SHA-256 string length must be 64 hex chars", 64, initialSha256.length)
            assertEquals("MD5 string length must be 32 hex chars", 32, initialMd5.length)

            // 3 & 4. MANIFEST INSPECTION & PERMISSION EXTRACTION
            val scannedInfo = scannerService.scanApk(tempApkFile)
            assertNotNull("Scanned APK Info must be parsed successfully", scannedInfo)
            assertEquals("Parsed app name must match", "InitialDemoApp", scannedInfo.appName)
            assertEquals("Parsed package name must match", "com.example.initialapp", scannedInfo.packageName)
            assertEquals("Parsed version name must match", "1.0.0", scannedInfo.versionName)
            assertEquals("Parsed version code must match", 100L, scannedInfo.versionCode)
            assertEquals("Parsed minSdk must match", 24, scannedInfo.minSdk)
            assertEquals("Parsed targetSdk must match", 35, scannedInfo.targetSdk)
            assertTrue("Scanned permissions must be populated", scannedInfo.permissions.isNotEmpty())
            assertTrue("Must detect INTERNET permission", scannedInfo.permissions.any { it.name.contains("INTERNET") })

            // 5. DEX INSPECTION
            assertTrue("DEX files list must contain classes.dex", scannedInfo.dexFiles.isNotEmpty())
            val dexFile = scannedInfo.dexFiles.first()
            assertEquals("classes.dex", dexFile.name)
            assertTrue("Dex file must have valid size", dexFile.sizeBytes > 0)
            assertTrue("Dex header must report non-zero class count", dexFile.classDefsCount > 0)

            // 6. NATIVE LIBRARY INSPECTION
            assertTrue("Scanned native ABIs must include arm64-v8a", scannedInfo.nativeLibraries.containsKey("arm64-v8a"))
            val arm64Libs = scannedInfo.nativeLibraries["arm64-v8a"]!!
            assertTrue("Arm64 libs must contain libnative-engine.so", arm64Libs.any { it.contains("libnative-engine.so") })

            // 7. CERTIFICATE & SIGNATURE INSPECTION
            val certs = signingManager.listKeys()
            assertTrue("Keystore must contain at least default signing key", certs.isNotEmpty())
            val defaultKey = certs.first { it.isDefault }
            assertTrue("Default key subject must contain CN", defaultKey.subject.contains("CN="))
            assertEquals("Fingerprint SHA-256 hex string length without colons must be 64", 64, defaultKey.sha256Fingerprint.replace(":", "").length)

            // 8. DIANA ANALYSIS
            val dianaReport = dianaAnalyzer.analyze(scannedInfo)
            assertNotNull("DIANA Report must be generated", dianaReport)
            assertTrue("DIANA Security score must be between 0 and 100", dianaReport.overallSecurityScore in 0..100)
            assertTrue("DIANA must produce security findings", dianaReport.findings.isNotEmpty())
            assertTrue("DIANA must produce rebuild recommendations", dianaReport.rebuildRecommendations.isNotEmpty())

            // 9. REBUILD PLANNING
            val rebuildConfig = RebuildConfig(
                newAppName = "RebuiltPatchedApp",
                newVersionName = "2.1.0",
                newVersionCode = 210,
                newMinSdk = 26,
                newTargetSdk = 35,
                customAssets = listOf(CustomAssetItem("config/app_config.json", "{\"patched\":true,\"env\":\"prod\"}")),
                keyAlias = "test_custom_key"
            )
            val buildPlan = rebuildPipeline.createBuildPlan(scannedInfo, rebuildConfig)
            assertNotNull("Build plan must be created", buildPlan)
            assertEquals("Input APK name must match", scannedInfo.fileName, buildPlan.inputApkName)
            assertTrue("Build plan changes must contain label change", buildPlan.changes.any { it.targetKey == "android:label" })
            assertTrue("Build plan changes must contain versionName change", buildPlan.changes.any { it.targetKey == "android:versionName" })
            assertTrue("Build plan changes must contain versionCode change", buildPlan.changes.any { it.targetKey == "android:versionCode" })
            assertTrue("Build plan changes must contain minSdk change", buildPlan.changes.any { it.targetKey == "android:minSdkVersion" })
            assertTrue("Build plan changes must contain asset replacement", buildPlan.changes.any { it.targetKey.contains("app_config.json") })

            // 10. AUTHORIZED APK REBUILD (12-STAGE PIPELINE)
            // Ensure custom key exists for signing
            signingManager.generateAndSaveKey("test_custom_key", "CN=Test Rebuilder, O=SecurityLab, C=US")
            val rebuildResult = rebuildPipeline.executeRebuild(tempApkFile, rebuildConfig, buildPlan)

            assertTrue("Rebuild result must report success: ${rebuildResult.errorMessage}", rebuildResult.isSuccess)
            assertNotNull("Output APK file must not be null", rebuildResult.outputApkFile)
            assertTrue("Output APK file must exist on disk", rebuildResult.outputApkFile!!.exists())
            assertTrue("Output APK file size must be > 0", rebuildResult.outputApkFile!!.length() > 0)
            assertNotNull("Rebuild result output SHA-256 must be populated", rebuildResult.outputSha256)

            // 11 & 12. APK SIGNING & SIGNATURE VERIFICATION
            val verifyResult = signingManager.verifyApk(rebuildResult.outputApkFile!!)
            assertTrue("Rebuilt APK must pass cryptographic verification: ${verifyResult.issues}", verifyResult.isValid)
            assertTrue("Verified entries count must be > 0", verifyResult.verifiedEntriesCount > 0)
            assertTrue("Verified certificates must be present", verifyResult.certificates.isNotEmpty())

            // 13. OUTPUT / EXPORT VERIFICATION
            // Inspect the rebuilt APK to ensure all changes were correctly applied to the ZIP
            ZipFile(rebuildResult.outputApkFile!!).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                assertNotNull("Rebuilt APK must contain AndroidManifest.xml", manifestEntry)
                val configAssetEntry = zip.getEntry("assets/config/app_config.json")
                assertNotNull("Rebuilt APK must contain injected asset", configAssetEntry)

                val assetContent = zip.getInputStream(configAssetEntry).bufferedReader().readText()
                assertTrue("Injected asset content must match", assetContent.contains("\"patched\":true"))

                val signatureMf = zip.getEntry("META-INF/MANIFEST.MF")
                val signatureSf = zip.getEntry("META-INF/CERT.SF")
                val signatureRsa = zip.getEntry("META-INF/CERT.RSA")
                assertNotNull("META-INF/MANIFEST.MF must exist", signatureMf)
                assertNotNull("META-INF/CERT.SF must exist", signatureSf)
                assertNotNull("META-INF/CERT.RSA must exist", signatureRsa)
            }

            // Clean up test file
            tempApkFile.delete()
            rebuildResult.outputApkFile?.delete()
        }
    }

    @Test
    fun testApkBuildManager_NewProjectBuild() {
        runBlocking {
            val buildConfig = ProjectBuildConfig(
                projectName = "NewStandaloneApp",
                packageName = "com.workbench.standalone",
                versionName = "1.0.0",
                versionCode = 101,
                buildType = BuildType.DEBUG,
                targetAbis = listOf(AbiOption.ARM64_V8A, AbiOption.X86_64)
            )

            val result = buildManager.executeBuild(buildConfig)
            assertTrue("Standalone project build must succeed: ${result.errorMessage}", result.isSuccess)
            assertNotNull("Output APK file must exist", result.outputApkFile)
            assertTrue("Output APK file must exist on disk", result.outputApkFile!!.exists())

            val verify = signingManager.verifyApk(result.outputApkFile!!)
            assertTrue("Newly built APK must have valid signature", verify.isValid)

            result.outputApkFile?.delete()
        }
    }

    @Test
    fun testAiInference_Modes_And_Telemetry() {
        runBlocking {
            // 16. REAL PRIVATE BRAIN MODEL MANAGER DISCOVERY
            val testProvider = object : com.example.ai.inference.PrivateBrainProvider {
                override suspend fun isAvailable(): Boolean = true
                override suspend fun listModels(): List<String> = listOf("qwen2.5-coder:1.5b", "llama3.2:3b")
                override suspend fun generate(
                    messages: List<com.example.ai.inference.ChatMessage>,
                    model: String,
                    temperature: Float,
                    maxTokens: Int
                ): String = "Real model output"
                override fun generateStream(
                    messages: List<com.example.ai.inference.ChatMessage>,
                    model: String,
                    temperature: Float,
                    maxTokens: Int
                ): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
                    emit("Analysis chunk")
                }
            }
            inferenceService.setProvider(testProvider)

            // Test ModelManager server discovery
            val discovered = modelManager.refreshModels()
            assertTrue("ModelManager must discover models from real provider", discovered.isNotEmpty())
            assertEquals("qwen2.5-coder:1.5b", discovered.first().fileName)
            assertEquals(com.example.ai.models.ServerConnectionState.AVAILABLE, modelManager.serverState.value)
            assertTrue("isModelAvailable must return true for server model", modelManager.isModelAvailable("qwen2.5-coder:1.5b"))

            // Connect/load model
            modelManager.loadModel("qwen2.5-coder:1.5b")
            assertTrue("Inference service must report local model loaded", inferenceService.isLocalModelLoaded())
            assertEquals("qwen2.5-coder:1.5b", inferenceService.getLoadedModelName())

            // 17. CLOUD & OFFLINE GENERATION & METRICS
            inferenceService.setMode(AiMode.OFFLINE)
            val offlineResponses = inferenceService.generateStream("Analyze permissions").toList()
            assertTrue("Offline stream must generate tokens", offlineResponses.isNotEmpty())

            inferenceService.setMode(AiMode.AUTO)
            assertEquals(AiMode.AUTO, inferenceService.getMode())

            // 18. AUTO AI MODE WITH UNLOAD
            inferenceService.unloadLocalModel()
            assertFalse("Inference service must report local model unloaded", inferenceService.isLocalModelLoaded())

            // 19. MODEL / RAM / TOKEN MONITORING
            val metrics = SystemMonitor.captureMetrics(context)
            assertTrue("Allocated RAM must be >= 0", metrics.allocatedRamMb >= 0)
            assertTrue("Available storage must be >= 0", metrics.availableStorageMb >= 0)
            assertTrue("Active threads count must be > 0", metrics.activeThreadsCount > 0)

            // 15. FAILURE CLEANUP & MAINTENANCE
            val testSandbox = File(context.filesDir, "test_sandbox").apply { mkdirs() }
            File(testSandbox, "dummy.tmp").writeText("temp")
            SecurityManager.cleanupDirectory(testSandbox)
            assertFalse("Sandbox directory must be cleaned", testSandbox.exists())
        }
    }
}
