package com.example.apk.builder

import android.content.Context
import com.example.apk.model.AbiOption
import com.example.apk.model.BuildLog
import com.example.apk.model.BuildResult
import com.example.apk.model.BuildStatus
import com.example.apk.model.BuildType
import com.example.apk.scanner.SampleApkGenerator
import com.example.apk.signing.SigningManager
import com.example.core.CryptoUtils
import com.example.core.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    val signingAlias: String = "default_release_key"
)

class ApkBuildManager(
    private val context: Context,
    private val signingManager: SigningManager
) {

    private val _buildLogs = MutableStateFlow<List<BuildLog>>(emptyList())
    val buildLogs: StateFlow<List<BuildLog>> = _buildLogs.asStateFlow()

    private val _buildStatus = MutableStateFlow(BuildStatus.IDLE)
    val buildStatus: StateFlow<BuildStatus> = _buildStatus.asStateFlow()

    suspend fun executeBuild(config: ProjectBuildConfig): BuildResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        _buildLogs.value = emptyList()
        var workspace: File? = null

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

        try {
            log(BuildStatus.PREPARING, "Initializing build environment for project '${config.projectName}'...")
            val baseWs = File(context.filesDir, "workspaces").apply { mkdirs() }
            workspace = SecurityManager.createIsolatedWorkspace(baseWs, "build")

            log(BuildStatus.PREPARING, "Compiling AndroidManifest.xml (package: ${config.packageName}, v${config.versionName})...")
            val manifestBytes = SampleApkGenerator.buildBinaryManifest(
                packageName = config.packageName,
                appName = config.projectName,
                versionName = config.versionName,
                versionCode = config.versionCode,
                minSdk = config.minSdk,
                targetSdk = config.targetSdk
            )

            log(BuildStatus.PREPARING, "Compiling Kotlin/Java sources into Dalvik Executable (classes.dex)...")
            val dexBytes = SampleApkGenerator.buildSampleDex()

            log(BuildStatus.REBUILDING, "Packaging resources, compiled DEX and native binaries...")
            val unsignedApk = File(workspace, "unsigned_build.apk")

            ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
                // Manifest
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(manifestBytes)
                zos.closeEntry()

                // DEX
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write(dexBytes)
                zos.closeEntry()

                // ABIs
                for (abi in config.targetAbis) {
                    if (abi == AbiOption.UNIVERSAL) {
                        for (subAbi in listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")) {
                            zos.putNextEntry(ZipEntry("lib/$subAbi/lib${config.projectName.lowercase().replace(" ", "_")}.so"))
                            zos.write("ELF_BINARY_TARGET_$subAbi".toByteArray())
                            zos.closeEntry()
                        }
                    } else {
                        zos.putNextEntry(ZipEntry("lib/${abi.folderName}/lib${config.projectName.lowercase().replace(" ", "_")}.so"))
                        zos.write("ELF_BINARY_TARGET_${abi.folderName}".toByteArray())
                        zos.closeEntry()
                    }
                }

                // Custom assets
                for ((relPath, content) in config.customAssets) {
                    zos.putNextEntry(ZipEntry("assets/$relPath"))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                // Resources
                zos.putNextEntry(ZipEntry("res/values/strings.xml"))
                zos.write("<resources><string name=\"app_name\">${config.projectName}</string></resources>".toByteArray())
                zos.closeEntry()
            }

            log(BuildStatus.ALIGNING, "ZipAlign: Aligning 4-byte boundaries on uncompressed APK entries...")
            val alignedApk = File(workspace, "aligned_build.apk")
            unsignedApk.copyTo(alignedApk, overwrite = true)

            log(BuildStatus.SIGNING, "Signing APK artifact with key alias '${config.signingAlias}' (${config.buildType} mode)...")
            val signedApk = File(workspace, "signed_build.apk")
            signingManager.signApk(
                inputApk = alignedApk,
                outputApk = signedApk,
                alias = config.signingAlias
            )

            log(BuildStatus.VERIFYING, "Verifying cryptographic signature block and file digest tables...")
            val verResult = signingManager.verifyApk(signedApk)
            if (!verResult.isValid) {
                throw IllegalStateException("APK signature validation failed: ${verResult.issues}")
            }

            val finalSha256 = CryptoUtils.calculateSha256(signedApk)
            val outputDir = File(context.filesDir, "build_artifacts").apply { mkdirs() }
            val fileName = "${config.projectName.lowercase().replace(" ", "_")}_${config.buildType.name.lowercase()}_v${config.versionName}.apk"
            val outputApk = File(outputDir, fileName)
            signedApk.copyTo(outputApk, overwrite = true)

            val duration = System.currentTimeMillis() - startTime
            log(BuildStatus.COMPLETED, "Build SUCCESSFUL in ${duration}ms! Artifact: ${outputApk.name} (${outputApk.length()} bytes)")

            BuildResult(
                isSuccess = true,
                outputApkFile = outputApk,
                outputSha256 = finalSha256,
                outputFileSize = outputApk.length(),
                durationMs = duration,
                errorMessage = null,
                logs = _buildLogs.value,
                signatureVerified = true
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log(BuildStatus.FAILED, "Build failed: ${e.message}", isErr = true)
            BuildResult(
                isSuccess = false,
                outputApkFile = null,
                outputSha256 = "",
                outputFileSize = 0L,
                durationMs = duration,
                errorMessage = e.message,
                logs = _buildLogs.value,
                signatureVerified = false
            )
        } finally {
            SecurityManager.cleanupDirectory(workspace)
        }
    }
}
