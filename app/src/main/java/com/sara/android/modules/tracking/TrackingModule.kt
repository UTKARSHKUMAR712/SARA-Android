package com.sara.android.modules.tracking

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.content.SharedPreferences
import com.sara.android.events.EventBus
import com.sara.android.events.TrackingUpdate
import com.sara.android.events.TrackingUpdateEvent
import com.sara.android.events.TrackingSummary
import com.sara.android.events.TrackingSessionEvent
import com.sara.android.runtime.Module
import com.sara.android.ui.LogBuffer
import java.util.concurrent.atomic.AtomicInteger

enum class TrackingMode {
    LIVE, BALANCED, LOW_POWER
}

class TrackingModule : Module {

    override val name = "TrackingModule"

    companion object {
        private const val PREFS_NAME = "sara_tracking"
        private const val KEY_ACTIVE = "tracking_active"
        private const val KEY_CHAT_ID = "tracking_chat_id"
        private const val KEY_START_TIME = "tracking_start_time"
        private const val KEY_UPDATES = "tracking_updates"
        private const val KEY_DISTANCE = "tracking_total_distance"
        private const val KEY_MODE = "tracking_mode"
        private const val KEY_DISTANCE_THRESHOLD = "tracking_distance_threshold"
        private const val KEY_INTERVAL = "tracking_interval"
        private const val KEY_LAST_LAT = "tracking_last_lat"
        private const val KEY_LAST_LNG = "tracking_last_lng"
        private const val KEY_LAST_ACCURACY = "tracking_last_accuracy"
        private const val KEY_LAST_TIME = "tracking_last_time"
        private const val KEY_LAST_PROVIDER = "tracking_last_provider"

        @Volatile
        private var instance: TrackingModule? = null

        fun getInstance(): TrackingModule? = instance
    }

    private var appContext: Context? = null
    private var engine: TrackingEngine? = null

    override fun onStart(context: Context) {
        appContext = context.applicationContext
        instance = this
        engine = TrackingEngine(appContext!!)
        engine!!.restore()
    }

    override fun onStop() {
        engine?.saveState()
        engine?.stopListening()
        engine = null
        instance = null
    }

    fun isTracking(): Boolean = engine?.isTracking == true

    fun start(chatId: Long, mode: TrackingMode? = null): String =
        engine?.start(chatId, mode) ?: "Tracking engine not initialized."

    fun stop(): String = engine?.stop() ?: "Tracking engine not initialized."

    fun status(): String = engine?.status() ?: "Tracking engine not initialized."

    fun setMode(mode: TrackingMode): String = engine?.let {
        it.setMode(mode)
        "Tracking mode set to ${mode.name}."
    } ?: "Tracking engine not initialized."

    fun setDistance(meters: Float): String = engine?.let {
        it.setDistance(meters)
        if (meters <= 0f) "Distance threshold disabled."
        else "Distance threshold set to ${meters.toInt()}m."
    } ?: "Tracking engine not initialized."

    fun setInterval(intervalMs: Long): String = engine?.let {
        it.setInterval(intervalMs)
        "Update interval set to ${intervalMs / 1000}s."
    } ?: "Tracking engine not initialized."

    fun getTrackingChatId(): Long = engine?.chatId ?: 0L

    private class TrackingEngine(private val context: Context) {

        var isTracking = false; private set
        var mode = TrackingMode.BALANCED; private set
        var distanceThreshold = 0f; private set
        var intervalMs = 30_000L; private set
        var chatId = 0L; private set

        private var startTime = 0L
        private var totalUpdates = 0
        private var totalDistance = 0f
        private var lastLocation: Location? = null
        private var lastUpdateTime = 0L

        private var lm: LocationManager? = null
        private var locationListener: LocationListener? = null
        private var thread: HandlerThread? = null
        private var handler: Handler? = null

        private val log get() = appContext?.let { LogBuffer.getInstance(it) }
        private var appContext: Context? = null

        init {
            this.appContext = context.applicationContext
        }

        fun start(chatId: Long, requestedMode: TrackingMode?): String {
            if (isTracking) {
                return "Tracking already active. Updates are being sent to chat $this.chatId."
            }

            requestedMode?.let { mode = it }

            val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!fineGranted) {
                return "Location permission not granted. Grant ACCESS_FINE_LOCATION in Settings."
            }

            this.chatId = chatId
            isTracking = true
            startTime = System.currentTimeMillis()
            totalUpdates = 0
            totalDistance = 0f
            lastLocation = null
            lastUpdateTime = 0L

