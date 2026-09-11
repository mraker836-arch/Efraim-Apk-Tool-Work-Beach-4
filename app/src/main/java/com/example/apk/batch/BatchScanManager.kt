package com.example.apk.batch

import android.content.Context
import android.net.Uri
import com.example.apk.model.ApkScanResult
import com.example.apk.scanner.ApkScanPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class BatchItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val scanResult: ApkScanResult? = null,
    val errorMessage: String? = null
)

enum class BatchItemStatus {
    PENDING,
    SCANNING,
    COMPLETED,
    FAILED
}

data class BatchSummary(
    val totalFiles: Int,
    val completedCount: Int,
    val failedCount: Int,
    val totalFindings: Int,
    val highRiskCount: Int,
    val totalBytesProcessed: Long
)

class BatchScanManager(
    private val context: Context,
    private val pipeline: ApkScanPipeline
) {
    private val _queue = MutableStateFlow<List<BatchItem>>(emptyList())
    val queue: StateFlow<List<BatchItem>> = _queue.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentBatchSummary = MutableStateFlow<BatchSummary?>(null)
    val currentBatchSummary: StateFlow<BatchSummary?> = _currentBatchSummary.asStateFlow()

    fun enqueue(uris: List<Uri>) {
        val pairs = uris.map { uri ->
            var name = uri.lastPathSegment ?: "unknown.apk"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex) ?: name
                        }
                    }
                }
            } catch (_: Exception) {}
            Pair(uri, name)
        }
        enqueueFiles(pairs)
    }

    fun enqueueFiles(urisWithNames: List<Pair<Uri, String>>) {
        val newItems = urisWithNames.map { (uri, name) ->
            var size = 0L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {}

            BatchItem(
                uri = uri,
                fileName = name,
                fileSize = size,
                status = BatchItemStatus.PENDING
            )
        }
        _queue.value = _queue.value + newItems
    }

    fun clearQueue() {
        if (!_isProcessing.value) {
            _queue.value = emptyList()
            _currentBatchSummary.value = null
        }
    }

    suspend fun executeBatch(onProgress: ((completed: Int, total: Int, currentName: String) -> Unit)? = null) {
        if (_isProcessing.value || _queue.value.isEmpty()) return
        _isProcessing.value = true

        val items = _queue.value.toMutableList()
        val total = items.size

        for (i in items.indices) {
            val item = items[i]
            if (item.status == BatchItemStatus.COMPLETED) continue

            items[i] = item.copy(status = BatchItemStatus.SCANNING)
            _queue.value = items.toList()
            onProgress?.invoke(i, total, item.fileName)

            val scanResult = pipeline.scanFromUri(context, item.uri, item.fileName)
            scanResult.fold(
                onSuccess = { res ->
                    items[i] = item.copy(
                        status = BatchItemStatus.COMPLETED,
                        scanResult = res,
                        fileSize = res.fileInfo.fileSize
                    )
                },
                onFailure = { err ->
                    items[i] = item.copy(
                        status = BatchItemStatus.FAILED,
                        errorMessage = err.localizedMessage ?: err.message ?: "Analysis failed"
                    )
                }
            )
            _queue.value = items.toList()
        }

        // Generate batch summary
        val completed = items.count { it.status == BatchItemStatus.COMPLETED }
        val failed = items.count { it.status == BatchItemStatus.FAILED }
        val findings = items.mapNotNull { it.scanResult }.sumOf { it.securityFindings.size }
        val highRisk = items.mapNotNull { it.scanResult }.count { res ->
            res.securityFindings.any { it.severity.name == "CRITICAL" || it.severity.name == "HIGH" }
        }
        val bytes = items.mapNotNull { it.scanResult }.sumOf { it.fileInfo.fileSize }

        _currentBatchSummary.value = BatchSummary(
            totalFiles = total,
            completedCount = completed,
            failedCount = failed,
            totalFindings = findings,
            highRiskCount = highRisk,
            totalBytesProcessed = bytes
        )

        _isProcessing.value = false
    }

    /**
     * Purges temporary sandbox cache files generated during batch processing.
     */
    fun purgeBatchSandbox() {
        try {
            val cacheDir = java.io.File(context.cacheDir, "apk_scans")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".apk")) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
