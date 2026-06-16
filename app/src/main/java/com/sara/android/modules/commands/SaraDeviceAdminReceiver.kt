package com.sara.android.modules.commands

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.sara.android.ui.LogBuffer

class SaraDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        LogBuffer.getInstance(context).info("DeviceAdmin", "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        LogBuffer.getInstance(context).warn("DeviceAdmin", "Device admin disabled")
    }
}
