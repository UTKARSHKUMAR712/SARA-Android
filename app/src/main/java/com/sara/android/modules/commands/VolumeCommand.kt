package com.sara.android.modules.commands

import android.content.Context
import android.media.AudioManager

class VolumeCommand : Command {
    override val name = "volume"
    override val description = "Show or set current volume levels. Usage: /volume [media|ring|alarm|notif] [0-100]"

    override fun execute(context: Context, args: List<String>): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (args.size == 2) {
            val streamName = args[0].lowercase()
            val percent = args[1].toIntOrNull()
            
            if (percent != null && percent in 0..100) {
                val stream = when (streamName) {
                    "media" -> AudioManager.STREAM_MUSIC
                    "ring" -> AudioManager.STREAM_RING
                    "alarm" -> AudioManager.STREAM_ALARM
                    "notif" -> AudioManager.STREAM_NOTIFICATION
                    else -> null
                }
                
                if (stream != null) {
                    val max = am.getStreamMaxVolume(stream)
                    val targetVolume = (percent * max) / 100
                    try {
                        am.setStreamVolume(stream, targetVolume, AudioManager.FLAG_SHOW_UI)
                        return CommandResult.Text("🔊 Set $streamName volume to $percent% ($targetVolume/$max)")
                    } catch (e: SecurityException) {
                        return CommandResult.Text("🔊 Permission denied: Cannot change Do Not Disturb settings. Check Notification Access.")
                    }
                }
            }
        }

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
            return "█".repeat(filled) + "░".repeat((8 - filled).coerceAtLeast(0)) + " $value/$max"
        }

        return CommandResult.Text(buildString {
            appendLine("🔊 <b>Volume Diagnostics</b>")
            appendLine()
            appendLine("<b>Media:</b>   ${bar(media, mediaMax)}")
            appendLine("<b>Ring:</b>    ${bar(ring, ringMax)}")
            appendLine("<b>Alarm:</b>   ${bar(alarm, alarmMax)}")
            appendLine("<b>Notif:</b>   ${bar(notif, notifMax)}")
            appendLine()
            appendLine("<b>Ringer mode:</b> ${
                when (am.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> "Normal"
                    AudioManager.RINGER_MODE_SILENT -> "Silent"
                    AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                    else -> "Unknown"
                }
            }")
            appendLine()
            appendLine("<i>Tip: Set volume with /volume [type] [0-100]</i>")
        })
    }
}
