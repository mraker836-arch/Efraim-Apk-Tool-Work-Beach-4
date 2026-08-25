package com.example.diagnostics.engine

import com.example.diagnostics.redactor.SensitiveDataRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val details: String,
    val userRole: String = "OWNER",
    val timestamp: Long = System.currentTimeMillis()
)

class AuditLogService {

    private val _logs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logs: StateFlow<List<AuditLogEntry>> = _logs.asStateFlow()

    private val logList = CopyOnWriteArrayList<AuditLogEntry>()

    fun logEvent(
        action: String,
        details: String,
        userRole: String = "OWNER"
    ): AuditLogEntry {
        val safeAction = SensitiveDataRedactor.redact(action)
        val safeDetails = SensitiveDataRedactor.redact(details)

        val entry = AuditLogEntry(
            action = safeAction,
            details = safeDetails,
            userRole = userRole
        )

        logList.add(0, entry)
        _logs.value = logList.toList()
        return entry
    }

    fun clearLogs() {
        logList.clear()
        _logs.value = emptyList()
    }
}
