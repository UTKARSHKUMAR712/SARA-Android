package com.sara.android.modules.commands

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CameraCommand : Command {
    override val name = "camera"
    override val description = "Capture a photo. Usage: /camera [front|back]"

    override fun execute(context: Context, args: List<String>): CommandResult {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return CommandResult.Text(
                "Camera permission not granted.\n" +
                "Grant CAMERA permission via Settings → Apps → SARA → Permissions."
            )
        }

        val facing = if (args.firstOrNull() == "front") {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        } else {
            Camera.CameraInfo.CAMERA_FACING_BACK
        }

        val cameraId = findCamera(facing)
        if (cameraId == null) {
            return CommandResult.Text("No camera found for the requested facing direction.")
        }

        val latch = CountDownLatch(1)
        var result: CommandResult = CommandResult.Text("Camera error: unknown")
        var camera: Camera? = null
        var handlerThread: HandlerThread? = null

        try {
            camera = Camera.open(cameraId)
            val params = camera.parameters ?: return CommandResult.Text("Failed to get camera parameters.")

            val sizes = params.supportedPictureSizes
            if (sizes != null && sizes.isNotEmpty()) {
                val best = sizes.maxByOrNull { it.width * it.height }
                params.setPictureSize(best!!.width, best.height)
            }
            params.pictureFormat = ImageFormat.JPEG
            camera.parameters = params

            handlerThread = HandlerThread("CameraCapture").apply { start() }
            val handler = Handler(handlerThread.looper)

            camera.takePicture(null, null, Camera.PictureCallback { data, _ ->
                try {
                    val file = File(context.cacheDir, "sara_camera_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { it.write(data) }
                    result = CommandResult.Photo(file.absolutePath, "Camera capture")
                } catch (e: Exception) {
                    result = CommandResult.Text("Failed to save photo: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }, handler)

            if (!latch.await(10, TimeUnit.SECONDS)) {
                result = CommandResult.Text("Camera capture timed out after 10 seconds.")
            }
        } catch (e: Exception) {
            result = CommandResult.Text("Camera error: ${e.message}")
        } finally {
            handlerThread?.quitSafely()
            camera?.release()
        }

        return result
    }

    private fun findCamera(facing: Int): Int? {
        val count = Camera.numberOfCameras
        val info = Camera.CameraInfo()
        for (i in 0 until count) {
            Camera.getCameraInfo(i, info)
            if (info.facing == facing) return i
        }
        return if (count > 0) 0 else null
    }
}
