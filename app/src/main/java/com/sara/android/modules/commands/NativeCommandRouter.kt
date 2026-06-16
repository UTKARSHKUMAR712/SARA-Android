package com.sara.android.modules.commands

import android.content.Context
import com.sara.android.runtime.SaraRuntime

class NativeCommandRouter {

    private val commands = mutableMapOf<String, Command>()

    fun register(command: Command) {
        commands[command.name.lowercase()] = command
    }

    fun registerAll(commands: List<Command>) {
        for (cmd in commands) register(cmd)
    }

    fun route(text: String, context: Context): CommandResult? {
        val trimmed = text.trim()
        val parts = trimmed.split("\\s+".toRegex())
        if (parts.isEmpty()) return null

        val raw = parts[0].lowercase()
        val cmdName = raw.removePrefix("/")
        val args = parts.drop(1)

        val handler = commands[cmdName] ?: return null
        return handler.execute(context, args)
    }

    fun getHelpText(): String {
        val info = mutableListOf<String>()
        val media = mutableListOf<String>()
        val location = mutableListOf<String>()
        val device = mutableListOf<String>()
        val general = mutableListOf<String>()

        for ((_, cmd) in commands) {
            val line = "/${cmd.name} — ${cmd.description}"
            when (cmd.name) {
                "camera", "screenshot" -> media.add(line)
                "location", "gps", "track" -> location.add(line)
                "battery", "network", "wifi", "volume", "ring", "torch", "lock", "clipboard", "notify" -> device.add(line)
                else -> general.add(line)
            }
        }

        return buildString {
            appendLine("SARA — Available Commands")
            appendLine()
            if (general.isNotEmpty()) { appendLine("General:"); general.forEach { appendLine("  $it") }; appendLine() }
            if (location.isNotEmpty()) { appendLine("Location:"); location.forEach { appendLine("  $it") }; appendLine() }
            if (media.isNotEmpty()) { appendLine("Media:"); media.forEach { appendLine("  $it") }; appendLine() }
            if (device.isNotEmpty()) { appendLine("Device:"); device.forEach { appendLine("  $it") } }
        }
    }
}
