package com.sara.android.modules.commands

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.sara.android.modules.tracking.TrackingMode
import com.sara.android.modules.tracking.TrackingModule
import com.sara.android.ui.LogBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TrackCommand : Command {
    override val name = "track"
    override val description = "Location tracking commands. Subcommands: start, stop, status, once, live, balanced, lowpower, distance, interval"

    companion object {
        @Volatile
        var lastChatId: Long = 0L
    }

    override fun execute(context: Context, args: List<String>): CommandResult {
        val log = LogBuffer.getInstance(context)
        val sub = args.firstOrNull()?.lowercase() ?: return help()

        return when (sub) {
            "start" -> handleStart(context, args.drop(1))
            "stop" -> handleStop(context)
            "status" -> handleStatus()
            "once" -> handleOnce(context)
            "live" -> handleMode(context, TrackingMode.LIVE)
            "balanced" -> handleMode(context, TrackingMode.BALANCED)
            "lowpower", "low_power" -> handleMode(context, TrackingMode.LOW_POWER)
            "distance" -> handleDistance(context, args.drop(1))
            "interval" -> handleInterval(context, args.drop(1))
            "help" -> help()
            else -> help()
        }
    }

    private fun help(): CommandResult = CommandResult.Text(
        "Track commands:\n" +
        "/track start — Start tracking\n" +
        "/track stop — Stop tracking\n" +
        "/track status — Show tracking status\n" +
        "/track once — Get one fresh location\n" +
        "/track live — High accuracy, frequent updates\n" +
        "/track balanced — Moderate battery usage\n" +
        "/track lowpower — Battery saver mode\n" +
        "/track distance <meters> — Movement threshold\n" +
        "/track interval <duration> — Update interval\n" +
        "/track help — This message"
    )

    private fun handleStart(context: Context, extra: List<String>): CommandResult {
        val tm = TrackingModule.getInstance()
        if (tm == null) return CommandResult.Text("Tracking module not available.")

        val mode = extra.firstOrNull()?.lowercase()?.let { m ->
            when (m) {
                "live" -> TrackingMode.LIVE
                "balanced" -> TrackingMode.BALANCED
                "lowpower", "low_power" -> TrackingMode.LOW_POWER
                else -> null
            }
        }

        val chatId = lastChatId
        if (chatId == 0L) {
            return CommandResult.Text("Chat ID not set. Use /track start from Telegram.")
        }

        val result = tm.start(chatId, mode)
        return CommandResult.Text(result)
    }

    private fun handleStop(context: Context): CommandResult {
        val tm = TrackingModule.getInstance()
        if (tm == null) return CommandResult.Text("Tracking module not available.")
        return CommandResult.Text(tm.stop())
    }

    private fun handleStatus(): CommandResult {
        val tm = TrackingModule.getInstance()
        if (tm == null) return CommandResult.Text("Tracking module not available.")
        return CommandResult.Text(tm.status())
    }

    private fun handleOnce(context: Context): CommandResult {
        return getFreshLocation(context)
    }

    private fun handleMode(context: Context, mode: TrackingMode): CommandResult {
        val tm = TrackingModule.getInstance()
        if (tm == null) return CommandResult.Text("Tracking module not available.")
        return CommandResult.Text(tm.setMode(mode))
    }

    private fun handleDistance(context: Context, args: List<String>): CommandResult {
        if (args.isEmpty()) return CommandResult.Text("Usage: /track distance <meters>")
        val meters = args[0].toFloatOrNull()
        if (meters == null || meters < 0f) return CommandResult.Text("Invalid distance. Use a positive number in meters.")
        val tm = TrackingModule.getInstance()
        if (tm == null) return CommandResult.Text("Tracking module not available.")
        return CommandResult.Text(tm.setDistance(meters))
    }

    private fun handleInterval(context: Context, args: List<String>): CommandResult {
        if (args.isEmpty()) return CommandResult.Text("Usage: /track interval <duration> (e.g. 10s, 30s, 1m, 5m)")
        val ms = parseDuration(args[0])
        if (ms == null || ms < 1000L) return CommandResult.Text("Invalid interval. Use format like 10s, 30s, 1m, 5m.")
        val tm = TrackingModule.getInstance()
        if (tm == null) return CommandResult.Text("Tracking module not available.")
        return CommandResult.Text(tm.setInterval(ms))
    }

    private fun parseDuration(input: String): Long? {
        val regex = Regex("^(\\d+)\\s*(s|sec|m|min|h|hr)\$", RegexOption.IGNORE_CASE)
        val match = regex.find(input.trim()) ?: return null
        val value = match.groupValues[1].toLongOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "s", "sec" -> value * 1000L
            "m", "min" -> value * 60_000L
            "h", "hr" -> value * 3600_000L
            else -> null
        }
    }

    private fun getFreshLocation(context: Context): CommandResult {
        val log = LogBuffer.getInstance(context)

        val fineGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

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

        val latch = CountDownLatch(1)
        var best: Location? = null

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (best == null || loc.accuracy < best!!.accuracy) {
                    best = loc
                }
                if (loc.accuracy < 10f) latch.countDown()
            }
            override fun onStatusChanged(p: String?, s: Int, extras: Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }

        if (gpsEnabled) {
            try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener) } catch (_: SecurityException) {}
        }
        if (networkEnabled) {
            try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener) } catch (_: SecurityException) {}
        }

        val gotFix = latch.await(10, TimeUnit.SECONDS)
        lm.removeUpdates(listener)

        val loc = best ?: run {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var cached: Location? = null
            for (p in providers) {
                try {
                    val l = lm.getLastKnownLocation(p)
                    if (l != null && (cached == null || l.accuracy < cached.accuracy)) cached = l
                } catch (_: SecurityException) {}
            }
            cached
        }

        if (loc == null) {
            return CommandResult.Text("Could not obtain location. Try moving to an open area and ensure location is enabled.")
        }

        val provider = loc.provider ?: "unknown"
        val source = if (gotFix) "Fresh" else "Cached"
        log.info("TrackOnce", "Location: source=$source, provider=$provider, accuracy=${loc.accuracy}m")

        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timeStr = df.format(Date(loc.time))

        val now = System.currentTimeMillis()
        val ageMs = now - loc.time
        val ageStr = when {
            ageMs < 1000 -> "${ageMs}ms ago"
            ageMs < 60_000 -> "${ageMs / 1000}s ago"
            else -> "${ageMs / 60_000}m ${(ageMs % 60_000) / 1000}s ago"
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
            if (loc.hasSpeed()) appendLine("Speed: ${"%.1f".format(loc.speed)} m/s")
            if (loc.hasBearing()) appendLine("Bearing: ${"%.1f".format(loc.bearing)}°")
            appendLine()
            appendLine("https://www.google.com/maps?q=${"%.6f".format(loc.latitude)},${"%.6f".format(loc.longitude)}")
        })
    }
}
