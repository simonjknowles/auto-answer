package com.simon.autoanswer.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.diag.CrashLog
import kotlin.math.abs

object SilenceWatchdog {

    private const val TAG = "SilenceWatchdog"
    private const val SAMPLE_RATE = 8000
    private const val WINDOW_MS = 5000L
    private const val SILENCE_THRESHOLD = 800
    private const val MAX_CALL_DURATION_MS = 2 * 60 * 60 * 1000L

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var record: AudioRecord? = null

    fun startForActiveCall(context: Context, isCellular: Boolean) {
        val prefs = Prefs.get(context)
        if (!prefs.autoHangupSilence.value) return
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "RECORD_AUDIO not granted; auto-hangup disabled")
            return
        }
        stop()
        val silenceLimitMs = prefs.autoHangupMinutes.value * 60_000L
        Log.i(TAG, "Silence watchdog armed silenceLimit=${silenceLimitMs / 1000}s cellular=$isCellular")

        thread = HandlerThread("SilenceWatchdog").also { it.start() }
        handler = Handler(thread!!.looper)
        handler!!.post { loop(context, silenceLimitMs, isCellular) }
    }

    fun stop() {
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        handler = null
        thread?.quitSafely()
        thread = null
    }

    @Suppress("MissingPermission")
    private fun loop(context: Context, silenceLimitMs: Long, isCellular: Boolean) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord init failed", e)
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }
        record = recorder
        recorder.startRecording()

        val buf = ShortArray(bufferSize)
        var lastVoiceAt = System.currentTimeMillis()
        val safetyDeadline = System.currentTimeMillis() + MAX_CALL_DURATION_MS

        while (System.currentTimeMillis() < safetyDeadline) {
            val n = recorder.read(buf, 0, buf.size)
            if (n <= 0) {
                Thread.sleep(200)
                continue
            }
            val peak = (0 until n).maxOfOrNull { abs(buf[it].toInt()) } ?: 0
            val now = System.currentTimeMillis()
            if (peak > SILENCE_THRESHOLD) lastVoiceAt = now
            val silenceFor = now - lastVoiceAt
            if (silenceFor >= silenceLimitMs) {
                Log.i(TAG, "Continuous silence ${silenceFor / 1000}s detected; ending call")
                CrashLog.append(context, "auto-hangup: silence ${silenceFor / 1000}s cellular=$isCellular")
                endCall(context)
                break
            }
            Thread.sleep(WINDOW_MS)
        }
        stop()
    }

    @Suppress("MissingPermission")
    private fun endCall(context: Context) {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        try {
            telecom.endCall()
        } catch (e: SecurityException) {
            Log.e(TAG, "endCall denied", e)
        }
    }
}
