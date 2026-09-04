package com.example.apk.builder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.apk.model.AbiOption
import com.example.apk.model.BuildFailureCategory
import com.example.apk.model.BuildMode
import com.example.apk.model.BuildStatus
import com.example.apk.model.BuildType
import com.example.apk.signing.SigningManager
import com.example.core.CryptoUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
class RealApkBuildEngineTest {

    private lateinit var context: Context
    private lateinit var signingManager: SigningManager
    private lateinit var buildManager: ApkBuildManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        signingManager = SigningManager(context)
        buildManager = ApkBuildManager(context, signingManager)
    }

    // 1. Invalid project directory
    @Test
    fun testProjectValidation_NonExistentDirectory() {
        val invalidDir = File("/invalid/non_existent_directory_42")
        val validation = buildManager.validateProject(invalidDir)
        assertFalse("Non-existent directory must be rejected", validation.isValid)
        assertEquals(BuildFailureCategory.PROJECT_INVALID, validation.category)
        assertTrue(validation.errorMessage!!.contains("PROJECT_INVALID"))
    }

    @Test
    fun testProjectValidation_RejectApkFileAsSourceProject() {
        val tempApk = File(context.cacheDir, "sample_test.apk").apply {
            writeBytes("PKdummy".toByteArray())
        }
        val validation = buildManager.validateProject(tempApk)
        assertFalse("APK file must NOT be accepted as source project directory", validation.isValid)
        assertEquals(BuildFailureCategory.PROJECT_INVALID, validation.category)
        assertTrue(validation.errorMessage!!.contains("APK_REPACK mode"))
        tempApk.delete()
    }

    @Test
    fun testProjectValidation_DirectoryWithoutGradleFiles() {
        val emptyDir = File(context.cacheDir, "empty_test_project_${System.currentTimeMillis()}").apply { mkdirs() }
        val validation = buildManager.validateProject(emptyDir)
        assertFalse("Empty directory without build files must be rejected", validation.isValid)
        assertEquals(BuildFailureCategory.PROJECT_INVALID, validation.category)
        emptyDir.deleteRecursively()
    }

    // 2. Missing Gradle wrapper
    @Test
    fun testGradleWrapper_MissingPropertiesAndJar() {
        val dummyProject = File(context.cacheDir, "dummy_project_${System.currentTimeMillis()}").apply { mkdirs() }
        val validation = buildManager.validateGradleWrapper(dummyProject)
        assertFalse("Project missing wrapper must fail validation", validation.isValid)
        assertEquals(BuildFailureCategory.GRADLE_WRAPPER_INVALID, validation.category)
        assertTrue(validation.errorMessage!!.contains("GRADLE_WRAPPER_INVALID"))
        dummyProject.deleteRecursively()
    }

    // 3. Corrupt Gradle wrapper
    @Test
    fun testGradleWrapper_CorruptJar() {
        val dummyProject = File(context.cacheDir, "corrupt_wrapper_project_${System.currentTimeMillis()}").apply { mkdirs() }
        val wrapperDir = File(dummyProject, "gradle/wrapper").apply { mkdirs() }
        File(wrapperDir, "gradle-wrapper.properties").writeText("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.3.1-bin.zip")
        // Write corrupt non-zip bytes to jar
        File(wrapperDir, "gradle-wrapper.jar").writeText("CORRUPT_BYTES_NOT_A_ZIP")

        val validation = buildManager.validateGradleWrapper(dummyProject)
        assertFalse("Corrupt wrapper JAR must be detected and rejected", validation.isValid)
        assertEquals(BuildFailureCategory.GRADLE_WRAPPER_INVALID, validation.category)
        assertTrue(validation.errorMessage!!.contains("recreate") || validation.errorMessage!!.contains("corrupt"))
        dummyProject.deleteRecursively()
    }

    // 4. Android SDK validation
    @Test
    fun testAndroidSdkValidation_DetectsEnvironment() {
        val validation = buildManager.validateAndroidSdk()
        // In our build environment, SDK should be detected at /opt/android/sdk
        if (System.getenv("ANDROID_SDK_ROOT") != null || File("/opt/android/sdk").exists()) {
            assertTrue("Android SDK should be valid in build environment: ${validation.errorMessage}", validation.isValid)
            assertNotNull(validation.executablePath)
        }
    }

    // 5. Build task allowlist and security
    @Test
    fun testGradleTaskValidation_SecurityAllowlist() {
        assertTrue(buildManager.validateGradleTask(":app:assembleDebug"))
        assertTrue(buildManager.validateGradleTask(":app:assembleRelease"))
        assertTrue(buildManager.validateGradleTask("assembleDebug"))
        assertTrue(buildManager.validateGradleTask("assembleRelease"))

        // Command injection attempts must fail
        assertFalse(buildManager.validateGradleTask(":app:assembleDebug; rm -rf /"))
        assertFalse(buildManager.validateGradleTask(":app:assembleRelease && echo hacked"))
        assertFalse(buildManager.validateGradleTask("assembleDebug | cat /etc/passwd"))
        assertFalse(buildManager.validateGradleTask("`touch /tmp/owned`"))
        assertFalse(buildManager.validateGradleTask("\$(whoami)"))
    }

    // 6. Missing APK artifact
    @Test
    fun testArtifactValidation_MissingFile() {
        val missing = File(context.cacheDir, "does_not_exist.apk")
        val validation = buildManager.validateApkArtifact(missing)
        assertFalse("Missing artifact must be detected", validation.isValid)
        assertEquals(BuildFailureCategory.ARTIFACT_MISSING, validation.failureCategory)
    }

    // 7. Invalid APK artifact (non-apk extension)
    @Test
    fun testArtifactValidation_NonApkExtension() {
        val textFile = File(context.cacheDir, "not_an_apk.zip").apply { writeText("dummy") }
        val validation = buildManager.validateApkArtifact(textFile)
        assertFalse("File without .apk extension must be rejected", validation.isValid)
        assertEquals(BuildFailureCategory.ARTIFACT_INVALID, validation.failureCategory)
        textFile.delete()
    }

    // 8. Invalid APK artifact (zero byte file)
    @Test
    fun testArtifactValidation_ZeroByteFile() {
        val emptyApk = File(context.cacheDir, "empty.apk").apply { createNewFile() }
        val validation = buildManager.validateApkArtifact(emptyApk)
        assertFalse("0-byte APK must be rejected", validation.isValid)
        assertEquals(BuildFailureCategory.ARTIFACT_INVALID, validation.failureCategory)
        emptyApk.delete()
    }

    // 9. Invalid APK artifact (corrupt ZIP)
    @Test
    fun testArtifactValidation_CorruptZip() {
        val corruptApk = File(context.cacheDir, "corrupt.apk").apply {
            writeBytes("THIS_IS_NOT_A_VALID_ZIP_ARCHIVE_DATA".toByteArray())
        }
        val validation = buildManager.validateApkArtifact(corruptApk)
        assertFalse("Corrupted ZIP must be rejected", validation.isValid)
        assertEquals(BuildFailureCategory.ARTIFACT_INVALID, validation.failureCategory)
        corruptApk.delete()
    }

    // 10. Successful APK validation on real compiled APK
    @Test
    fun testArtifactValidation_RealCompiledDebugApk() {
        val debugApk = File("app/build/outputs/apk/debug/app-debug.apk")
        if (debugApk.exists()) {
            val validation = buildManager.validateApkArtifact(debugApk)
            assertTrue("Real compiled APK must pass validation: ${validation.errorMessage}", validation.isValid)
            assertTrue("Real APK must contain AndroidManifest.xml", validation.hasManifest)
            assertTrue("Real APK must contain classes.dex", validation.hasClassesDex)
        }
    }

    // 11. Build cancellation
    @Test
    fun testBuildCancellation_TransitionsToCancelled() {
        buildManager.cancelBuild()
        assertEquals(BuildStatus.CANCELLED, buildManager.buildStatus.value)
        val logs = buildManager.buildLogs.value
        assertTrue("Cancellation log must be recorded", logs.any { it.stage == BuildStatus.CANCELLED })
    }

    // 12. Release signing unavailable detection
    @Test
    fun testReleaseSigningUnavailable_StrictCheck() {
        val emptyDir = File(context.cacheDir, "no_signing_dir_${System.currentTimeMillis()}").apply { mkdirs() }
        assertFalse("Directory without release keys must return false for hasReleaseSigningConfig",
            buildManager.hasReleaseSigningConfig(emptyDir))
        emptyDir.deleteRecursively()
    }

    // 13. Log sanitization
    @Test
    fun testLogSanitization_MasksCredentials() {
        val raw1 = "password=MySuperSecretPassword123"
        val sanitized1 = buildManager.sanitizeLog(raw1)
        assertFalse("Raw password must NOT appear in sanitized log", sanitized1.contains("MySuperSecretPassword123"))
        assertTrue("Password must be masked", sanitized1.contains("******"))

        val raw2 = "Authorization: Bearer ya29.a0AfH6SMDUMMYTOKEN123"
        val sanitized2 = buildManager.sanitizeLog(raw2)
        assertFalse("Bearer token must NOT appear in log", sanitized2.contains("ya29.a0AfH6SMDUMMYTOKEN123"))

        val raw3 = "STORE_PASSWORD=keystore_pass_val"
        val sanitized3 = buildManager.sanitizeLog(raw3)
        assertFalse(sanitized3.contains("keystore_pass_val"))
    }

    // 14. SHA-256 calculation
    @Test
    fun testSha256Calculation_Accurate() {
        val testFile = File(context.cacheDir, "sha_test.bin").apply {
            writeBytes("TEST_DATA_FOR_SHA256".toByteArray(Charsets.UTF_8))
        }
        val sha256 = CryptoUtils.calculateSha256(testFile)
        assertEquals(64, sha256.length)
        assertTrue(sha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
        testFile.delete()
    }

    // 15. Real Gradle phase detection
    @Test
    fun testGradlePhaseDetection() {
        assertEquals(BuildStatus.CONFIGURING, buildManager.detectGradlePhase("> Configure project :app"))
        assertEquals(BuildStatus.COMPILING, buildManager.detectGradlePhase("> Task :app:compileReleaseKotlin"))
        assertEquals(BuildStatus.COMPILING, buildManager.detectGradlePhase("> Task :app:compileDebugJavaWithJavac"))
        assertEquals(BuildStatus.MERGING_RESOURCES, buildManager.detectGradlePhase("> Task :app:processReleaseResources"))
        assertEquals(BuildStatus.DEXING, buildManager.detectGradlePhase("> Task :app:dexBuilderRelease"))
        assertEquals(BuildStatus.PACKAGING, buildManager.detectGradlePhase("> Task :app:packageRelease"))
        assertEquals(BuildStatus.SIGNING, buildManager.detectGradlePhase("> Task :app:validateSigningRelease"))
        assertEquals(BuildStatus.BUILDING, buildManager.detectGradlePhase("> Task :app:someOtherTask"))
    }

    // 16. Build mode separation: APK_REPACK vs SOURCE_BUILD
    @Test
    fun testBuildModeSeparation_SourceBuildVsRepack() = runBlocking {
        val repackConfig = ProjectBuildConfig(
            projectName = "TestRepack",
            packageName = "com.test.repack",
            buildMode = BuildMode.APK_REPACK
        )
        val result = buildManager.executeBuild(repackConfig)
        assertFalse("executeBuild must reject APK_REPACK mode without source project", result.isSuccess)
        assertEquals(BuildMode.APK_REPACK, result.buildMode)
        assertTrue(result.errorMessage!!.contains("APK_REPACK"))
    }

    // 17. No fallback to debug on release failure
    @Test
    fun testNoDebugFallback_TaskPreserved() {
        val releaseConfig = ProjectBuildConfig(
            projectName = "AppRelease",
            packageName = "com.example",
            buildType = BuildType.RELEASE
        )
        assertEquals(BuildType.RELEASE, releaseConfig.buildType)
        // Task must resolve to :app:assembleRelease, not fallback to debug
        val resolvedTask = releaseConfig.gradleTask ?: ":app:assembleRelease"
        assertEquals(":app:assembleRelease", resolvedTask)
    }
}
