package com.simon.autoanswer.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object CallerAnnouncer {

    private const val TAG = "CallerAnnouncer"

    @Volatile private var engine: TextToSpeech? = null

    fun announce(context: Context, callerName: String) {
        val sanitised = callerName.trim().ifBlank { "Unknown caller" }
        ensureEngine(context.applicationContext) { tts ->
            val utterance = "Call from $sanitised"
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            ensureSpeakable(context, AudioManager.STREAM_MUSIC)
            tts.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, ID)
        }
    }

    private fun ensureEngine(context: Context, onReady: (TextToSpeech) -> Unit) {
        val current = engine
        if (current != null) {
            onReady(current)
            return
        }
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.UK
                engine?.let(onReady)
            } else {
                Log.w(TAG, "TTS init failed; status=$status")
            }
        }
        engine = tts
    }

    private fun ensureSpeakable(context: Context, stream: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(stream)
        val current = am.getStreamVolume(stream)
        val minimum = (max * 0.6f).toInt().coerceAtLeast(1)
        if (current < minimum) {
            am.setStreamVolume(stream, minimum, 0)
        }
    }

    private const val ID = "caller_announcement"
}
