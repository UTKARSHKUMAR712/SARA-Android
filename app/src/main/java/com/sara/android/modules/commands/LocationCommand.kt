package com.sara.android.modules.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.sara.android.ui.LogBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationCommand : Command {
    override val name = "location"
    override val description = "Get current location (GPS preferred)"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val log = LogBuffer.getInstance(context)
        val startTime = System.currentTimeMillis()

        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            return CommandResult.Text(
                "\uD83D\uDCCD Location permission not granted.\n" +
                "Grant ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION " +
                "via Settings \u2192 Apps \u2192 SARA \u2192 Permissions."
            )
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            return CommandResult.Text(
                "\uD83D\uDCCD Location services are disabled.\n" +
                "Enable GPS/Location in Settings \u2192 Location."
            )
        }

        val freshLatch = CountDownLatch(1)
        var bestLocation: Location? = null

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (bestLocation == null || loc.accuracy < bestLocation!!.accuracy) {
                    bestLocation = loc
                }
                if (loc.accuracy < 10f) {
                    freshLatch.countDown()
                }
            }
            override fun onStatusChanged(p: String?, s: Int, extras: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }

        if (gpsEnabled) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
            } catch (_: SecurityException) {}
        }
        if (networkEnabled) {
            try {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener)
            } catch (_: SecurityException) {}
        }

        val gotFresh = freshLatch.await(8, TimeUnit.SECONDS)
        lm.removeUpdates(listener)

        if (gotFresh && bestLocation != null) {
            val elapsed = System.currentTimeMillis() - startTime
            val provider = bestLocation!!.provider ?: "unknown"
            log.info(name, "Fresh location: provider=$provider, accuracy=${bestLocation!!.accuracy}m, time=${elapsed}ms")
            return formatLocation(bestLocation!!, "Fresh", provider)
        }

        if (bestLocation != null) {
            val elapsed = System.currentTimeMillis() - startTime
            val provider = bestLocation!!.provider ?: "unknown"
            log.info(name, "Best available (timeout): provider=$provider, accuracy=${bestLocation!!.accuracy}m, time=${elapsed}ms")
            return formatLocation(bestLocation!!, "Fresh", provider)
        }

        log.info(name, "Fresh location timed out after 8s, falling back to cached")

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var cachedLocation: Location? = null
        for (p in providers) {
            try {
                val loc = lm.getLastKnownLocation(p)
                if (loc != null && (cachedLocation == null || loc.accuracy < cachedLocation!!.accuracy)) {
                    cachedLocation = loc
                }
            } catch (_: SecurityException) {}
        }

        if (cachedLocation != null) {
            val elapsed = System.currentTimeMillis() - startTime
            val provider = cachedLocation!!.provider ?: "unknown"
            log.info(name, "Cached location: provider=$provider, accuracy=${cachedLocation!!.accuracy}m, time=${elapsed}ms")
            return formatLocation(cachedLocation!!, "Cached", provider)
        }

        return CommandResult.Text(
            "\uD83D\uDCCD Could not obtain location.\n" +
            "No GPS or Network location data available. " +
            "Try moving to an open area and ensure location is enabled."
        )
    }

    private fun formatLocation(loc: Location, source: String, provider: String): CommandResult {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timeStr = df.format(Date(loc.time))

        val now = System.currentTimeMillis()
        val ageMs = now - loc.time
        val ageStr = when {
            ageMs < 1000 -> "${ageMs}ms ago"
            ageMs < 60_000 -> "${ageMs / 1000}s ago"
            ageMs < 3600_000 -> "${ageMs / 60_000}m ${(ageMs % 60_000) / 1000}s ago"
            else -> "${ageMs / 3600_000}h ${(ageMs % 3600_000) / 60_000}m ago"
        }

        return CommandResult.Text(buildString {
            appendLine("\uD83D\uDCCD Location")
            appendLine()
            appendLine("Source: $source $provider")
            appendLine("Latitude: ${"%.6f".format(loc.latitude)}")
            appendLine("Longitude: ${"%.6f".format(loc.longitude)}")
            appendLine("Accuracy: ${"%.1f".format(loc.accuracy)} m")
            appendLine("Provider: ${loc.provider ?: provider}")
            appendLine("Timestamp: $timeStr")
            appendLine("Age: $ageStr")
            appendLine()
            appendLine("https://www.google.com/maps?q=${"%.6f".format(loc.latitude)},${"%.6f".format(loc.longitude)}")
        })
    }
}
