package com.sara.android.modules.commands

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.sara.android.R

class NotifyCommand : Command {
    override val name = "notify"
    override val description = "Send a local notification: /notify <text>"

    override fun execute(context: Context, args: List<String>): String {
        if (args.isEmpty()) return "Usage: /notify <message text>"

        val text = args.joinToString(" ")

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "SARA command notifications" }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("\uD83E\uDD16 SARA Command")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)

        return "\uD83D\uDD14 Notification sent: $text"
    }

    companion object {
        private const val CHANNEL_ID = "sara_commands"
        private const val CHANNEL_NAME = "SARA Commands"
    }
}
