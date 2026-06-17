package com.sara.android.modules.commands

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.sara.android.modules.media.ScreenshotModule
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScreenshotCommand : Command {
    override val name = "screenshot"
    override val description = "Capture screen (requires MediaProjection setup)"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val module = ScreenshotModule.instance
        if (module == null || module.mediaProjection == null) {
            return CommandResult.Text(
                "📸 Screenshot requires MediaProjection consent.\n\n" +
                "To enable:\n" +
                "1. Open SARA app dashboard\n" +
                "2. Tap 'Screen Capture'\n" +
                "3. Confirm the system dialog\n\n" +
                "The permission lasts until the app is killed."
            )
        }

        val projection = module.mediaProjection!!
        
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        var result: CommandResult = CommandResult.Text("Screenshot error: unknown")
        val latch = CountDownLatch(1)

        val thread = HandlerThread("ScreenshotThread")
        thread.start()
        val handler = Handler(thread.looper)
        
        if (context is com.sara.android.service.SaraForegroundService) {
            context.addForegroundType(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        }
        
        var virtualDisplay: VirtualDisplay? = null
        var imageReader: ImageReader? = null
        
        handler.post {
            try {
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                
                virtualDisplay = projection.createVirtualDisplay(
                    "ScreenshotDisplay",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface,
                    null, handler
                )

                imageReader!!.setOnImageAvailableListener({ reader ->
                    try {
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * width
                            
                            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                            bitmap.copyPixelsFromBuffer(buffer)
                            image.close()

                            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                            
                            val file = File(context.cacheDir, "sara_screenshot_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { out ->
                                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            
                            result = CommandResult.Photo(file.absolutePath, "Screenshot capture")
                        }
                    } catch (e: Exception) {
                        result = CommandResult.Text("📸 Failed to process image: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }, handler)
                
            } catch (e: Exception) {
                result = CommandResult.Text("📸 Screenshot setup failed: ${e.message}")
                latch.countDown()
            }
        }

        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                result = CommandResult.Text("📸 Screenshot capture timed out after 5 seconds.")
            }
        } finally {
            // Guarantee cleanup regardless of timeouts or exceptions
            try {
                if (context is com.sara.android.service.SaraForegroundService) {
                    context.removeForegroundType(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                }
                virtualDisplay?.release()
                imageReader?.setOnImageAvailableListener(null, null)
                imageReader?.close()
                thread.quitSafely()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }

        return result
    }
}
