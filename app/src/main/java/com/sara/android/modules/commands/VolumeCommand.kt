package com.sara.android.modules.commands

import android.content.Context
import android.media.AudioManager

class VolumeCommand : Command {
    override val name = "volume"
    override val description = "Show current volume levels"

    override fun execute(context: Context, args: List<String>): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val media = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val ring = am.getStreamVolume(AudioManager.STREAM_RING)
        val ringMax = am.getStreamMaxVolume(AudioManager.STREAM_RING)

        val alarm = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val alarmMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        val notif = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        val notifMax = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)

        fun bar(value: Int, max: Int): String {
            val filled = if (max > 0) (value * 8 / max) else 0
            return "\u2588".repeat(filled) + "\u2591".repeat((8 - filled).coerceAtLeast(0)) + " $value/$max"
        }

        return buildString {
            appendLine("\uD83D\uDD0A Volume")
            appendLine()
            appendLine("Media:   ${bar(media, mediaMax)}")
            appendLine("Ring:    ${bar(ring, ringMax)}")
            appendLine("Alarm:   ${bar(alarm, alarmMax)}")
            appendLine("Notif:   ${bar(notif, notifMax)}")
            appendLine()
            appendLine("Ringer mode: ${
                when (am.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> "Normal"
                    AudioManager.RINGER_MODE_SILENT -> "Silent"
                    AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                    else -> "Unknown"
                }
            }")
        }
    }
}
