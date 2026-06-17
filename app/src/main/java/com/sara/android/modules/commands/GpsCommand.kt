package com.sara.android.modules.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GpsCommand : Command {
    override val name = "gps"
    override val description = "Show GPS/location provider status and last fix"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        var lastGpsFix: android.location.Location? = null
        if (fineGranted) {
            try {
                lastGpsFix = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (_: SecurityException) {}
        }

        return CommandResult.Text(buildString {
            appendLine("🛰️ GPS Diagnostics")
            appendLine()
            appendLine("<b>Providers</b>")
            appendLine("GPS: ${if (gps) "✅ Enabled" else "❌ Disabled"}")
            appendLine("Network: ${if (network) "✅ Enabled" else "❌ Disabled"}")
            appendLine()
            
            if (lastGpsFix != null) {
                appendLine("<b>Last GPS Fix</b>")
                appendLine("Latitude: ${"%.6f".format(lastGpsFix.latitude)}")
                appendLine("Longitude: ${"%.6f".format(lastGpsFix.longitude)}")
                appendLine("Accuracy: ${"%.1f".format(lastGpsFix.accuracy)} m")
                
                val now = System.currentTimeMillis()
                val ageMs = now - lastGpsFix.time
                val ageStr = when {
                    ageMs < 1000 -> "${ageMs}ms ago"
                    ageMs < 60_000 -> "${ageMs / 1000}s ago"
                    ageMs < 3600_000 -> "${ageMs / 60_000}m ${(ageMs % 60_000) / 1000}s ago"
                    else -> "${ageMs / 3600_000}h ${(ageMs % 3600_000) / 60_000}m ago"
                }
                
                val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                appendLine("Time: ${df.format(Date(lastGpsFix.time))}")
                appendLine("Age: $ageStr")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && lastGpsFix.hasSpeedAccuracy()) {
                    appendLine("Speed: ${lastGpsFix.speed} m/s")
                }
            } else {
                appendLine("<b>Last GPS Fix:</b> None cached")
                if (!fineGranted) {
                    appendLine("<i>ACCESS_FINE_LOCATION not granted.</i>")
                }
            }
        })
    }
}
