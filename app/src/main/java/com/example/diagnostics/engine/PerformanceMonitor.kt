package com.example.diagnostics.engine

import com.example.diagnostics.model.PerformanceCategory
import com.example.diagnostics.model.PerformanceMetricRecord
import com.example.diagnostics.model.PerformanceRating
import com.example.diagnostics.model.PerformanceThresholds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

data class PerformanceRegressionResult(
    val hasRegression: Boolean,
    val category: PerformanceCategory,
    val currentDurationMs: Long,
    val previousBaselineMs: Long,
    val percentageIncrease: Float,
    val message: String
)

class PerformanceMonitor(
    initialThresholds: PerformanceThresholds = PerformanceThresholds()
) {

    private val _thresholds = MutableStateFlow(initialThresholds)
    val thresholds: StateFlow<PerformanceThresholds> = _thresholds.asStateFlow()

    private val _metrics = MutableStateFlow<List<PerformanceMetricRecord>>(emptyList())
    val metrics: StateFlow<List<PerformanceMetricRecord>> = _metrics.asStateFlow()

    private val metricList = CopyOnWriteArrayList<PerformanceMetricRecord>()

    fun updateThresholds(newThresholds: PerformanceThresholds) {
        _thresholds.value = newThresholds
    }

    fun recordMeasurement(
        category: PerformanceCategory,
        durationMs: Long,
        contextInfo: String = "",
        isFailure: Boolean = false
    ): PerformanceMetricRecord {
        val rating = if (isFailure) {
            PerformanceRating.FAILED
        } else if (durationMs < 0) {
            PerformanceRating.NOT_AVAILABLE
        } else {
            calculateRating(category, durationMs)
        }

        val record = PerformanceMetricRecord(
            category = category,
            durationMs = durationMs,
            rating = rating,
            contextInfo = contextInfo
        )

        metricList.add(0, record)
        _metrics.value = metricList.toList()
        return record
    }

    inline fun <T> measureTimedBlock(
        category: PerformanceCategory,
        contextInfo: String = "",
        block: () -> T
    ): T {
        val start = System.currentTimeMillis()
        var failed = false
        try {
            return block()
        } catch (e: Throwable) {
            failed = true
            throw e
        } finally {
            val duration = System.currentTimeMillis() - start
            recordMeasurement(category, duration, contextInfo, failed)
        }
    }

    fun calculateRating(category: PerformanceCategory, durationMs: Long): PerformanceRating {
        if (durationMs < 0) return PerformanceRating.NOT_AVAILABLE
        val t = _thresholds.value

        val (fastLimit, normalLimit) = when (category) {
            PerformanceCategory.STARTUP -> Pair(t.startupFastMs, t.startupNormalMs)
            PerformanceCategory.SCREEN_LOAD -> Pair(500L, 1200L)
            PerformanceCategory.APK_IMPORT -> Pair(t.apkImportFastMs, t.apkImportNormalMs)
            PerformanceCategory.APK_SCAN -> Pair(t.apkScanFastMs, t.apkScanNormalMs)
            PerformanceCategory.APK_ANALYSIS -> Pair(t.apkAnalysisFastMs, t.apkAnalysisNormalMs)
            PerformanceCategory.REBUILD -> Pair(t.rebuildFastMs, t.rebuildNormalMs)
            PerformanceCategory.EXPORT -> Pair(t.exportFastMs, t.exportNormalMs)
            PerformanceCategory.AI_RESPONSE -> Pair(t.aiResponseFastMs, t.aiResponseNormalMs)
        }

        return when {
            durationMs <= fastLimit -> PerformanceRating.FAST
            durationMs <= normalLimit -> PerformanceRating.NORMAL
            else -> PerformanceRating.SLOW
        }
    }

    fun checkRegression(
        category: PerformanceCategory,
        currentDurationMs: Long
    ): PerformanceRegressionResult {
        val historyForCategory = metricList.filter {
            it.category == category && it.rating != PerformanceRating.FAILED && it.durationMs > 0
        }

        if (historyForCategory.size < 2) {
            return PerformanceRegressionResult(
                hasRegression = false,
                category = category,
                currentDurationMs = currentDurationMs,
                previousBaselineMs = -1L,
                percentageIncrease = 0f,
                message = "INSUFFICIENT HISTORICAL DATA"
            )
        }

        // Calculate average baseline excluding current/latest
        val baselineSamples = historyForCategory.drop(1)
        val baselineAverage = baselineSamples.map { it.durationMs }.average().toLong()

        if (baselineAverage <= 0) {
            return PerformanceRegressionResult(
                hasRegression = false,
                category = category,
                currentDurationMs = currentDurationMs,
                previousBaselineMs = baselineAverage,
                percentageIncrease = 0f,
                message = "BASELINE DATA INSUFFICIENT"
            )
        }

        val increase = (currentDurationMs - baselineAverage).toFloat() / baselineAverage.toFloat()
        val percentIncrease = increase * 100f

        // Regression flagged if >= 50% slower than baseline
        val isRegression = percentIncrease >= 50f

        val message = if (isRegression) {
            "PERFORMANCE REGRESSION DETECTED: ${category.label} took ${currentDurationMs}ms (Baseline: ${baselineAverage}ms, +${String.format(java.util.Locale.US, "%.1f", percentIncrease)}%)"
        } else {
            "Performance is within baseline parameters (${currentDurationMs}ms vs ${baselineAverage}ms baseline)"
        }

        return PerformanceRegressionResult(
            hasRegression = isRegression,
            category = category,
            currentDurationMs = currentDurationMs,
            previousBaselineMs = baselineAverage,
            percentageIncrease = percentIncrease,
            message = message
        )
    }

    fun clearHistory() {
        metricList.clear()
        _metrics.value = emptyList()
    }
}
