package com.sara.android.modules.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat

class LocationCommand : Command {
    override val name = "location"
    override val description = "Show last known location"

    override fun execute(context: Context, args: List<String>): String {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return "\uD83D\uDCCD Location permission not granted.\n" +
                    "Grant ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION via Settings → Apps → SARA → Permissions."
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLat = Double.NaN
        var bestLon = Double.NaN
        var bestProvider = ""

        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null && (bestLat.isNaN() || loc.accuracy < lm.getLastKnownLocation(bestProvider)?.accuracy ?: Float.MAX_VALUE)) {
                    bestLat = loc.latitude
                    bestLon = loc.longitude
                    bestProvider = provider
                }
            } catch (_: SecurityException) {}
        }

        if (bestLat.isNaN()) {
            return "\uD83D\uDCCD No cached location found.\n" +
                    "Open Google Maps or any navigation app to get a fix, then try again."
        }

        return buildString {
            appendLine("\uD83D\uDCCD Location")
            appendLine()
            appendLine("Latitude: ${"%.6f".format(bestLat)}")
            appendLine("Longitude: ${"%.6f".format(bestLon)}")
            appendLine("Provider: $bestProvider")
            appendLine()
            appendLine("https://www.google.com/maps?q=${"%.6f".format(bestLat)},${"%.6f".format(bestLon)}")
        }
    }
}