            startListening()
            saveState()
            log?.info("Tracking", "Started — mode=${mode.name}, interval=${intervalMs}ms, distanceThreshold=${distanceThreshold}m")
            return "Tracking started.\nMode: ${mode.name}\nInterval: ${intervalMs / 1000}s\nDistance threshold: ${if (distanceThreshold <= 0f) "None" else "${distanceThreshold.toInt()}m"}"
        }

        fun stop(): String {
            if (!isTracking) return "Tracking is not active."

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val avgSpeed = if (totalUpdates > 1 && duration > 0) {
                (totalDistance / (duration / 1000f))
            } else 0f

            stopListening()
            isTracking = false

            val summary = TrackingSummary(
                chatId = chatId,
                startTime = startTime,
                endTime = endTime,
                totalUpdates = totalUpdates,
                totalDistance = totalDistance,
                averageSpeed = avgSpeed
            )
            EventBus.publish(TrackingSessionEvent(summary))

            val durSec = duration / 1000
            val hrs = durSec / 3600
            val mins = (durSec % 3600) / 60
            val secs = durSec % 60

            clearState()
            log?.info("Tracking", "Stopped — updates=$totalUpdates, distance=${"%.1f".format(totalDistance)}m, duration=${hrs}h ${mins}m ${secs}s")

            return buildString {
                appendLine("Tracking stopped.")
                appendLine()
                appendLine("Start: ${formatTime(startTime)}")
                appendLine("End: ${formatTime(endTime)}")
                appendLine("Duration: ${hrs}h ${mins}m ${secs}s")
                appendLine("Updates sent: $totalUpdates")
                appendLine("Total distance: ${"%.1f".format(totalDistance)} m")
                if (avgSpeed > 0f) {
                    appendLine("Avg speed: ${"%.2f".format(avgSpeed)} m/s")
                }
            }
        }

        fun status(): String {
            if (!isTracking) return "Tracking is not active."

            val now = System.currentTimeMillis()
            val duration = now - startTime
            val durSec = duration / 1000
            val hrs = durSec / 3600
            val mins = (durSec % 3600) / 60
            val secs = durSec % 60

            return buildString {
                appendLine("Tracking: RUNNING")
                appendLine()
                appendLine("Mode: ${mode.name}")
                appendLine("Interval: ${intervalMs / 1000}s")
                appendLine("Distance threshold: ${if (distanceThreshold <= 0f) "None" else "${distanceThreshold.toInt()}m"}")
                appendLine("Duration: ${hrs}h ${mins}m ${secs}s")
                appendLine("Updates sent: $totalUpdates")
                appendLine("Total distance: ${"%.1f".format(totalDistance)} m")
                if (lastLocation != null) {
                    appendLine()
                    appendLine("Last location:")
                    appendLine("  ${"%.6f".format(lastLocation!!.latitude)}, ${"%.6f".format(lastLocation!!.longitude)}")
                    appendLine("  Accuracy: ${"%.1f".format(lastLocation!!.accuracy)} m")
                    appendLine("  ${formatTime(lastLocation!!.time)}")
                }
            }
        }

        fun setMode(newMode: TrackingMode) {
            mode = newMode
            if (isTracking) {
                stopListening()
                startListening()
            }
            saveState()
        }

        fun setDistance(meters: Float) {
            distanceThreshold = meters.coerceAtLeast(0f)
            saveState()
        }

        fun setInterval(ms: Long) {
            intervalMs = ms.coerceAtLeast(1000L)
            saveState()
        }

