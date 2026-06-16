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

    fun route(text: String, context: Context): String? {
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
        val sb = StringBuilder("Available commands:")
        for ((_, cmd) in commands) {
            sb.append("\n/${cmd.name} — ${cmd.description}")
        }
        return sb.toString()
    }
}
