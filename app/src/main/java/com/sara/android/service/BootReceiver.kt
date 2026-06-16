package com.sara.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Future: enable and start service on boot
            // val serviceIntent = Intent(context, SaraForegroundService::class.java)
            // context.startForegroundService(serviceIntent)
        }
    }
}
