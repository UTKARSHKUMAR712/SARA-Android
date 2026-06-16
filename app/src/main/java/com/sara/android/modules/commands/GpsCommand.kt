package com.sara.android.modules.commands

import android.content.Context
import android.location.LocationManager

class GpsCommand : Command {
    override val name = "gps"
    override val description = "Show GPS/location provider status"

    override fun execute(context: Context, args: List<String>): String {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        return buildString {
            appendLine("\uD83D\uDCCD GPS / Location")
            appendLine()
            appendLine("GPS (satellite): ${if (gps) "Enabled" else "Disabled"}")
            appendLine("Network (Wi-Fi/cell): ${if (network) "Enabled" else "Disabled"}")
            if (!gps && !network) {
                appendLine()
                appendLine("Location services appear to be off.")
                appendLine("Enable them in Settings → Location.")
            }
        }
    }
}
