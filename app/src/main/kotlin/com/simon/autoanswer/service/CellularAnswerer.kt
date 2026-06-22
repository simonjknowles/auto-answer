package com.simon.autoanswer.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.simon.autoanswer.data.ContactLookup
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.diag.CrashLog
import com.simon.autoanswer.tts.CallerAnnouncer

class CellularAnswerer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val telephony =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var legacyListener: PhoneStateListener? = null
    private var lastHandledAt = 0L

    fun start() {
        if (!hasPermissions()) {
            Log.i(TAG, "Permissions not granted, cellular answerer idle")
            return
        }
        @Suppress("DEPRECATION")
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleState(state, phoneNumber.orEmpty())
            }
        }
        @Suppress("DEPRECATION")
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        legacyListener = listener
        Log.i(TAG, "Registered PhoneStateListener (with number)")
    }

    fun stop() {
        @Suppress("DEPRECATION")
        legacyListener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
        legacyListener = null
    }

    private fun hasPermissions(): Boolean {
        val answer = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ANSWER_PHONE_CALLS,
        )
        val state = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE,
        )
        return answer == PackageManager.PERMISSION_GRANTED &&
            state == PackageManager.PERMISSION_GRANTED
    }

    private fun handleState(state: Int, number: String) {
        if (state != TelephonyManager.CALL_STATE_RINGING) return

        val now = System.currentTimeMillis()
        if (now - lastHandledAt < DEDUPE_WINDOW_MS) return
        lastHandledAt = now

        val prefs = Prefs.get(context)
        if (!prefs.enabled.value || !prefs.cellularEnabled.value) return
        if (prefs.dndUntilMs.value > now) {
            Log.i(TAG, "DND active; cellular call left ringing")
            return
        }
        if (!hasPermissions()) {
            Log.w(TAG, "RINGING but permissions missing")
            return
        }

        val callerName = ContactLookup.displayNameFor(context, number)

        if (prefs.cellularWhitelistOnly.value) {
            val allowed = isWhitelisted(prefs, number)
            if (!allowed) {
                CrashLog.append(
                    context,
                    "cellular skip (not whitelisted): name=$callerName num=${number.maskExcept(4)}",
                )
                Log.i(TAG, "Caller not in whitelist; not auto-answering")
                return
            }
        }

        CrashLog.append(
            context,
            "cellular ringing: name=$callerName num=${number.maskExcept(4)}",
        )

        if (prefs.testMode.value) {
            handler.post {
                Toast.makeText(
                    context,
                    "Test mode: would auto-answer cellular from " +
                        (callerName ?: "unknown") + " in ${prefs.delayMs.value} ms",
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }

        val delay = prefs.delayMs.value.toLong()
        val announceDelay = if (prefs.ttsAnnounce.value) {
            CallerAnnouncer.announce(context, callerName ?: "Unknown caller")
            1500L
        } else 0L

        handler.postDelayed({ accept() }, delay + announceDelay)
    }

    private fun isWhitelisted(prefs: Prefs, incomingNumber: String): Boolean {
        if (incomingNumber.isBlank()) return false
        return prefs.cellularWhitelist.value.any { allowed ->
            ContactLookup.matches(allowed, incomingNumber)
        }
    }

    @Suppress("MissingPermission")
    private fun accept() {
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION")
            telecom.acceptRingingCall()
            Log.i(TAG, "Accepted ringing cellular call")
            forceLoudspeaker()
            SilenceWatchdog.startForActiveCall(context, isCellular = true)
        } catch (e: SecurityException) {
            Log.e(TAG, "acceptRingingCall denied", e)
        }
    }

    private fun forceLoudspeaker() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_CALL
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = true
    }

    private fun String.maskExcept(lastN: Int): String =
        if (length <= lastN) this else "*".repeat(length - lastN) + takeLast(lastN)

    companion object {
        private const val TAG = "CellularAnswerer"
        private const val DEDUPE_WINDOW_MS = 5_000L
    }
}
