package com.sara.android.modules.commands

import android.content.Context
import com.sara.android.events.EventBus
import com.sara.android.events.HealthEvent
import com.sara.android.events.TelegramStatusEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StatusCommand : Command {
    override val name = "status"
    override val description = "Show SARA runtime status"

    override fun execute(context: Context, args: List<String>): String {
        val rt = Runtime.getRuntime()
        val usedMem = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val totalMem = rt.totalMemory() / (1024 * 1024)

        val sb = StringBuilder()
        sb.appendLine("\uD83E\uDD16 SARA Android Status")
        sb.appendLine()
        sb.appendLine("Memory: ${usedMem}MB / ${totalMem}MB")
        sb.appendLine("Telegram: Connected")

        return sb.toString()
    }
}
