package com.pricescanner.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

object SoundManager {
    private const val SAMPLE_RATE = 44100
    private const val FREQUENCY = 880.0
    private const val DURATION_MS = 120

    fun playBeep() {
        try {
            val numSamples = (SAMPLE_RATE * DURATION_MS / 1000).toInt()
            val samples = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                samples[i] = (sin(2.0 * PI * FREQUENCY * t) * Short.MAX_VALUE * 0.08).toInt().toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            // Release after playback
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                track.release()
            }, (DURATION_MS + 50).toLong())
        } catch (_: Exception) {}
    }
}
