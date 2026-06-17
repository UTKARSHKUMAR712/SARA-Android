package com.sara.android.modules.jobs

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.sara.android.runtime.Module
import com.sara.android.runtime.SaraRuntime
import com.sara.android.ui.LogBuffer

class WatchdogManager(private val runtime: SaraRuntime) : Module {
    override val name = "WatchdogManager"

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var isRunning = false
    private var context: Context? = null

    private val checkIntervalMs = 60000L // Every 1 minute

    override fun onStart(context: Context) {
        this.context = context.applicationContext
        thread = HandlerThread("WatchdogThread").apply { start() }
        handler = Handler(thread!!.looper)
        isRunning = true
        
        LogBuffer.getInstance(context).info(name, "Watchdog started")
        handler?.postDelayed(watchdogRunnable, checkIntervalMs)
    }

    override fun onStop() {
        isRunning = false
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                checkModules()
            } catch (e: Exception) {
                context?.let {
                    LogBuffer.getInstance(it).error(name, "Watchdog failure: ${e.message}")
                }
            } finally {
                handler?.postDelayed(this, checkIntervalMs)
            }
        }
    }

    private fun checkModules() {
        val modules = runtime.getModules()
        val log = LogBuffer.getInstance(context!!)
        
        for (module in modules) {
            // We check the lastError and isInitialized properties.
            // If a module has a recent critical error or died, we try to restart it.
            val status = module.getHealthStatus()
            if (!status.isHealthy) {
                log.warn(name, "Module ${module.name} is unhealthy. Attempting restart...")
                try {
                    module.onStop()
                    module.onStart(context!!)
                    log.info(name, "Successfully restarted ${module.name}")
                } catch (e: Exception) {
                    log.error(name, "Failed to restart ${module.name}: ${e.message}")
                }
            }
        }
    }
}
