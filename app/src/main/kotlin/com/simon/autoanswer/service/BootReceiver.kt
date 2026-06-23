package com.simon.autoanswer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simon.autoanswer.work.HeartbeatScheduler
import com.simon.autoanswer.work.LogEmailScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received ${intent.action}")
        WatchdogService.start(context)
        HeartbeatScheduler.schedule(context)
        LogEmailScheduler.schedule(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
