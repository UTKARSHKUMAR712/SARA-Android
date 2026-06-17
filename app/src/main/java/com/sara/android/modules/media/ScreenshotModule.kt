package com.sara.android.modules.media

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import com.sara.android.runtime.Module

class ScreenshotModule : Module {
    override val name = "ScreenshotModule"
    
    companion object {
        var instance: ScreenshotModule? = null
            private set
    }
    
    private var appContext: Context? = null
    private var projectionManager: MediaProjectionManager? = null
    var mediaProjection: MediaProjection? = null
        private set

    override fun onStart(context: Context) {
        appContext = context.applicationContext
        instance = this
        projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStop() {
        mediaProjection?.stop()
        mediaProjection = null
        projectionManager = null
        instance = null
    }

    fun setProjectionIntent(resultCode: Int, data: Intent) {
        mediaProjection?.stop()
        mediaProjection = projectionManager?.getMediaProjection(resultCode, data)
    }
}
