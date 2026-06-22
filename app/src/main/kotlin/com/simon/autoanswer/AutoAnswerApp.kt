package com.simon.autoanswer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.simon.autoanswer.diag.CrashLog
import com.simon.autoanswer.service.WatchdogService
import com.simon.autoanswer.work.HeartbeatScheduler

class AutoAnswerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        ensureChannels()
        WatchdogService.start(this)
        HeartbeatScheduler.schedule(this)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Auto Answer status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows the auto-answer service is active." }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENTS,
                "Auto-answer events",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Per-call events such as incoming calls and announcements." }
        )
    }

    companion object {
        const val CHANNEL_STATUS = "auto_answer_status"
        const val CHANNEL_EVENTS = "auto_answer_events"
    }
}
