package com.simon.autoanswer.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.simon.autoanswer.audio.AudioRouter
import com.simon.autoanswer.audio.Chime
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.diag.CrashLog
import com.simon.autoanswer.tts.CallerAnnouncer

class CallNotificationListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val recentlyHandled = mutableMapOf<String, Long>()
    private var cellular: CellularAnswerer? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        cellular = CellularAnswerer(this).also { it.start() }
    }

    override fun onListenerDisconnected() {
        cellular?.stop()
        cellular = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == PKG_WHATSAPP || sbn.packageName == PKG_WHATSAPP_BUSINESS) {
            RemoteCommandParser.handle(this, sbn)
            handleWhatsAppCall(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        recentlyHandled.remove(sbn.key)
        if (sbn.packageName == PKG_WHATSAPP || sbn.packageName == PKG_WHATSAPP_BUSINESS) {
            val n = sbn.notification
            if (n != null && n.category == Notification.CATEGORY_CALL) {
                SilenceWatchdog.stop()
            }
        }
    }

    private fun handleWhatsAppCall(sbn: StatusBarNotification) {
        val prefs = Prefs.get(this)
        if (!prefs.enabled.value) return
        if (prefs.dndUntilMs.value > System.currentTimeMillis()) return

        val n = sbn.notification ?: return
        if (!isIncomingCall(n)) return

        val key = sbn.key
        val now = System.currentTimeMillis()
        val last = recentlyHandled[key]
        if (last != null && now - last < DEDUPE_WINDOW_MS) return
        recentlyHandled[key] = now
        recentlyHandled.entries.removeAll { now - it.value > 60_000 }

        val callerName = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        CrashLog.append(this, "whatsapp ringing from '$callerName'")
        Log.i(TAG, "Incoming WhatsApp call detected (key=$key from=$callerName)")

        if (prefs.loudChime.value) Chime.play(this)

        val answerAction = findAnswerAction(n)

        if (prefs.testMode.value) {
            handler.post {
                Toast.makeText(
                    this,
                    "Test mode: would auto-answer WhatsApp from '$callerName'" +
                        if (answerAction != null) " (notification action)" else " (accessibility tap)",
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }

        val baseDelay = prefs.delayMs.value.toLong()
        val announceDelay = if (prefs.ttsAnnounce.value && callerName.isNotBlank()) {
            CallerAnnouncer.announce(this, callerName)
            1500L
        } else 0L

        if (answerAction == null) {
            AnswerState.arm()
            handler.postDelayed({
                if (prefs.forceBluetoothAudio.value) AudioRouter.routeToBluetoothIfAvailable(this)
                SilenceWatchdog.startForActiveCall(this, isCellular = false)
            }, baseDelay + announceDelay)
            return
        }

        handler.postDelayed({
            if (prefs.forceBluetoothAudio.value) AudioRouter.routeToBluetoothIfAvailable(this)
            val ok = fireAnswerAction(answerAction)
            if (!ok) {
                AnswerState.arm()
                return@postDelayed
            }
            SilenceWatchdog.startForActiveCall(this, isCellular = false)
        }, baseDelay + announceDelay)
    }

    private fun isIncomingCall(n: Notification): Boolean {
        if (n.category == Notification.CATEGORY_CALL) return true
        val text = (n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "") +
            " " + (n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "")
        val lower = text.lowercase()
        return lower.contains("incoming voice call") ||
            lower.contains("incoming video call") ||
            lower.contains("incoming call") ||
            lower.contains("calling")
    }

    private fun findAnswerAction(n: Notification): Notification.Action? {
        val actions = n.actions ?: return null
        return actions.firstOrNull { action ->
            val title = action.title?.toString()?.lowercase() ?: return@firstOrNull false
            ANSWER_KEYWORDS.any { it in title }
        }
    }

    private fun fireAnswerAction(action: Notification.Action): Boolean {
        return try {
            action.actionIntent.send(this, 0, Intent())
            Log.i(TAG, "Fired Answer PendingIntent")
            true
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Answer PendingIntent was canceled", e)
            false
        }
    }

    companion object {
        private const val TAG = "CallNotifListener"
        private const val PKG_WHATSAPP = "com.whatsapp"
        private const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        private const val DEDUPE_WINDOW_MS = 5_000L

        private val ANSWER_KEYWORDS = listOf(
            "answer", "accept", "antworten", "responder", "repondre", "rispondi"
        )
    }
}
