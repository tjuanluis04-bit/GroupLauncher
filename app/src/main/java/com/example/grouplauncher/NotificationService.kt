package com.example.grouplauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Conteo de notificaciones activas por paquete (para las insignias). */
object Badges {
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
}

class NotificationService : NotificationListenerService() {
    private fun refresh() {
        val list: Array<StatusBarNotification> = try { activeNotifications } catch (e: Exception) { return }
        Badges.counts = list
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 && !it.isOngoing }
            .groupingBy { it.packageName }.eachCount()
    }
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
}
