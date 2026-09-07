package com.example.apk.builder

import android.content.Context
import com.example.apk.model.AbiOption
import com.example.apk.model.BuildFailureCategory
import com.example.apk.model.BuildLog
import com.example.apk.model.BuildMode
import com.example.apk.model.BuildResult
import com.example.apk.model.BuildStatus
import com.example.apk.model.BuildType
import com.example.apk.model.RebuildConfig
import com.example.apk.rebuilder.ApkRebuildPipeline
import com.example.apk.signing.SigningManager
import com.example.core.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

data class ProjectBuildConfig(
    val projectName: String,
    val packageName: String,
    val versionName: String = "1.0.0",
    val versionCode: Long = 100,
    val buildType: BuildType = BuildType.DEBUG,
    val targetAbis: List<AbiOption> = listOf(AbiOption.ARM64_V8A, AbiOption.X86_64),
    val minSdk: Int = 24,
    val targetSdk: Int = 36,
    val customAssets: Map<String, String> = emptyMap(),
    val isMinified: Boolean = false,
    val signingAlias: String = "default_release_key",
    val projectDir: File? = null,
    val buildMode: BuildMode = BuildMode.SOURCE_BUILD,
    val gradleTask: String? = null,
    val strictReleaseSigning: Boolean = false
)

data class ToolchainValidation(
    val isValid: Boolean,
    val category: BuildFailureCategory? = null,
    val errorMessage: String? = null,
    val executablePath: String? = null
)

data class ArtifactValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val failureCategory: BuildFailureCategory? = null,
    val hasManifest: Boolean = false,
    val hasClassesDex: Boolean = false,
    val isSigned: Boolean = false
)

