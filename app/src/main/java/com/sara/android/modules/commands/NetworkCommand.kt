package com.sara.android.modules.commands

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkCommand : Command {
    override val name = "network"
    override val description = "Show network connectivity status"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val transport = when {
            wifi -> "Wi-Fi"
            cellular -> "Mobile"
            ethernet -> "Ethernet"
            vpn -> "VPN"
            else -> "None"
        }

        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true

        val ip = try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "N/A"
        } catch (_: Exception) { "N/A" }

        return CommandResult.Text(buildString {
            appendLine("🌐 <b>Network Diagnostics</b>")
            appendLine()
            appendLine("<b>Connection:</b> ${if (connected) "✅ Online" else "❌ Offline"}")
            appendLine("<b>Transport:</b> $transport")
            if (vpn) appendLine("<b>VPN:</b> ✅ Active")
            appendLine("<b>Metered:</b> ${if (metered) "Yes" else "No (Unmetered)"}")
            appendLine("<b>Local IP:</b> <code>$ip</code>")
        })
    }
}
