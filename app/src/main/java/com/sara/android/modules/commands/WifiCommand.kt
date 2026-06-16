package com.sara.android.modules.commands

import android.content.Context
import android.net.wifi.WifiManager

class WifiCommand : Command {
    override val name = "wifi"
    override val description = "Show Wi-Fi status"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val enabled = wm.isWifiEnabled

        val info: String
        val signal: String
        if (enabled) {
            val conn = wm.connectionInfo
            info = if (conn != null && conn.ssid != null && conn.ssid != "<unknown ssid>") {
                conn.ssid.trim('"')
            } else {
                "Not connected"
            }
            signal = if (conn != null) {
                val rssi = conn.rssi
                val level = WifiManager.calculateSignalLevel(rssi, 5)
                "\u2591\u2592\u2593\u2588".take(level).padEnd(4, '\u2591') + " ($level/4)"
            } else {
                "N/A"
            }
        } else {
            info = "Wi-Fi disabled"
            signal = "N/A"
        }

        return CommandResult.Text(buildString {
            appendLine("\uD83D\uDCF6 Wi-Fi")
            appendLine()
            appendLine("Enabled: ${if (enabled) "Yes" else "No"}")
            appendLine("SSID: $info")
            appendLine("Signal: $signal")
        })
    }
}
