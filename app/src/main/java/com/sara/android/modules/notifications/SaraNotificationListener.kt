package com.sara.android.modules.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sara.android.events.EventBus
import com.sara.android.events.NotificationEvent
import com.sara.android.ui.LogBuffer

class SaraNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        
        EventBus.publish(NotificationEvent(packageName, title))
        
        // Example: ignore our own notifications
        if (packageName == applicationContext.packageName) return
        
        LogBuffer.getInstance(applicationContext).info("NotificationListener", "Posted: $packageName - $title")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: track removed notifications
    }
}
