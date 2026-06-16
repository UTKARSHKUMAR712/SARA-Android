package com.sara.android.modules.commands

sealed class CommandResult {
    data class Text(val message: String) : CommandResult()
    data class Photo(val filePath: String, val caption: String? = null) : CommandResult()
}
