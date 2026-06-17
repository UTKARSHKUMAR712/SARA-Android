package com.sara.android.modules.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.sara.android.ui.LogBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                "📍 Location permission not granted.\n" +
                "Grant ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION " +
                "via Settings → Apps → SARA → Permissions."
            )
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        
        if (context is com.sara.android.service.SaraForegroundService) {
            context.addForegroundType(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
        
        var location: Location? = null
        try {
            val task = fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            location = Tasks.await(task, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.warn(name, "getCurrentLocation failed: ${e.message}")
        }

        if (location == null) {
            try {
                val lastTask = fusedClient.lastLocation
                location = Tasks.await(lastTask, 2, TimeUnit.SECONDS)
            } catch (e: Exception) {
                log.warn(name, "getLastLocation failed: ${e.message}")
            }
        }

        if (location != null) {
            if (context is com.sara.android.service.SaraForegroundService) {
                context.removeForegroundType(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            }
            val elapsed = System.currentTimeMillis() - startTime
            val provider = location.provider ?: "fused"
            log.info(name, "Location found: provider=$provider, accuracy=${location.accuracy}m, time=${elapsed}ms")
            return formatLocation(location, "Fused", provider)
        }

        if (context is com.sara.android.service.SaraForegroundService) {
                context.removeForegroundType(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            }
            
        return CommandResult.Text(
            "📍 Could not obtain location.\n" +
            "No location data available even after 10s timeout. " +
            "Ensure device Location is enabled."
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
            appendLine("📍 Location")
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
