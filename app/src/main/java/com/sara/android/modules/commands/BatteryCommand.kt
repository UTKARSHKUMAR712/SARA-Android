package com.sara.android.modules.commands

import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryCommand : Command {
    override val name = "battery"
    override val description = "Show battery status"

    override fun execute(context: Context, args: List<String>): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) return "Could not read battery state."

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (scale > 0) (level * 100 / scale) else level

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }

        val plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugStr = when {
            plug == BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            plug == BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            plug == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }

        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val tempC = if (temp >= 0) temp / 10.0 else -1.0

        return buildString {
            appendLine("\uD83D\uDD0B Battery")
            appendLine()
            appendLine("Level: $pct%")
            appendLine("Status: $statusStr")
            if (plugStr != "None") appendLine("Power source: $plugStr")
            appendLine("Health: $healthStr")
            if (tempC >= 0) appendLine("Temperature: ${"%.1f".format(tempC)}°C")
        }
    }
}
