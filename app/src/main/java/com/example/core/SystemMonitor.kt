package com.example.core

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

data class SystemMetrics(
    val allocatedRamMb: Long,
    val freeRamMb: Long,
    val maxRamMb: Long,
    val ramUsagePercent: Float,
    val availableStorageMb: Long,
    val totalStorageMb: Long,
    val storageUsagePercent: Float,
    val activeThreadsCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

object SystemMonitor {

    fun captureMetrics(context: Context): SystemMetrics {
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory()
        val freeMem = runtime.freeMemory()
        val maxMem = runtime.maxMemory()
        val usedMem = totalMem - freeMem

        val allocatedMb = usedMem / (1024 * 1024)
        val freeMb = (maxMem - usedMem) / (1024 * 1024)
        val maxMb = maxMem / (1024 * 1024)
        val ramPercent = if (maxMem > 0) (usedMem.toFloat() / maxMem.toFloat()) * 100f else 0f

        val stat = StatFs(context.filesDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availableStorageMb = availableBytes / (1024 * 1024)
        val totalStorageMb = totalBytes / (1024 * 1024)
        val usedStorage = totalBytes - availableBytes
        val storagePercent = if (totalBytes > 0) (usedStorage.toFloat() / totalBytes.toFloat()) * 100f else 0f

        val threadCount = Thread.activeCount()

        return SystemMetrics(
            allocatedRamMb = allocatedMb,
            freeRamMb = freeMb,
            maxRamMb = maxMb,
            ramUsagePercent = ramPercent.coerceIn(0f, 100f),
            availableStorageMb = availableStorageMb,
            totalStorageMb = totalStorageMb,
            storageUsagePercent = storagePercent.coerceIn(0f, 100f),
            activeThreadsCount = threadCount
        )
    }
}
