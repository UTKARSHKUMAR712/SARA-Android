package com.sara.android.modules.commands

import android.content.Context

class ScreenshotCommand : Command {
    override val name = "screenshot"
    override val description = "Capture screen (requires MediaProjection setup)"

    override fun execute(context: Context, args: List<String>): CommandResult {
        return CommandResult.Text(
            "Screenshot requires MediaProjection consent.\n\n" +
            "To enable:\n" +
            "1. Open SARA app dashboard\n" +
            "2. Tap 'Grant Screenshot Permission'\n" +
            "3. Confirm the system dialog\n\n" +
            "The permission lasts until reboot."
        )
    }
}
