package com.simon.autoanswer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.simon.autoanswer.AutoAnswerApp
import com.simon.autoanswer.MainActivity
import com.simon.autoanswer.R
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.debug.DebugServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class WatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var debugServer: DebugServer? = null

    private val tick = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("Auto Answer is running"))
        handler.post(tick)
        observeDebugServerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        stopDebugServer()
        scope.cancel()
        super.onDestroy()
    }

    private fun observeDebugServerState() {
        val prefs = Prefs.get(this)
        combine(prefs.debugServerEnabled, prefs.debugServerPort) { enabled, port -> enabled to port }
            .distinctUntilChanged()
            .onEach { (enabled, port) ->
                stopDebugServer()
                if (enabled) startDebugServer(port)
                updateNotification()
            }
            .launchIn(scope)
    }

    private fun startDebugServer(port: Int) {
        try {
            val server = DebugServer(applicationContext, port)
            server.start(NanoHTTPDTimeout.SOCKET_READ_TIMEOUT, false)
            debugServer = server
        } catch (e: Exception) {
            android.util.Log.e("WatchdogService", "Debug server failed to start on $port", e)
        }
    }

    private fun stopDebugServer() {
        runCatching { debugServer?.stop() }
        debugServer = null
    }

    private object NanoHTTPDTimeout { const val SOCKET_READ_TIMEOUT = 5000 }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val prefs = Prefs.get(this)
        val dndUntil = prefs.dndUntilMs.value
        val now = System.currentTimeMillis()

        val baseText = when {
            !prefs.enabled.value -> "Paused (master switch off)"
            dndUntil > now -> "Do Not Disturb until ${formatTime(dndUntil)}"
            else -> "Ready · last check ${formatTime(now)}"
        }
        val text = if (prefs.debugServerEnabled.value) {
            "$baseText · debug :${prefs.debugServerPort.value}"
        } else baseText
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, AutoAnswerApp.CHANNEL_STATUS)
            .setContentTitle("Auto Answer")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(AutoAnswerApp.CHANNEL_STATUS) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                AutoAnswerApp.CHANNEL_STATUS,
                "Auto Answer status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows the auto-answer service is active."
                setShowBadge(false)
            }
        )
    }

    private fun formatTime(ms: Long): String {
        val h = (ms / 3_600_000) % 24
        val m = (ms / 60_000) % 60
        return "%02d:%02d".format(h, m)
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val TICK_MS = 5 * 60_000L

        fun start(context: Context) {
            val intent = Intent(context, WatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchdogService::class.java))
        }
    }
}
