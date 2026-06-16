package com.sara.android.events

open class Event {
    val timestamp: Long = System.currentTimeMillis()
}

class ServiceStartEvent : Event()
class ServiceStopEvent : Event()

enum class ServiceStatus { STARTING, RUNNING, STOPPED, ERROR }

class ServiceStatusEvent(val status: ServiceStatus, val message: String = "") : Event()

class HealthEvent(
    val uptime: Long,
    val memoryUsage: Long,
    val moduleCount: Int,
    val eventCount: Int,
    val lastError: String?
) : Event()

class TelegramStatusEvent(val connected: Boolean, val message: String = "") : Event()

class AppEvent(val packageName: String, val action: String) : Event()
class MediaEvent(val packageName: String, val isPlaying: Boolean) : Event()
class BatteryEvent(val level: Int, val isCharging: Boolean) : Event()
class NetworkEvent(val isConnected: Boolean, val type: String) : Event()
class ScreenEvent(val screenOn: Boolean) : Event()
class BootEvent : Event()
class NotificationEvent(val packageName: String, val title: String) : Event()
class ModuleEvent(val moduleName: String, val action: String, val data: Any? = null) : Event()

data class TrackingUpdate(
    val chatId: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String,
    val locationTime: Long,
    val speed: Float,
    val bearing: Float,
    val updateNumber: Int,
    val sessionDuration: Long,
    val totalDistance: Float
)

class TrackingUpdateEvent(val update: TrackingUpdate) : Event()

data class TrackingSummary(
    val chatId: Long,
    val startTime: Long,
    val endTime: Long,
    val totalUpdates: Int,
    val totalDistance: Float,
    val averageSpeed: Float
)

class TrackingSessionEvent(val summary: TrackingSummary) : Event()
