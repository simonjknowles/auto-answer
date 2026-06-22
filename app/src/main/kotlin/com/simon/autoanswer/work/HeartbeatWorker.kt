package com.simon.autoanswer.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.diag.CrashLog
import com.simon.autoanswer.diag.DiagnosticChecks
import java.net.HttpURLConnection
import java.net.URL

class HeartbeatWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val url = Prefs.get(ctx).heartbeatUrl.value.trim()
        if (url.isEmpty()) {
            Log.i(TAG, "No heartbeat URL configured; skipping")
            return Result.success()
        }
        return try {
            val report = DiagnosticChecks.collect(ctx)
            val healthy = report.allRequiredOk()
            val target = if (healthy) url else url.trimEnd('/') + "/fail"
            postSummary(target, report.toBriefBody())
            CrashLog.append(ctx, "heartbeat ok=$healthy → $target")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed", e)
            CrashLog.append(ctx, "heartbeat error: ${e.message}")
            Result.retry()
        }
    }

    private fun postSummary(target: String, body: String) {
        val conn = URL(target).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            conn.setRequestProperty("User-Agent", "AutoAnswer/1.0")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.i(TAG, "Heartbeat response $code")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
    }
}
