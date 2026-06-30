package com.auralis.protect.data.audio

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

class RingController(
    private val context: Context
) {
    private var activeRingtone: Ringtone? = null

    fun startRing() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            activeRingtone?.stop()
            activeRingtone = RingtoneManager.getRingtone(context, alarmUri)
            activeRingtone?.play()
        } catch (_: Exception) {
            // Later: route this to Auralis event logs.
        }
    }

    fun stopRing() {
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (_: Exception) {
            // Later: route this to Auralis event logs.
        }
    }
}
