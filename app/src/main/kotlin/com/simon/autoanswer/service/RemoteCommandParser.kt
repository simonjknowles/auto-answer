package com.simon.autoanswer.service

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.diag.CrashLog
import com.simon.autoanswer.work.HeartbeatScheduler

object RemoteCommandParser {

    private const val TAG = "RemoteCmd"
    private const val PREFIX = "/aa "

    fun handle(context: Context, sbn: StatusBarNotification) {
        val prefs = Prefs.get(context)
        if (!prefs.remoteCmdEnabled.value) return

        val n = sbn.notification ?: return
        val sender = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (!body.startsWith(PREFIX, ignoreCase = true)) return

        if (!isAdmin(prefs, sender)) {
            Log.w(TAG, "Command from non-admin '$sender' ignored")
            CrashLog.append(context, "remote-cmd rejected sender=$sender")
            return
        }
        val cmd = body.removePrefix(PREFIX).trim().lowercase()
        Log.i(TAG, "Processing command '$cmd' from '$sender'")
        CrashLog.append(context, "remote-cmd sender=$sender cmd=$cmd")
        execute(context, prefs, cmd)
    }

    private fun isAdmin(prefs: Prefs, sender: String): Boolean {
        val admins = prefs.adminContacts.value
        if (admins.isEmpty()) return false
        return admins.any { admin ->
            sender.equals(admin, ignoreCase = true) ||
                sender.contains(admin, ignoreCase = true)
        }
    }

    private fun execute(context: Context, prefs: Prefs, cmd: String) {
        when {
            cmd == "status" -> HeartbeatScheduler.pingNow(context)
            cmd == "pause" || cmd == "off" -> prefs.setEnabled(false)
            cmd == "resume" || cmd == "on" -> prefs.setEnabled(true)
            cmd.startsWith("dnd ") -> handleDnd(prefs, cmd.removePrefix("dnd ").trim())
            cmd == "dnd off" || cmd == "dnd clear" -> prefs.setDndUntil(0L)
            cmd == "test" -> prefs.setTestMode(!prefs.testMode.value)
            cmd == "ping" -> HeartbeatScheduler.pingNow(context)
            else -> Log.i(TAG, "Unknown command: $cmd")
        }
    }

    private fun handleDnd(prefs: Prefs, arg: String) {
        val minutes = when {
            arg.endsWith("h") -> arg.dropLast(1).toIntOrNull()?.times(60)
            arg.endsWith("m") -> arg.dropLast(1).toIntOrNull()
            else -> arg.toIntOrNull()
        } ?: return
        val until = System.currentTimeMillis() + minutes.coerceIn(1, 24 * 60) * 60_000L
        prefs.setDndUntil(until)
    }
}
