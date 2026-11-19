package com.okaleb.phoneguard

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object DrumSynth {
    private const val RATE = 22050
    private const val STEP = 0.2
    private var track: AudioTrack? = null

    // 0 = bass, 1 = tone, 2 = slap, -1 = rest
    private val pattern = intArrayOf(0, 1, 1, 2, 0, -1, 1, 2, 0, 1, 2, 2)

    private fun hit(buf: ShortArray, start: Int, type: Int) {
        val len = (RATE * 0.35).toInt()
        var phase = 0.0
        for (i in 0 until len) {
            if (start + i >= buf.size) break
            val t = i.toDouble() / RATE
            val s = when (type) {
                0 -> {
                    phase += 2 * PI * (70 + 80 * exp(-t * 25)) / RATE
                    sin(phase) * exp(-t * 9)
                }
                1 -> {
                    phase += 2 * PI * (200 + 60 * exp(-t * 30)) / RATE
                    sin(phase) * exp(-t * 14) +
                        (Random.nextDouble() - 0.5) * exp(-t * 60) * 0.5
                }
                else -> {
                    (Random.nextDouble() - 0.5) * 1.6 * exp(-t * 35) +
                        sin(2 * PI * 350 * t) * exp(-t * 30) * 0.3
                }
            }
            val v = buf[start + i] + (s * 14000).toInt()
            buf[start + i] = v.coerceIn(-32000, 32000).toShort()
        }
    }

    fun start() {
        stop()
        val stepSamples = (RATE * STEP).toInt()
        val buf = ShortArray(stepSamples * pattern.size)
        pattern.forEachIndexed { i, type ->
            if (type >= 0) hit(buf, i * stepSamples, type)
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(buf, 0, buf.size)
        t.setLoopPoints(0, buf.size, -1)
        t.play()
        track = t
    }

    fun stop() {
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        track = null
    }
}