class ApkBuildManager(
    private val context: Context,
    private val signingManager: SigningManager,
    defaultProjectDirParam: File? = null
) {
    val defaultProjectDir: File = defaultProjectDirParam ?: resolveDefaultProjectDir()

    private val _buildLogs = MutableStateFlow<List<BuildLog>>(emptyList())
    val buildLogs: StateFlow<List<BuildLog>> = _buildLogs.asStateFlow()

    private val _buildStatus = MutableStateFlow(BuildStatus.IDLE)
    val buildStatus: StateFlow<BuildStatus> = _buildStatus.asStateFlow()

    private val buildMutex = Mutex()
    private val isCancelled = AtomicBoolean(false)

    @Volatile
    private var activeProcess: Process? = null

    companion object {
        fun resolveDefaultProjectDir(): File {
            val candidates = listOf(
                File("."),
                File(".."),
                File(System.getProperty("user.dir") ?: ".")
            )
            for (candidate in candidates) {
                if (File(candidate, "settings.gradle.kts").exists() ||
                    File(candidate, "gradlew").exists() ||
                    File(candidate, "gradle/wrapper/gradle-wrapper.properties").exists()) {
                    return candidate.canonicalFile
                }
            }
            return File(".").canonicalFile
        }

        private val ALLOWED_TASKS = setOf(
            ":app:assembleDebug",
            ":app:assembleRelease",
            ":app:bundleDebug",
            ":app:bundleRelease",
            ":app:lintDebug",
            ":app:testDebugUnitTest",
            "assembleDebug",
            "assembleRelease",
            "bundleDebug",
            "bundleRelease"
        )
        private val TASK_NAME_PATTERN = Regex("^(:?[a-zA-Z0-9_-]+)+$")
    }

    fun cancelBuild() {
        isCancelled.set(true)
        val proc = activeProcess
        if (proc != null) {
            val isAlive = try {
                proc.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
            if (isAlive) {
                try {
                    proc.destroy()
                    proc.destroyForcibly()
                } catch (_: Exception) {}
            }
        }
        _buildStatus.value = BuildStatus.CANCELLED
        val logEntry = BuildLog(
            timestamp = System.currentTimeMillis(),
            stage = BuildStatus.CANCELLED,
            message = "Build execution was cancelled by user.",
            isError = false
        )
        _buildLogs.value = _buildLogs.value + logEntry
    }

    fun validateProject(projectDir: File): ToolchainValidation {
        if (!projectDir.exists()) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.PROJECT_INVALID,
                errorMessage = "PROJECT_INVALID: Project directory '${projectDir.path}' does not exist."
            )
        }
        if (!projectDir.isDirectory) {
            val hint = if (projectDir.name.endsWith(".apk", ignoreCase = true)) {
                " Input is an APK file, not an Android source project. Use APK_REPACK mode for existing APK files."
            } else ""
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.PROJECT_INVALID,
                errorMessage = "PROJECT_INVALID: '${projectDir.path}' is not a directory.$hint"
            )
        }

        val hasSettings = File(projectDir, "settings.gradle").exists() || File(projectDir, "settings.gradle.kts").exists()
        val hasBuild = File(projectDir, "build.gradle").exists() || File(projectDir, "build.gradle.kts").exists()
        val hasAppModule = File(projectDir, "app").isDirectory

        if (!hasSettings && !hasBuild) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.PROJECT_INVALID,
                errorMessage = "PROJECT_INVALID: Directory '${projectDir.path}' does not contain settings.gradle(.kts) or build.gradle(.kts)."
            )
        }
        if (!hasAppModule && !hasBuild) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.PROJECT_INVALID,
                errorMessage = "PROJECT_INVALID: Target is not an Android Gradle project (no app module or build files found)."
            )
        }
        return ToolchainValidation(isValid = true)
    }

    fun validateGradleWrapper(projectDir: File): ToolchainValidation {
        val wrapperDir = File(projectDir, "gradle/wrapper")
        val propFile = File(wrapperDir, "gradle-wrapper.properties")
        val jarFile = File(wrapperDir, "gradle-wrapper.jar")

        if (!propFile.exists()) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.GRADLE_WRAPPER_INVALID,
                errorMessage = "GRADLE_WRAPPER_INVALID: gradle-wrapper.properties missing at '${propFile.path}'. Remediation: Run 'gradle wrapper' to recreate."
            )
        }
        if (!jarFile.exists()) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.GRADLE_WRAPPER_INVALID,
                errorMessage = "GRADLE_WRAPPER_INVALID: gradle-wrapper.jar missing at '${jarFile.path}'. Remediation: Run 'gradle wrapper' to recreate."
            )
        }

        try {
            ZipFile(jarFile).use { zip ->
                val manifestEntry = zip.getEntry("META-INF/MANIFEST.MF")
                if (manifestEntry == null) {
                    return ToolchainValidation(
                        isValid = false,
                        category = BuildFailureCategory.GRADLE_WRAPPER_INVALID,
                        errorMessage = "GRADLE_WRAPPER_INVALID: gradle-wrapper.jar is corrupt (missing META-INF/MANIFEST.MF). Remediation: Run 'gradle wrapper'."
                    )
                }
            }
        } catch (e: Exception) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.GRADLE_WRAPPER_INVALID,
                errorMessage = "GRADLE_WRAPPER_INVALID: gradle-wrapper.jar is corrupt (${e.message}). Remediation: Run 'gradle wrapper' to recreate."
            )
        }

        val gradlewScript = File(projectDir, if (System.getProperty("os.name")?.lowercase()?.contains("win") == true) "gradlew.bat" else "gradlew")
        if (gradlewScript.exists()) {
            try {
                gradlewScript.setExecutable(true)
            } catch (_: Exception) {}
        }

        return ToolchainValidation(isValid = true, executablePath = gradlewScript.path)
    }

    fun validateAndroidSdk(): ToolchainValidation {
        val sdkPath = System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: listOf("/opt/android/sdk", "/usr/lib/android-sdk").firstOrNull { File(it).exists() }

        if (sdkPath.isNullOrBlank()) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.TOOLCHAIN_UNAVAILABLE,
                errorMessage = "ANDROID_TOOLCHAIN_UNAVAILABLE: Neither ANDROID_SDK_ROOT nor ANDROID_HOME environment variable is configured."
            )
        }
        val sdkDir = File(sdkPath)
        if (!sdkDir.exists() || !sdkDir.isDirectory) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.TOOLCHAIN_UNAVAILABLE,
                errorMessage = "ANDROID_TOOLCHAIN_UNAVAILABLE: Configured Android SDK directory '$sdkPath' does not exist."
            )
        }
        val hasPlatforms = File(sdkDir, "platforms").isDirectory
        val hasBuildTools = File(sdkDir, "build-tools").isDirectory
        val hasPlatformTools = File(sdkDir, "platform-tools").isDirectory

        if (!hasPlatforms && !hasBuildTools && !hasPlatformTools) {
            return ToolchainValidation(
                isValid = false,
                category = BuildFailureCategory.TOOLCHAIN_UNAVAILABLE,
                errorMessage = "ANDROID_TOOLCHAIN_UNAVAILABLE: Android SDK at '$sdkPath' contains neither platforms nor build tools."
            )
        }
        return ToolchainValidation(isValid = true, executablePath = sdkDir.path)
    }

    fun validateGradleTask(task: String): Boolean {
        val trimmed = task.trim()
        if (ALLOWED_TASKS.contains(trimmed)) return true
        return TASK_NAME_PATTERN.matches(trimmed)
    }

    fun hasReleaseSigningConfig(projectDir: File): Boolean {
        val envKeystore = System.getenv("KEYSTORE_PATH")
        if (!envKeystore.isNullOrBlank() && File(envKeystore).exists()) return true
        if (File(projectDir, "my-upload-key.jks").exists()) return true
        if (File(projectDir, "release.keystore").exists()) return true
        return false
    }

    fun sanitizeLog(raw: String): String {
        var sanitized = raw
        sanitized = Regex("(?i)(password|storePassword|keyPassword|keystore_pass|store_pass|key_pass)\\s*[=:]\\s*['\"]?([^'\"\\s]+)['\"]?")
            .replace(sanitized) { "${it.groupValues[1]}=******" }
        sanitized = Regex("(?i)(authorization:\\s*bearer\\s+)[a-zA-Z0-9_\\-\\.]+")
            .replace(sanitized, "$1******")
        sanitized = Regex("(?i)(KEYSTORE_BASE64|STORE_PASSWORD|KEY_PASSWORD|GITHUB_TOKEN)\\s*[=:]\\s*([A-Za-z0-9+/=_-]{4,})")
            .replace(sanitized) { "${it.groupValues[1]}=******" }
        return sanitized
    }

    fun detectGradlePhase(line: String): BuildStatus? {
        return when {
            line.contains("> Configure project") -> BuildStatus.CONFIGURING
            line.contains("compile") && (line.contains("Kotlin") || line.contains("Java")) -> BuildStatus.COMPILING
            (line.contains("merge") || line.contains("process") || line.contains("package")) && line.contains("Resources") -> BuildStatus.MERGING_RESOURCES
            line.contains("dexBuilder") || (line.contains("merge") && line.contains("Dex")) -> BuildStatus.DEXING
            line.contains("package") && (line.contains("Debug") || line.contains("Release")) -> BuildStatus.PACKAGING
            line.contains("validateSigning") || line.contains("sign") -> BuildStatus.SIGNING
            line.startsWith("> Task") -> BuildStatus.BUILDING
            else -> null
        }
    }

    fun locateGradleApk(projectDir: File, variant: String): File? {
        val variantDir = File(projectDir, "app/build/outputs/apk/$variant")
        if (variantDir.exists() && variantDir.isDirectory) {
            val apks = variantDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
            if (!apks.isNullOrEmpty()) {
                return apks.maxByOrNull { it.lastModified() }
            }
        }
        val baseApkDir = File(projectDir, "app/build/outputs/apk")
        if (baseApkDir.exists()) {
            val matches = baseApkDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) && it.path.contains(variant, ignoreCase = true) }
                .toList()
            if (matches.isNotEmpty()) {
                return matches.maxByOrNull { it.lastModified() }
            }
        }
        return null
    }

    fun validateApkArtifact(apkFile: File): ArtifactValidationResult {
        if (!apkFile.exists()) {
            return ArtifactValidationResult(
                isValid = false,
                failureCategory = BuildFailureCategory.ARTIFACT_MISSING,
                errorMessage = "BUILD_ARTIFACT_MISSING: Artifact file not found at '${apkFile.path}'."
            )
        }
        if (!apkFile.isFile) {
            return ArtifactValidationResult(
                isValid = false,
                failureCategory = BuildFailureCategory.ARTIFACT_INVALID,
                errorMessage = "BUILD_ARTIFACT_INVALID: Artifact path is not a file."
            )
        }
        if (!apkFile.name.endsWith(".apk", ignoreCase = true)) {
            return ArtifactValidationResult(
                isValid = false,
                failureCategory = BuildFailureCategory.ARTIFACT_INVALID,
                errorMessage = "BUILD_ARTIFACT_INVALID: File '${apkFile.name}' does not have .apk extension."
            )
        }
        if (apkFile.length() <= 0L) {
            return ArtifactValidationResult(
                isValid = false,
                failureCategory = BuildFailureCategory.ARTIFACT_INVALID,
                errorMessage = "BUILD_ARTIFACT_INVALID: Output APK size is 0 bytes."
            )
        }

        var hasManifest = false
        var hasClassesDex = false
        var isSigned = false

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name == "AndroidManifest.xml") hasManifest = true
                    if (name.startsWith("classes") && name.endsWith(".dex")) hasClassesDex = true
                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC") || name.endsWith(".SF"))) {
                        isSigned = true
                    }
                }
            }
        } catch (e: Exception) {
            return ArtifactValidationResult(
                isValid = false,
                failureCategory = BuildFailureCategory.ARTIFACT_INVALID,
                errorMessage = "BUILD_ARTIFACT_INVALID: Output file cannot be parsed as a valid APK ZIP archive (${e.message})."
            )
        }

        if (!hasManifest) {
            return ArtifactValidationResult(
                isValid = false,
                failureCategory = BuildFailureCategory.ARTIFACT_INVALID,
                errorMessage = "BUILD_ARTIFACT_INVALID: APK is missing AndroidManifest.xml."
            )
        }
        if (!hasClassesDex) {
            return ArtifactValidationResult(
                isValid = false,
                failureCategory = BuildFailureCategory.ARTIFACT_INVALID,
                errorMessage = "BUILD_ARTIFACT_INVALID: APK is missing compiled classes.dex."
            )
        }

        return ArtifactValidationResult(
            isValid = true,
            hasManifest = true,
            hasClassesDex = true,
            isSigned = isSigned
        )
    }

    suspend fun executeBuild(config: ProjectBuildConfig): BuildResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (!buildMutex.tryLock()) {
            return@withContext BuildResult(
                isSuccess = false,
                failureCategory = BuildFailureCategory.EXECUTION_ERROR,
                errorMessage = "A build is currently running. Concurrent builds on the same project are not permitted."
            )
        }

        try {
            isCancelled.set(false)
            _buildLogs.value = emptyList()
            _buildStatus.value = BuildStatus.PREPARING

            fun log(stage: BuildStatus, msg: String, isErr: Boolean = false) {
                _buildStatus.value = stage
                val entry = BuildLog(
                    timestamp = System.currentTimeMillis(),
                    stage = stage,
                    message = msg,
                    isError = isErr
                )
                _buildLogs.value = _buildLogs.value + entry
            }

            // 1. Check Build Mode
            if (config.buildMode == BuildMode.APK_REPACK) {
                log(BuildStatus.FAILED, "APK_REPACK mode requires an existing APK input. Use executeRepack() for repackaging.", isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    buildMode = BuildMode.APK_REPACK,
                    failureCategory = BuildFailureCategory.PROJECT_INVALID,
                    errorMessage = "APK_REPACK mode requires an existing APK. For compiling from Android source, use SOURCE_BUILD mode."
                )
            }

            val projectDir = (config.projectDir ?: defaultProjectDir).canonicalFile
            log(BuildStatus.PREPARING, "Starting SOURCE_BUILD for project '${config.projectName}' in '${projectDir.path}'...")

            // 2. Validate Project
            val projVal = validateProject(projectDir)
            if (!projVal.isValid) {
                val err = projVal.errorMessage ?: "PROJECT_INVALID"
                log(BuildStatus.FAILED, err, isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    buildMode = BuildMode.SOURCE_BUILD,
                    failureCategory = projVal.category,
                    errorMessage = err
                )
            }

            // 3. Validate Gradle Wrapper
            val wrapperVal = validateGradleWrapper(projectDir)
            if (!wrapperVal.isValid) {
                val err = wrapperVal.errorMessage ?: "GRADLE_WRAPPER_INVALID"
                log(BuildStatus.FAILED, err, isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    buildMode = BuildMode.SOURCE_BUILD,
                    failureCategory = wrapperVal.category,
                    errorMessage = err
                )
            }

            // 4. Validate Android SDK
            val sdkVal = validateAndroidSdk()
            if (!sdkVal.isValid) {
                val err = sdkVal.errorMessage ?: "ANDROID_TOOLCHAIN_UNAVAILABLE"
                log(BuildStatus.FAILED, err, isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    buildMode = BuildMode.SOURCE_BUILD,
                    failureCategory = sdkVal.category,
                    errorMessage = err
                )
            }

            // 5. Determine Task and Variant
            val isRelease = config.buildType == BuildType.RELEASE
            val variant = if (isRelease) "release" else "debug"
            val defaultTask = if (isRelease) ":app:assembleRelease" else ":app:assembleDebug"
            val task = (config.gradleTask?.trim() ?: defaultTask)

            if (!validateGradleTask(task)) {
                val err = "Command security check failed: '$task' is not an allowed Gradle build task."
                log(BuildStatus.FAILED, err, isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    buildMode = BuildMode.SOURCE_BUILD,
                    failureCategory = BuildFailureCategory.EXECUTION_ERROR,
                    errorMessage = err
                )
            }

            // 6. Release Signing Validation
            if (isRelease && config.strictReleaseSigning) {
                if (!hasReleaseSigningConfig(projectDir)) {
                    val err = "RELEASE_SIGNING_NOT_CONFIGURED: Production release signing keystore is not configured. Release builds require valid keystore credentials."
                    log(BuildStatus.FAILED, err, isErr = true)
                    return@withContext BuildResult(
                        isSuccess = false,
                        buildMode = BuildMode.SOURCE_BUILD,
                        buildType = BuildType.RELEASE,
                        variant = variant,
                        failureCategory = BuildFailureCategory.RELEASE_SIGNING_NOT_CONFIGURED,
                        errorMessage = err
                    )
                }
            }

            // 7. Resolve Gradle Executable
            val gradlewFile = File(projectDir, if (System.getProperty("os.name")?.lowercase()?.contains("win") == true) "gradlew.bat" else "gradlew")
            val gradleExec = if (gradlewFile.exists()) {
                gradlewFile.setExecutable(true)
                gradlewFile.absolutePath
            } else {
                "gradle"
            }

            log(BuildStatus.CONFIGURING, "Executing Gradle build toolchain: $gradleExec $task...")

            // 8. Execute Real Gradle Build with ProcessBuilder
            val command = listOf(gradleExec, task, "--stacktrace")
            val processBuilder = ProcessBuilder(command)
                .directory(projectDir)
                .redirectErrorStream(true)

            val procEnv = processBuilder.environment()
            System.getenv("ANDROID_SDK_ROOT")?.let { procEnv["ANDROID_SDK_ROOT"] = it }
            System.getenv("ANDROID_HOME")?.let { procEnv["ANDROID_HOME"] = it }
            System.getenv("JAVA_HOME")?.let { procEnv["JAVA_HOME"] = it }

            val process = processBuilder.start()
            activeProcess = process

            val recentOutput = mutableListOf<String>()
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            var line: String? = reader.readLine()
            while (line != null) {
                if (isCancelled.get()) {
                    process.destroyForcibly()
                    break
                }
                val sanitized = sanitizeLog(line)
                recentOutput.add(sanitized)
                if (recentOutput.size > 30) recentOutput.removeAt(0)

                val phase = detectGradlePhase(line)
                if (phase != null && phase != _buildStatus.value) {
                    _buildStatus.value = phase
                }
                log(_buildStatus.value, sanitized)
                line = reader.readLine()
            }

            val exitCode = process.waitFor()
            activeProcess = null

            if (isCancelled.get()) {
                val duration = System.currentTimeMillis() - startTime
                _buildStatus.value = BuildStatus.CANCELLED
                return@withContext BuildResult(
                    isSuccess = false,
                    durationMs = duration,
                    logs = _buildLogs.value,
                    buildMode = BuildMode.SOURCE_BUILD,
                    failureCategory = BuildFailureCategory.CANCELLED,
                    errorMessage = "Build execution was cancelled by user.",
                    exitCode = -1
                )
            }

            // 9. Verify Gradle Exit Code - DO NOT FALL BACK TO DEBUG ON RELEASE FAILURE
            if (exitCode != 0) {
                val duration = System.currentTimeMillis() - startTime
                _buildStatus.value = BuildStatus.FAILED
                val summary = recentOutput.takeLast(10).joinToString("\n")
                val errMsg = "BUILD_FAILED: Gradle exited with non-zero exit code $exitCode for task '$task'.\nRecent output:\n$summary"
                log(BuildStatus.FAILED, errMsg, isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    durationMs = duration,
                    logs = _buildLogs.value,
                    buildMode = BuildMode.SOURCE_BUILD,
                    buildType = config.buildType,
                    variant = variant,
                    failureCategory = BuildFailureCategory.COMPILATION_FAILED,
                    errorMessage = errMsg,
                    exitCode = exitCode
                )
            }

            // 10. Locate Gradle Generated APK
            log(BuildStatus.VERIFYING, "Gradle completed with code 0. Locating generated APK artifact for variant '$variant'...")
            val generatedApk = locateGradleApk(projectDir, variant)
            if (generatedApk == null) {
                val duration = System.currentTimeMillis() - startTime
                _buildStatus.value = BuildStatus.FAILED
                val errMsg = "BUILD_ARTIFACT_MISSING: Gradle exited with code 0 but no APK artifact was found in 'app/build/outputs/apk/$variant/'."
                log(BuildStatus.FAILED, errMsg, isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    durationMs = duration,
                    logs = _buildLogs.value,
                    buildMode = BuildMode.SOURCE_BUILD,
                    buildType = config.buildType,
                    variant = variant,
                    failureCategory = BuildFailureCategory.ARTIFACT_MISSING,
                    errorMessage = errMsg,
                    exitCode = exitCode
                )
            }

            // 11. Validate Generated APK Artifact
            val validation = validateApkArtifact(generatedApk)
            if (!validation.isValid) {
                val duration = System.currentTimeMillis() - startTime
                _buildStatus.value = BuildStatus.FAILED
                val errMsg = validation.errorMessage ?: "BUILD_ARTIFACT_INVALID: Artifact failed integrity checks."
                log(BuildStatus.FAILED, errMsg, isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    durationMs = duration,
                    logs = _buildLogs.value,
                    buildMode = BuildMode.SOURCE_BUILD,
                    buildType = config.buildType,
                    variant = variant,
                    failureCategory = validation.failureCategory ?: BuildFailureCategory.ARTIFACT_INVALID,
                    errorMessage = errMsg,
                    exitCode = exitCode
                )
            }

            // 12. Copy Artifact to Application Artifact Storage
            val artifactsDir = File(context.filesDir, "build_artifacts").apply { mkdirs() }
            val exportFileName = "${config.projectName.lowercase().replace(" ", "_")}_${variant}_v${config.versionName}.apk"
            val outputApk = File(artifactsDir, exportFileName)
            generatedApk.copyTo(outputApk, overwrite = true)

            // 13. Ensure APK Signature
            var sigResult = signingManager.verifyApk(outputApk)
            if (!sigResult.isValid) {
                try {
                    val signedTemp = File(artifactsDir, "signed_${outputApk.name}")
                    signingManager.signApk(outputApk, signedTemp)
                    signedTemp.copyTo(outputApk, overwrite = true)
                    signedTemp.delete()
                    sigResult = signingManager.verifyApk(outputApk)
                } catch (e: Exception) {
                    log(BuildStatus.BUILDING, "Warning: Automated signing step: ${e.message}")
                }
            }

            val sha256 = CryptoUtils.calculateSha256(outputApk)
            val isSigned = sigResult.isValid || validation.isSigned

            if (isRelease && config.strictReleaseSigning && !isSigned) {
                val duration = System.currentTimeMillis() - startTime
                _buildStatus.value = BuildStatus.FAILED
                val errMsg = "RELEASE_SIGNING_NOT_CONFIGURED: Release artifact exists but is not cryptographically signed."
                log(BuildStatus.FAILED, errMsg, isErr = true)
                return@withContext BuildResult(
                    isSuccess = false,
                    durationMs = duration,
                    logs = _buildLogs.value,
                    buildMode = BuildMode.SOURCE_BUILD,
                    buildType = BuildType.RELEASE,
                    variant = variant,
                    failureCategory = BuildFailureCategory.RELEASE_SIGNING_NOT_CONFIGURED,
                    errorMessage = errMsg,
                    exitCode = exitCode
                )
            }

            val duration = System.currentTimeMillis() - startTime
            _buildStatus.value = BuildStatus.COMPLETED
            log(BuildStatus.COMPLETED, "BUILD SUCCESSFUL in ${duration}ms! Artifact: ${outputApk.name} (${outputApk.length()} bytes, SHA-256: $sha256)")

            BuildResult(
                isSuccess = true,
                outputApkFile = outputApk,
                outputSha256 = sha256,
                outputFileSize = outputApk.length(),
                durationMs = duration,
                errorMessage = null,
                logs = _buildLogs.value,
                signatureVerified = isSigned,
                buildMode = BuildMode.SOURCE_BUILD,
                buildType = config.buildType,
                variant = variant,
                exitCode = 0,
                failureCategory = null
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            _buildStatus.value = BuildStatus.FAILED
            val entry = BuildLog(
                timestamp = System.currentTimeMillis(),
                stage = BuildStatus.FAILED,
                message = "Build execution exception: ${e.message}",
                isError = true
            )
            _buildLogs.value = _buildLogs.value + entry
            BuildResult(
                isSuccess = false,
                outputApkFile = null,
                outputSha256 = "",
                outputFileSize = 0L,
                durationMs = duration,
                errorMessage = e.message ?: "Unknown build error",
                logs = _buildLogs.value,
                signatureVerified = false,
                buildMode = BuildMode.SOURCE_BUILD,
                failureCategory = BuildFailureCategory.EXECUTION_ERROR
            )
        } finally {
            activeProcess = null
            buildMutex.unlock()
        }
    }

    suspend fun executeRepack(
        inputApk: File,
        repackConfig: RebuildConfig
    ): BuildResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        _buildLogs.value = emptyList()
        _buildStatus.value = BuildStatus.REBUILDING

        fun log(stage: BuildStatus, msg: String, isErr: Boolean = false) {
            _buildStatus.value = stage
            _buildLogs.value = _buildLogs.value + BuildLog(
                timestamp = System.currentTimeMillis(),
                stage = stage,
                message = msg,
                isError = isErr
            )
        }

        log(BuildStatus.REBUILDING, "APK REPACK: Operating on existing APK '${inputApk.name}' (this is a package repack, NOT a source compilation)...")
        if (!inputApk.exists() || !inputApk.name.endsWith(".apk", ignoreCase = true)) {
            val err = "APK_REPACK failed: Input file does not exist or is not an APK: ${inputApk.path}"
            log(BuildStatus.FAILED, err, isErr = true)
            return@withContext BuildResult(
                isSuccess = false,
                buildMode = BuildMode.APK_REPACK,
                failureCategory = BuildFailureCategory.PROJECT_INVALID,
                errorMessage = err
            )
        }

        val scannerService = com.example.apk.scanner.ApkScannerService(context)
        val pipeline = ApkRebuildPipeline(context, scannerService, signingManager)
        val apkInfo = scannerService.scanApk(inputApk)
        val plan = pipeline.createBuildPlan(apkInfo, repackConfig)
        val result = pipeline.executeRebuild(inputApk, repackConfig, plan)
        val duration = System.currentTimeMillis() - startTime

        if (result.isSuccess) {
            log(BuildStatus.COMPLETED, "APK Repack completed successfully in ${duration}ms.")
            BuildResult(
                isSuccess = true,
                outputApkFile = result.outputApkFile,
                outputSha256 = result.outputSha256,
                outputFileSize = result.outputFileSize,
                durationMs = duration,
                logs = _buildLogs.value,
                signatureVerified = result.signatureVerified,
                buildMode = BuildMode.APK_REPACK,
                buildType = BuildType.RELEASE,
                variant = "repack"
            )
        } else {
            val err = result.errorMessage ?: "APK Repack failed"
            log(BuildStatus.FAILED, err, isErr = true)
            BuildResult(
                isSuccess = false,
                errorMessage = err,
                durationMs = duration,
                logs = _buildLogs.value,
                buildMode = BuildMode.APK_REPACK,
                failureCategory = BuildFailureCategory.COMPILATION_FAILED
            )
        }
    }
}

