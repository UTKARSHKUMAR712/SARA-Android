package com.sara.android.events

open class Event {
    val timestamp: Long = System.currentTimeMillis()
}

class ServiceStartEvent : Event()
class ServiceStopEvent : Event()

class AppEvent(val packageName: String, val action: String) : Event()
class MediaEvent(val packageName: String, val isPlaying: Boolean) : Event()
class BatteryEvent(val level: Int, val isCharging: Boolean) : Event()
class NetworkEvent(val isConnected: Boolean, val type: String) : Event()
class ScreenEvent(val screenOn: Boolean) : Event()
class BootEvent : Event()
class NotificationEvent(val packageName: String, val title: String) : Event()
class ModuleEvent(val moduleName: String, val action: String, val data: Any? = null) : Event()
