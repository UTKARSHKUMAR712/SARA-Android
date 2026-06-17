package com.sara.android.modules.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.sara.android.events.AppEvent
import com.sara.android.events.EventBus
import com.sara.android.runtime.Module

class AppModule : Module, Runnable {
    override val name = "AppModule"

    private var appContext: Context? = null
    private var usageStatsManager: UsageStatsManager? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var isPolling = false
    private var lastEventTime = System.currentTimeMillis()

    override fun onStart(context: Context) {
        try {
            appContext = context.applicationContext
            usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            
            thread = HandlerThread("AppModuleThread").apply { start() }
            handler = Handler(thread!!.looper)
            isPolling = true
            handler?.post(this)
        } catch (e: Exception) {
            com.sara.android.ui.LogBuffer.getInstance(context).error(name, "Failed to start: ${e.message}")
        }
    }

    override fun onStop() {
        isPolling = false
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
        usageStatsManager = null
    }

    override fun run() {
        if (!isPolling) return
        
        try {
            val now = System.currentTimeMillis()
            val events = usageStatsManager?.queryEvents(lastEventTime, now)
            
            if (events != null) {
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        EventBus.publish(AppEvent(event.packageName, "opened"))
                    } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                        EventBus.publish(AppEvent(event.packageName, "closed"))
                    }
                }
            }
            lastEventTime = now
        } catch (e: Exception) {
            appContext?.let {
                com.sara.android.ui.LogBuffer.getInstance(it).error(name, "Polling error: ${e.message}")
            }
        }

        handler?.postDelayed(this, 2000)
    }
}
