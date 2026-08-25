package com.example.diagnostics.engine

import com.example.diagnostics.model.DiagnosticErrorRecord
import com.example.diagnostics.model.ErrorCategory
import com.example.diagnostics.model.ErrorSeverity
import com.example.diagnostics.model.ErrorStatus
import com.example.diagnostics.redactor.SensitiveDataRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

data class ErrorTrendingStats(
    val totalErrors: Int,
    val criticalErrors: Int,
    val regularErrors: Int,
    val warnings: Int,
    val infoCount: Int,
    val resolvedErrors: Int,
    val unresolvedErrors: Int,
    val errorsByModule: Map<String, Int>,
    val errorsByRelease: Map<String, Int>,
    val errorsByCategory: Map<ErrorCategory, Int>,
    val hasSufficientData: Boolean
)

class ErrorCenter {

    private val _errors = MutableStateFlow<List<DiagnosticErrorRecord>>(emptyList())
    val errors: StateFlow<List<DiagnosticErrorRecord>> = _errors.asStateFlow()

    private val errorList = CopyOnWriteArrayList<DiagnosticErrorRecord>()

    fun recordError(
        category: ErrorCategory,
        severity: ErrorSeverity,
        message: String,
        module: String,
        stackTrace: String = "",
        releaseVersion: String = "1.0",
        buildVersion: String = "1"
    ): DiagnosticErrorRecord {
        val safeMessage = SensitiveDataRedactor.redact(message)
        val safeStackTrace = SensitiveDataRedactor.redact(stackTrace)

        val record = DiagnosticErrorRecord(
            category = category,
            severity = severity,
            message = safeMessage,
            module = module,
            releaseVersion = releaseVersion,
            buildVersion = buildVersion,
            stackTrace = safeStackTrace,
            status = ErrorStatus.NEW
        )

        errorList.add(0, record)
        _errors.value = errorList.toList()
        return record
    }

    fun updateStatus(
        id: String,
        newStatus: ErrorStatus,
        resolutionNotes: String = ""
    ): Boolean {
        val index = errorList.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = errorList[index]
            val updated = old.copy(
                status = newStatus,
                resolvedAt = if (newStatus == ErrorStatus.RESOLVED) System.currentTimeMillis() else old.resolvedAt,
                resolutionNotes = SensitiveDataRedactor.redact(resolutionNotes)
            )
            errorList[index] = updated
            _errors.value = errorList.toList()
            return true
        }
        return false
    }

    fun clearAllErrors() {
        errorList.clear()
        _errors.value = emptyList()
    }

    fun getTrendingStats(): ErrorTrendingStats {
        val currentList = errorList.toList()
        if (currentList.isEmpty()) {
            return ErrorTrendingStats(
                totalErrors = 0,
                criticalErrors = 0,
                regularErrors = 0,
                warnings = 0,
                infoCount = 0,
                resolvedErrors = 0,
                unresolvedErrors = 0,
                errorsByModule = emptyMap(),
                errorsByRelease = emptyMap(),
                errorsByCategory = emptyMap(),
                hasSufficientData = false
            )
        }

        val total = currentList.size
        val critical = currentList.count { it.severity == ErrorSeverity.CRITICAL }
        val errorsCount = currentList.count { it.severity == ErrorSeverity.ERROR }
        val warnings = currentList.count { it.severity == ErrorSeverity.WARNING }
        val info = currentList.count { it.severity == ErrorSeverity.INFO }
        val resolved = currentList.count { it.status == ErrorStatus.RESOLVED }
        val unresolved = total - resolved

        val byModule = currentList.groupingBy { it.module }.eachCount()
        val byRelease = currentList.groupingBy { it.releaseVersion }.eachCount()
        val byCategory = currentList.groupingBy { it.category }.eachCount()

        return ErrorTrendingStats(
            totalErrors = total,
            criticalErrors = critical,
            regularErrors = errorsCount,
            warnings = warnings,
            infoCount = info,
            resolvedErrors = resolved,
            unresolvedErrors = unresolved,
            errorsByModule = byModule,
            errorsByRelease = byRelease,
            errorsByCategory = byCategory,
            hasSufficientData = true
        )
    }

    fun getErrorsForModule(module: String): List<DiagnosticErrorRecord> {
        return errorList.filter { it.module.equals(module, ignoreCase = true) }
    }

    fun getUnresolvedCriticalErrors(): List<DiagnosticErrorRecord> {
        return errorList.filter { it.severity == ErrorSeverity.CRITICAL && it.status != ErrorStatus.RESOLVED }
    }
}
