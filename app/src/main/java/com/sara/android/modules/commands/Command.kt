package com.sara.android.modules.commands

import android.content.Context

interface Command {
    val name: String
    val description: String
    fun execute(context: Context, args: List<String>): CommandResult
}
