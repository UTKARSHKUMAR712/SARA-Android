package com.sara.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.sara.android.R

class SaraForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var counter = 0
    private var lastError: String? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            counter++
            try {
                val text = lastError ?: "App is running... (${counter}s)"
                updateNotification(text)
                lastError = null
            } catch (e: Exception) {
                lastError = "Error: ${e.message}"
                updateNotification(lastError!!)
            }
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Starting SARA...")
        startForeground(NOTIFICATION_ID, notification)
        handler.post(tickRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SARA background service notifications"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "sara_foreground_service"
        private const val CHANNEL_NAME = "SARA Service"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_TITLE = "SARA"
    }
}
