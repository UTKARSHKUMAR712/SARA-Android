package com.sara.android.runtime

import android.content.Context
import com.sara.android.events.EventBus
import com.sara.android.events.HealthEvent
import com.sara.android.events.ServiceStartEvent
import com.sara.android.events.ServiceStatus
import com.sara.android.events.ServiceStatusEvent
import com.sara.android.events.ServiceStopEvent
import com.sara.android.modules.automation.AutomationEngine
import com.sara.android.modules.jobs.JobScheduler
import com.sara.android.modules.llm.LlmModule
import com.sara.android.modules.media.MediaModule
import com.sara.android.modules.notifications.NotificationModule
import com.sara.android.modules.rules.RuleEngine
import com.sara.android.modules.storage.StorageModule
import com.sara.android.modules.telegram.TelegramModule
import com.sara.android.modules.watchers.WatcherModule

class SaraRuntime(private val context: Context) {

    private val modules = mutableListOf<Module>()
    private var started = false
    var startTime: Long = 0
        private set
    var lastError: String? = null
        private set
    private var _eventCount = 0

    val eventCount: Int get() = _eventCount
    val moduleCount: Int get() = modules.size
    val isStarted: Boolean get() = started

    fun build(): SaraRuntime {
        modules.clear()
        modules.add(WatcherModule())
        modules.add(RuleEngine())
        modules.add(JobScheduler())
        modules.add(AutomationEngine())
        modules.add(NotificationModule())
        modules.add(TelegramModule())
        modules.add(MediaModule())
        modules.add(LlmModule())
        modules.add(StorageModule())
        return this
    }

    fun start() {
        if (started) return
        started = true
        startTime = System.currentTimeMillis()
        EventBus.publish(ServiceStatusEvent(ServiceStatus.STARTING))
        for (module in modules) {
            try {
                module.onStart(context)
                _eventCount++
            } catch (e: Exception) {
                lastError = "[${module.name}] ${e.message}"
                _eventCount++
            }
        }
        EventBus.publish(ServiceStartEvent())
        EventBus.publish(ServiceStatusEvent(ServiceStatus.RUNNING))
        publishHealth()
    }

    fun stop() {
        if (!started) return
        started = false
        EventBus.publish(ServiceStopEvent())
        EventBus.publish(ServiceStatusEvent(ServiceStatus.STOPPED))
        for (module in modules.asReversed()) {
            try {
                module.onStop()
                _eventCount++
            } catch (_: Exception) {}
        }
        publishHealth()
    }

    fun publishHealth() {
        val rt = Runtime.getRuntime()
        EventBus.publish(
            HealthEvent(
                uptime = if (started) System.currentTimeMillis() - startTime else 0,
                memoryUsage = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
                moduleCount = modules.size,
                eventCount = _eventCount,
                lastError = lastError
            )
        )
    }
}
