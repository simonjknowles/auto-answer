package com.simon.autoanswer.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
        instance = this
        CrashLog.append(this, "notification listener connected")
        Log.i(TAG, "Listener connected")
        cellular = CellularAnswerer(this).also { it.start() }
    }

    override fun onListenerDisconnected() {
        instance = null
        CrashLog.append(this, "notification listener disconnected")
        Log.i(TAG, "Listener disconnected")
        cellular?.stop()
        cellular = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg.contains("whatsapp", ignoreCase = true)) {
            val n = sbn.notification
            val cat = n?.category
            val title = n?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = n?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(60)
            val actionCount = n?.actions?.size ?: 0
            CrashLog.append(this,
                "notif pkg=$pkg cat=$cat actions=$actionCount " +
                    "title='${title.take(40)}' text='$text'")
        }
        if (pkg == PKG_WHATSAPP || pkg == PKG_WHATSAPP_BUSINESS) {
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
        if (!prefs.enabled.value) {
            CrashLog.append(this, "whatsapp notif ignored: enabled=false")
            return
        }
        if (prefs.dndUntilMs.value > System.currentTimeMillis()) {
            CrashLog.append(this, "whatsapp notif ignored: dnd active")
            return
        }

        val n = sbn.notification ?: return
        if (!isIncomingCall(n)) {
            CrashLog.append(this, "whatsapp notif not classified as incoming call (cat=${n.category} text='${n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(60)}')")
            return
        }

        val key = sbn.key
        val now = System.currentTimeMillis()
        val last = recentlyHandled[key]
        if (last != null && now - last < DEDUPE_WINDOW_MS) return
        recentlyHandled[key] = now
        recentlyHandled.entries.removeAll { now - it.value > 60_000 }

        val callerName = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val actionTitles = n.actions?.map { it.title?.toString().orEmpty() }.orEmpty()
        val hasFullScreen = n.fullScreenIntent != null
        CrashLog.append(this,
            "whatsapp ringing from='$callerName' " +
                "category=${n.category} " +
                "actions=${actionTitles.size}[${actionTitles.joinToString("|")}] " +
                "fullScreenIntent=$hasFullScreen")
        Log.i(TAG, "Incoming WhatsApp call detected (key=$key from=$callerName)")

        if (prefs.loudChime.value) Chime.play(this)

        val answerAction = findAnswerAction(n)
        CrashLog.append(this, "answerAction = ${if (answerAction == null) "NOT FOUND" else "found: ${answerAction.title}"}")

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

        bringWhatsAppToForeground(n, sbn.packageName)

        val totalDelay = baseDelay + announceDelay
        val fireAtMs = System.currentTimeMillis() + totalDelay
        AnswerState.arm(minFireAtMs = fireAtMs)
        CrashLog.append(this, "armed accessibility tap, fireAt=+${totalDelay}ms")

        handler.postDelayed({
            if (prefs.forceBluetoothAudio.value) AudioRouter.routeToBluetoothIfAvailable(this)
            if (answerAction != null) {
                val ok = fireAnswerAction(answerAction)
                CrashLog.append(this, "fireAnswerAction returned $ok")
            }
            val invoked = CallAccessibilityService.directInvoke()
            CrashLog.append(this, "accessibility directInvoke=$invoked (attempt-1)")
            SilenceWatchdog.startForActiveCall(this, isCellular = false)
        }, totalDelay)

        listOf(1500L, 3000L, 6000L, 10000L).forEachIndexed { i, extra ->
            handler.postDelayed({
                val invoked = CallAccessibilityService.directInvoke()
                CrashLog.append(this, "accessibility directInvoke=$invoked (attempt-${i + 2})")
            }, totalDelay + extra)
        }
    }

    private fun bringWhatsAppToForeground(n: Notification, pkg: String) {
        val balOptions = makeBalOptions()

        val fsi = n.fullScreenIntent
        if (fsi != null) {
            try {
                if (balOptions != null) {
                    fsi.send(this, 0, null, null, null, null, balOptions)
                } else {
                    fsi.send()
                }
                CrashLog.append(this, "fired fullScreenIntent (BAL=${balOptions != null})")
            } catch (e: PendingIntent.CanceledException) {
                CrashLog.append(this, "fullScreenIntent send failed: ${e.message}")
            }
        } else {
            CrashLog.append(this, "no fullScreenIntent on notification")
        }

        val contentIntent = n.contentIntent
        if (contentIntent != null) {
            try {
                if (balOptions != null) {
                    contentIntent.send(this, 0, null, null, null, null, balOptions)
                } else {
                    contentIntent.send()
                }
                CrashLog.append(this, "fired contentIntent as additional path")
            } catch (e: PendingIntent.CanceledException) {
                CrashLog.append(this, "contentIntent send failed: ${e.message}")
            }
        }

        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(launchIntent)
                CrashLog.append(this, "startActivity launchIntent($pkg) — uses SYSTEM_ALERT_WINDOW BAL grant")
            }
        } catch (e: Exception) {
            CrashLog.append(this, "startActivity launchIntent failed: ${e.message}")
        }
    }

    private fun makeBalOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < 34) return null
        return try {
            val opts = ActivityOptions.makeBasic()
            opts.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            opts.toBundle()
        } catch (e: Throwable) {
            CrashLog.append(this, "BAL options unavailable: ${e.message}")
            null
        }
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

        @Volatile private var instance: CallNotificationListener? = null
        fun isAlive(): Boolean = instance != null

        private val ANSWER_KEYWORDS = listOf(
            "answer", "accept", "antworten", "responder", "repondre", "rispondi"
        )
    }
}
