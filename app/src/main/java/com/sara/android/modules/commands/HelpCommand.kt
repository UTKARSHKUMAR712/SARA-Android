package com.sara.android.modules.commands

import android.content.Context

class HelpCommand(private val router: NativeCommandRouter) : Command {
    override val name = "help"
    override val description = "Show this help message"
    override fun execute(context: Context, args: List<String>): CommandResult {
        return CommandResult.Text(router.getHelpText())
    }
}
