package com.sara.android.modules.commands

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.sara.android.modules.media.ScreenshotModule

class PermissionsCommand : Command {
    override val name = "permissions"
    override val description = "Check the current status of all sensitive SARA permissions"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val notifGranted = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )?.contains(context.packageName) == true

        val usageGranted = try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }

        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val batteryGranted = pm.isIgnoringBatteryOptimizations(context.packageName)

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = ComponentName(context, SaraDeviceAdminReceiver::class.java)
        val adminGranted = dpm.isAdminActive(adminComponent)

        val screenGranted = ScreenshotModule.instance?.mediaProjection != null

        return CommandResult.Text(buildString {
            appendLine("🛡️ SARA Permission Status")
            appendLine()
            appendLine(if (notifGranted) "✅ Notifications" else "❌ Notifications (/notify, auto-reply)")
            appendLine(if (cameraGranted) "✅ Camera" else "❌ Camera (/camera)")
            appendLine(if (locationGranted) "✅ Location" else "❌ Location (/location, /track)")
            appendLine(if (screenGranted) "✅ Screen Capture" else "❌ Screen Capture (/screenshot)")
            appendLine(if (usageGranted) "✅ Usage Stats" else "❌ Usage Stats (app trigger rules)")
            appendLine(if (adminGranted) "✅ Device Admin" else "❌ Device Admin (/lock)")
            appendLine(if (batteryGranted) "✅ Battery Opt Exempt" else "❌ Battery Opt Exempt (reliability)")
            appendLine()
            appendLine("To enable disabled features, open the SARA app Dashboard on your device.")
        })
    }
}
