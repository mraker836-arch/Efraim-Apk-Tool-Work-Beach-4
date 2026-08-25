package com.example.diagnostics.engine

import com.example.diagnostics.model.AlertNotification
import com.example.diagnostics.model.AlertType
import com.example.diagnostics.model.ErrorSeverity
import com.example.diagnostics.redactor.SensitiveDataRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

interface NotificationProvider {
    val name: String
    fun sendAlert(notification: AlertNotification): Boolean
}

class InAppNotificationProvider : NotificationProvider {
    override val name: String = "In-App UI"

    private val _notifications = MutableStateFlow<List<AlertNotification>>(emptyList())
    val notifications: StateFlow<List<AlertNotification>> = _notifications.asStateFlow()

    private val alertList = CopyOnWriteArrayList<AlertNotification>()

    override fun sendAlert(notification: AlertNotification): Boolean {
        alertList.add(0, notification)
        _notifications.value = alertList.toList()
        return true
    }

    fun markAsRead(id: String) {
        val index = alertList.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = alertList[index].copy(isRead = true)
            alertList[index] = updated
            _notifications.value = alertList.toList()
        }
    }

    fun clearAll() {
        alertList.clear()
        _notifications.value = emptyList()
    }
}

class NotificationService(
    private val providers: List<NotificationProvider> = listOf(InAppNotificationProvider())
) {

    private val primaryInAppProvider = providers.filterIsInstance<InAppNotificationProvider>().firstOrNull()
        ?: InAppNotificationProvider().also { /* fallback */ }

    val notifications: StateFlow<List<AlertNotification>> = primaryInAppProvider.notifications

    fun postAlert(
        type: AlertType,
        severity: ErrorSeverity,
        title: String,
        message: String
    ): AlertNotification {
        val safeTitle = SensitiveDataRedactor.redact(title)
        val safeMessage = SensitiveDataRedactor.redact(message)

        val alert = AlertNotification(
            type = type,
            severity = severity,
            title = safeTitle,
            message = safeMessage
        )

        for (provider in providers) {
            try {
                provider.sendAlert(alert)
            } catch (e: Exception) {
                // Providers fail safely
            }
        }
        return alert
    }

    fun markAlertRead(id: String) {
        primaryInAppProvider.markAsRead(id)
    }

    fun clearAlerts() {
        primaryInAppProvider.clearAll()
    }
}
