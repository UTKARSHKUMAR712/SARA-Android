package com.sara.android.modules.commands

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

class LockCommand : Command {
    override val name = "lock"
    override val description = "Lock the device immediately"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, SaraDeviceAdminReceiver::class.java)

        if (!dpm.isAdminActive(admin)) {
            return CommandResult.Text("❌ Device admin not enabled.\n" +
                    "Open SARA Dashboard → Settings → Enable Device Admin.")
        }

        try {
            dpm.lockNow()
            return CommandResult.Text("\uD83D\uDD12 Device locked successfully.")
        } catch (e: SecurityException) {
            return CommandResult.Text("❌ Lock failed: ${e.message}")
        }
    }
}
