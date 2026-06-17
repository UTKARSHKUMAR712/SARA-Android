package com.sara.android.modules.commands

import android.content.Context
import android.os.Build

class DiagnosticsCommand : Command {
    override val name = "diagnostics"
    override val description = "View full system and module health diagnostics"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val runtime = DiagnosticsManager.runtime ?: return CommandResult.Text("Error: Runtime not available")
        
        val sb = java.lang.StringBuilder()
        sb.append("📊 <b>SARA System Diagnostics</b>\n\n")
        
        // System Info
        sb.append("<b>Device:</b> ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("<b>Android:</b> ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        val uptimeSec = (System.currentTimeMillis() - runtime.startTime) / 1000
        val hrs = uptimeSec / 3600
        val mins = (uptimeSec % 3600) / 60
        sb.append("<b>Uptime:</b> ${hrs}h ${mins}m\n")

        val rt = Runtime.getRuntime()
        val memMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMemMb = rt.maxMemory() / (1024 * 1024)
        sb.append("<b>Memory:</b> ${memMb}MB / ${maxMemMb}MB\n")
        sb.append("<b>Threads:</b> ${Thread.getAllStackTraces().size}\n\n")

        // Modules Health
        sb.append("<b>Module Health:</b>\n")
        for (module in runtime.getModules()) {
            val status = module.getHealthStatus()
            val icon = if (status.isHealthy) "✅" else "⚠️"
            val msg = if (status.isHealthy) "Healthy" else status.message ?: "Unknown Error"
            sb.append("$icon <code>${module.name}</code>: $msg\n")
        }
        
        return CommandResult.Text(sb.toString())
    }
}
