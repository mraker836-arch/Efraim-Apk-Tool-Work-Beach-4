package com.example.diagnostics.engine

import android.content.Context
import com.example.diagnostics.model.CrashReport
import com.example.diagnostics.model.ErrorCategory
import com.example.diagnostics.model.ErrorSeverity
import com.example.diagnostics.redactor.SensitiveDataRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

class GlobalErrorHandler(
    private val errorCenter: ErrorCenter,
    private val appVersion: String = "1.0",
    private val buildNumber: String = "1"
) : Thread.UncaughtExceptionHandler {

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    private val _crashes = MutableStateFlow<List<CrashReport>>(emptyList())
    val crashes: StateFlow<List<CrashReport>> = _crashes.asStateFlow()

    private val crashList = CopyOnWriteArrayList<CrashReport>()

    fun install() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            captureCrash(throwable, "UncaughtException: Thread[${thread.name}]")
        } catch (e: Throwable) {
            // Diagnostic engine must never throw or escalate
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun captureCrash(
        throwable: Throwable,
        module: String = "Runtime"
    ): CrashReport {
        val safeMessage = SensitiveDataRedactor.redact(throwable.localizedMessage ?: throwable.javaClass.simpleName)
        val safeStackTrace = SensitiveDataRedactor.redactStackTrace(throwable)
        val exceptionType = throwable.javaClass.name

        val crashReport = CrashReport(
            exceptionType = exceptionType,
            safeMessage = safeMessage,
            stackTrace = safeStackTrace,
            module = module,
            applicationVersion = appVersion,
            buildNumber = buildNumber,
            platform = "Android API " + android.os.Build.VERSION.SDK_INT
        )

        crashList.add(0, crashReport)
        _crashes.value = crashList.toList()

        // Also record to ErrorCenter as CRITICAL RUNTIME error
        errorCenter.recordError(
            category = ErrorCategory.RUNTIME,
            severity = ErrorSeverity.CRITICAL,
            message = "$exceptionType: $safeMessage",
            module = module,
            stackTrace = safeStackTrace,
            releaseVersion = appVersion,
            buildVersion = buildNumber
        )

        return crashReport
    }

    fun clearCrashes() {
        crashList.clear()
        _crashes.value = emptyList()
    }
}
