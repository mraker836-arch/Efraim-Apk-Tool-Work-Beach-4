package com.example.core

import java.io.File
import java.io.IOException

/**
 * Security controls for workspace isolation, path traversal prevention,
 * archive extraction limit checks, and command allowlisting.
 */
object SecurityManager {

    const val MAX_APK_FILE_SIZE_BYTES = 300 * 1024 * 1024L // 300 MB limit
    const val MAX_EXTRACTED_FILES = 8000
    const val MAX_TOTAL_EXTRACTED_SIZE_BYTES = 500 * 1024 * 1024L // 500 MB limit
    const val MAX_COMPRESSION_RATIO = 100 // Compression bomb protection

    private val ALLOWED_COMMANDS = setOf(
        "zipalign",
        "apksigner",
        "d8",
        "aapt2",
        "keytool",
        "stat",
        "sha256sum"
    )

    /**
     * Validates that target file stays strictly inside the parent directory (prevents Zip Slip).
     */
    @Throws(SecurityException::class)
    fun validateSafePath(parentDir: File, destinationFile: File): File {
        val canonicalParent = parentDir.canonicalPath
        val canonicalDest = destinationFile.canonicalPath
        if (!canonicalDest.startsWith(canonicalParent + File.separator) && canonicalDest != canonicalParent) {
            throw SecurityException("Path traversal attempt detected: $canonicalDest is outside $canonicalParent")
        }
        return destinationFile
    }

    /**
     * Validates APK file size prior to processing.
     */
    @Throws(IllegalArgumentException::class)
    fun validateApkFileSize(apkFile: File) {
        if (!apkFile.exists()) {
            throw IllegalArgumentException("Target APK file does not exist: ${apkFile.name}")
        }
        val size = apkFile.length()
        if (size == 0L) {
            throw IllegalArgumentException("Target APK file is empty (0 bytes)")
        }
        if (size > MAX_APK_FILE_SIZE_BYTES) {
            throw IllegalArgumentException("APK file size ($size bytes) exceeds security limit of $MAX_APK_FILE_SIZE_BYTES bytes")
        }
    }

    /**
     * Creates an isolated workspace directory in the application sandbox.
     */
    fun createIsolatedWorkspace(baseDir: File, workspacePrefix: String = "apk_ws"): File {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = (1000..9999).random()
        val workspace = File(baseDir, "${workspacePrefix}_${timestamp}_$randomSuffix")
        if (!workspace.exists()) {
            workspace.mkdirs()
        }
        return workspace
    }

    /**
     * Cleans up a directory recursively with safety checks.
     */
    fun cleanupDirectory(dir: File?) {
        if (dir == null || !dir.exists()) return
        try {
            dir.deleteRecursively()
        } catch (e: Exception) {
            // Ignore cleanup failure
        }
    }

    /**
     * Validates command against an allowlist before execution.
     */
    fun isCommandAllowed(commandName: String): Boolean {
        val baseCommand = commandName.trim().split("\\s+".toRegex()).firstOrNull() ?: return false
        return ALLOWED_COMMANDS.contains(baseCommand.lowercase())
    }
}