        fun restore() {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ACTIVE, false)) return

            val savedChatId = prefs.getLong(KEY_CHAT_ID, 0)
            if (savedChatId == 0L) return

            val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!fineGranted) {
                clearState()
                return
            }

            mode = try { TrackingMode.valueOf(prefs.getString(KEY_MODE, "BALANCED") ?: "BALANCED") } catch (_: Exception) { TrackingMode.BALANCED }
            distanceThreshold = prefs.getFloat(KEY_DISTANCE_THRESHOLD, 0f)
            intervalMs = prefs.getLong(KEY_INTERVAL, 30_000L)
            chatId = savedChatId
            isTracking = true
            startTime = prefs.getLong(KEY_START_TIME, System.currentTimeMillis())
            totalUpdates = prefs.getInt(KEY_UPDATES, 0)
            totalDistance = prefs.getFloat(KEY_DISTANCE, 0f)

            val lastLat = prefs.getFloat(KEY_LAST_LAT, Float.NaN)
            if (!lastLat.isNaN()) {
                val lastLng = prefs.getFloat(KEY_LAST_LNG, Float.NaN)
                val lastAcc = prefs.getFloat(KEY_LAST_ACCURACY, 0f)
                val lastTime = prefs.getLong(KEY_LAST_TIME, 0L)
                val lastProv = prefs.getString(KEY_LAST_PROVIDER, "unknown") ?: "unknown"
                val loc = Location(lastProv).apply {
                    latitude = lastLat.toDouble()
                    longitude = lastLng.toDouble()
                    accuracy = lastAcc
                    time = lastTime
                }
                lastLocation = loc
            }

            startListening()
            log?.info("Tracking", "Restored — mode=${mode.name}, updates=$totalUpdates, distance=${"%.1f".format(totalDistance)}m")
        }

        fun saveState() {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_ACTIVE, isTracking)
                putLong(KEY_CHAT_ID, chatId)
                if (isTracking) {
                    putLong(KEY_START_TIME, startTime)
                    putInt(KEY_UPDATES, totalUpdates)
                    putFloat(KEY_DISTANCE, totalDistance)
                    putString(KEY_MODE, mode.name)
                    putFloat(KEY_DISTANCE_THRESHOLD, distanceThreshold)
                    putLong(KEY_INTERVAL, intervalMs)
                    if (lastLocation != null) {
                        putFloat(KEY_LAST_LAT, lastLocation!!.latitude.toFloat())
                        putFloat(KEY_LAST_LNG, lastLocation!!.longitude.toFloat())
                        putFloat(KEY_LAST_ACCURACY, lastLocation!!.accuracy)
                        putLong(KEY_LAST_TIME, lastLocation!!.time)
                        putString(KEY_LAST_PROVIDER, lastLocation!!.provider ?: "unknown")
                    }
                }
                apply()
            }
        }

        private fun clearState() {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }

        private fun startListening() {
            stopListening()
            lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            thread = HandlerThread("TrackingLocation").apply { start() }
            handler = Handler(thread!!.looper)

            val minTime: Long
            val minDist: Float

            when (mode) {
                TrackingMode.LIVE -> {
                    minTime = 0L
                    minDist = 0f
                }
                TrackingMode.BALANCED -> {
                    minTime = 2000L
                    minDist = 5f
                }
                TrackingMode.LOW_POWER -> {
                    minTime = 10000L
                    minDist = 20f
                }
            }

            locationListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    onLocationUpdate(loc)
                }
                override fun onStatusChanged(p: String?, s: Int, extras: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }

            try {
                if (lm!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    if (mode != TrackingMode.LOW_POWER) {
                        lm!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDist, locationListener!!, handler)
                    }
                }
                if (lm!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm!!.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDist.coerceAtLeast(10f), locationListener!!, handler)
                }
            } catch (_: SecurityException) {}
        }

        fun stopListening() {
            locationListener?.let { lm?.removeUpdates(it) }
            locationListener = null
            thread?.quitSafely()
            thread = null
            handler = null
            lm = null
        }

        private fun onLocationUpdate(loc: Location) {
            if (!isTracking) return

            if (!shouldSend(loc)) return

            val now = System.currentTimeMillis()
            val prev = lastLocation
            var distanceDelta = 0f
            if (prev != null) {
                distanceDelta = loc.distanceTo(prev)
            }
            totalDistance += distanceDelta
            totalUpdates++
            lastLocation = loc
            lastUpdateTime = now

            val sessionDuration = now - startTime
            val update = TrackingUpdate(
                chatId = chatId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracy = loc.accuracy,
                provider = loc.provider ?: "unknown",
                locationTime = loc.time,
                speed = if (loc.hasSpeed()) loc.speed else 0f,
                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                updateNumber = totalUpdates,
                sessionDuration = sessionDuration,
                totalDistance = totalDistance
            )
            EventBus.publish(TrackingUpdateEvent(update))
            saveState()
        }

        private fun shouldSend(loc: Location): Boolean {
            if (lastLocation == null) return true

            val prev = lastLocation ?: return true
            val timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime

            val age = loc.time.let { t -> if (t > 0) System.currentTimeMillis() - t else Long.MAX_VALUE }
            if (age > 120_000) return false

            if (loc.accuracy > 100f && mode == TrackingMode.LIVE) return false
            if (loc.accuracy > 500f) return false

            val distance = loc.distanceTo(prev)
            if (distance < 1f && loc.accuracy >= prev.accuracy) return false

            if (distanceThreshold > 0f && distance < distanceThreshold) {
                if (timeSinceLastUpdate < intervalMs.coerceAtMost(120_000L)) {
                    return false
                }
            }

            if (timeSinceLastUpdate < intervalMs && distance < distanceThreshold.coerceAtLeast(10f)) {
                return false
            }

            if (prev.hasAccuracy() && loc.hasAccuracy() &&
                loc.accuracy > prev.accuracy * 1.5f && distance < 20f) {
                return false
            }

            return true
        }

        private fun formatTime(millis: Long): String {
            val df = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            return df.format(java.util.Date(millis))
        }
    }
}
