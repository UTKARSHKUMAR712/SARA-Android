package com.sara.android.runtime

import android.content.Context
import com.sara.android.events.EventBus
import com.sara.android.events.ServiceStartEvent
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

    fun build(): SaraRuntime {
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
        for (module in modules) {
            try {
                module.onStart(context)
            } catch (_: Exception) {}
        }
        EventBus.publish(ServiceStartEvent())
    }

    fun stop() {
        if (!started) return
        started = false
        EventBus.publish(ServiceStopEvent())
        for (module in modules.asReversed()) {
            try {
                module.onStop()
            } catch (_: Exception) {}
        }
    }
}
