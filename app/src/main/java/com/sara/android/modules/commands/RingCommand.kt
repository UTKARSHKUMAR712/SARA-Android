package com.sara.android.modules.commands

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

class RingCommand : Command {
    override val name = "ring"
    override val description = "Play ringtone for 3 seconds"

    override fun execute(context: Context, args: List<String>): String {
        try {
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return "No ringtone found on device."

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = false
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                } catch (_: Exception) {}
            }, 3000)

            return "\uD83D\uDD14 Ringtone playing for 3 seconds..."
        } catch (e: Exception) {
            return "Failed to play ringtone: ${e.message}"
        }
    }
}
