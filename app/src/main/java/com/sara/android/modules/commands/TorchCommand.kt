package com.sara.android.modules.commands

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build

class TorchCommand : Command {
    override val name = "torch"
    override val description = "Toggle flashlight: /torch on or /torch off"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val enable = when {
            args.isEmpty() -> return CommandResult.Text("Usage: /torch on  or  /torch off")
            args[0].lowercase() in listOf("on", "1", "true") -> true
            args[0].lowercase() in listOf("off", "0", "false") -> false
            else -> return CommandResult.Text("Usage: /torch on  or  /torch off")
        }

        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId: String = try {
            cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val flash = chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                flash == true
            } ?: return CommandResult.Text("No camera with flash available.")
        } catch (e: CameraAccessException) {
            return CommandResult.Text("Camera access error: ${e.message}")
        } catch (e: SecurityException) {
            return CommandResult.Text("Camera permission not granted. Grant CAMERA permission in Settings.")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.setTorchMode(cameraId, enable)
            } else {
                return CommandResult.Text("Torch requires Android 6.0+.")
            }
        } catch (e: CameraAccessException) {
            return CommandResult.Text("Flash in use by another app: ${e.message}")
        } catch (e: Exception) {
            return CommandResult.Text("Torch failed: ${e.message}")
        }

        return if (enable) CommandResult.Text("\uD83D\uDD2E Flashlight turned ON") else CommandResult.Text("\uD83D\uDD2E Flashlight turned OFF")
    }
}
