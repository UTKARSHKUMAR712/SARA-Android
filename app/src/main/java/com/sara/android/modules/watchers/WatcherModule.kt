package com.sara.android.modules.watchers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.BatteryManager
import com.sara.android.events.BatteryEvent
import com.sara.android.events.EventBus
import com.sara.android.events.NetworkEvent
import com.sara.android.runtime.Module
import com.sara.android.ui.LogBuffer

class WatcherModule : Module {
    override val name = "WatcherModule"
    
    private var appContext: Context? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStart(context: Context) {
        appContext = context.applicationContext
        
        try {
            setupBatteryWatcher()
            setupNetworkWatcher()
        } catch (e: Exception) {
            LogBuffer.getInstance(context).error(name, "Failed to start watchers: ${e.message}")
        }
    }

    override fun onStop() {
        try {
            if (batteryReceiver != null) {
                appContext?.unregisterReceiver(batteryReceiver)
                batteryReceiver = null
            }
            if (networkCallback != null) {
                val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(networkCallback!!)
                networkCallback = null
            }
        } catch (e: Exception) {
            appContext?.let { LogBuffer.getInstance(it).error(name, "Failed to stop watchers: ${e.message}") }
        }
    }
    
    private fun setupBatteryWatcher() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL
                
                if (level != -1 && scale != -1) {
                    val batteryPct = (level * 100) / scale
                    EventBus.publish(BatteryEvent(batteryPct, isCharging))
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        appContext?.registerReceiver(batteryReceiver, filter)
    }

    private fun setupNetworkWatcher() {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm?.getNetworkCapabilities(network)
                val type = if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true) "Wi-Fi" else "Cellular"
                EventBus.publish(NetworkEvent(true, type))
            }

            override fun onLost(network: Network) {
                EventBus.publish(NetworkEvent(false, "Unknown"))
            }
        }
        cm?.registerDefaultNetworkCallback(networkCallback!!)
    }
}
