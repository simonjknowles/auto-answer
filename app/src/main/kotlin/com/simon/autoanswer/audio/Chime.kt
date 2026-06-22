package com.simon.autoanswer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

object Chime {

    private const val TAG = "Chime"
    private const val SAMPLE_RATE = 44_100

    fun play(context: Context) {
        Thread {
            runCatching { generateAndPlay(context) }.onFailure {
                Log.w(TAG, "Chime failed", it)
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun generateAndPlay(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val targetVol = (max * 0.9f).toInt().coerceAtLeast(1)
        if (current < targetVol) am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

        val pcm = buildDingDong()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((pcm.size * 1000L / SAMPLE_RATE) + 200)
        track.stop()
        track.release()
    }

    private fun buildDingDong(): ShortArray {
        val toneDurMs = 350
        val gapMs = 120
        val ding = tone(880.0, toneDurMs)
        val gap = ShortArray((gapMs * SAMPLE_RATE / 1000))
        val dong = tone(587.0, toneDurMs)
        return ding + gap + dong
    }

    private fun tone(frequencyHz: Double, durationMs: Int): ShortArray {
        val samples = (durationMs * SAMPLE_RATE / 1000)
        val out = ShortArray(samples)
        val attack = SAMPLE_RATE / 40
        val release = SAMPLE_RATE / 8
        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = when {
                i < attack -> i.toDouble() / attack
                i > samples - release -> (samples - i).toDouble() / release
                else -> 1.0
            }
            val sample = sin(2 * PI * frequencyHz * t) * envelope * Short.MAX_VALUE * 0.6
            out[i] = sample.toInt().toShort()
        }
        return out
    }
}
