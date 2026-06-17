package com.sara.android.modules.media

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.sara.android.runtime.Module
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CameraModule : Module {
    override val name = "CameraModule"
    var cameraProvider: ProcessCameraProvider? = null
        private set

    companion object {
        var instance: CameraModule? = null
            private set
    }

    override fun onStart(context: Context) {
        instance = this
        val latch = CountDownLatch(1)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
            } catch (e: Exception) {
                // Ignore
            } finally {
                latch.countDown()
            }
        }, ContextCompat.getMainExecutor(context))
        
        latch.await(5, TimeUnit.SECONDS)
        if (cameraProvider == null) {
            throw RuntimeException("CameraX failed to initialize in time")
        }
    }

    override fun onStop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        instance = null
    }
}
