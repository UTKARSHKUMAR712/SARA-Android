package com.sara.android.modules.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.sara.android.modules.media.CameraModule
import com.sara.android.modules.media.ServiceLifecycleOwner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CameraCommand : Command {
    override val name = "camera"
    override val description = "Capture a photo using CameraX. Usage: /camera [front|back]"

    override fun execute(context: Context, args: List<String>): CommandResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            return CommandResult.Text("📷 Camera permission not granted.\nTo enable, open SARA Dashboard and tap 'Camera'.")
        }

        val cameraModule = CameraModule.instance
        if (cameraModule == null || cameraModule.cameraProvider == null) {
            return CommandResult.Text("📷 CameraModule is not initialized.")
        }
        val cameraProvider = cameraModule.cameraProvider!!

        val facing = if (args.firstOrNull() == "front") {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(facing)
            .build()

        if (!cameraProvider.hasCamera(cameraSelector)) {
            return CommandResult.Text("📷 No camera found for the requested facing direction.")
        }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val lifecycleOwner = ServiceLifecycleOwner()

        val latch = CountDownLatch(1)
        var result: CommandResult = CommandResult.Text("Camera error: unknown")

        val mainHandler = Handler(Looper.getMainLooper())
        
        mainHandler.post {
            try {
                if (context is com.sara.android.service.SaraForegroundService) {
                    context.addForegroundType(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                }
                cameraProvider.unbindAll()
                lifecycleOwner.start()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
                
                val file = File(context.cacheDir, "sara_camera_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            result = CommandResult.Photo(file.absolutePath, "Camera capture")
                            lifecycleOwner.stop()
                            cameraProvider.unbindAll()
                            latch.countDown()
                        }

                        override fun onError(exc: ImageCaptureException) {
                            result = CommandResult.Text("📷 Failed to save photo: ${exc.message}")
                            lifecycleOwner.stop()
                            cameraProvider.unbindAll()
                            latch.countDown()
                        }
                    }
                )
            } catch (e: Exception) {
                result = CommandResult.Text("📷 Camera binding failed: ${e.message}")
                lifecycleOwner.stop()
                cameraProvider.unbindAll()
                latch.countDown()
            }
        }

        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                result = CommandResult.Text("📷 Camera capture timed out after 15 seconds.")
            }
        } finally {
            mainHandler.post {
                try {
                    lifecycleOwner.stop()
                    cameraProvider.unbindAll()
                    if (context is com.sara.android.service.SaraForegroundService) {
                        context.removeForegroundType(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
                    }
                } catch (e: Exception) {}
            }
        }

        return result
    }
}
