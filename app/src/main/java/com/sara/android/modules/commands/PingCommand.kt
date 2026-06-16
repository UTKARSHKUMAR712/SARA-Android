package com.sara.android.modules.commands

import android.content.Context

class PingCommand : Command {
    override val name = "ping"
    override val description = "Check if SARA is alive"
    override fun execute(context: Context, args: List<String>): CommandResult {
        return CommandResult.Text("Pong! \uD83D\uDFE2 SARA Android is alive.")
    }
}
