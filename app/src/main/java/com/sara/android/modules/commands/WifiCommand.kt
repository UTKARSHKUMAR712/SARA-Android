package com.sara.android.modules.commands

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

class WifiCommand : Command {
    override val name = "wifi"
    override val description = "Show Wi-Fi status"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val enabled = wm.isWifiEnabled
        
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isWifiTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        
        val info: String
        val signal: String
        
        if (enabled) {
            if (isWifiTransport) {
                val conn = wm.connectionInfo
                info = if (conn != null && conn.ssid != null && conn.ssid != "<unknown ssid>") {
                    conn.ssid.trim('"')
                } else {
                    "Connected (SSID hidden/requires location permission)"
                }
                signal = if (conn != null) {
                    val rssi = conn.rssi
                    val level = WifiManager.calculateSignalLevel(rssi, 5)
                    "░▒▓█".take(level).padEnd(4, '░') + " ($level/4) [${rssi}dBm]"
                } else {
                    "N/A"
                }
            } else {
                info = "Enabled but not connected to any network"
                signal = "N/A"
            }
        } else {
            info = "Wi-Fi disabled"
            signal = "N/A"
        }

        return CommandResult.Text(buildString {
            appendLine("📶 <b>Wi-Fi Diagnostics</b>")
            appendLine()
            appendLine("<b>State:</b> ${if (enabled) "✅ Enabled" else "❌ Disabled"}")
            appendLine("<b>Network:</b> $info")
            if (signal != "N/A") {
                appendLine("<b>Signal:</b> $signal")
            }
        })
    }
}
