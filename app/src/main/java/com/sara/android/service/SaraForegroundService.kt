package com.sara.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sara.android.R
import com.sara.android.events.EventBus
import com.sara.android.runtime.SaraRuntime

class SaraForegroundService : Service() {

    private val binder = LocalBinder()
    lateinit var runtime: SaraRuntime
        private set

    inner class LocalBinder : Binder() {
        fun getService(): SaraForegroundService = this@SaraForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        runtime = SaraRuntime(this).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESTART -> {
                scheduleRestart = true
                runtime.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                runtime.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification())
                } catch (_: Exception) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification())
                }
                runtime.start()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        if (scheduleRestart) {
            scheduleRestart = false
            val intent = Intent(this, SaraForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_TEXT)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sara_foreground_service"
        private const val CHANNEL_NAME = "SARA Service"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_TITLE = "SARA"
        private const val NOTIFICATION_TEXT = "Running in background"

        const val ACTION_RESTART = "com.sara.android.action.RESTART"
        const val ACTION_STOP = "com.sara.android.action.STOP"

        private var scheduleRestart = false
    }
}
