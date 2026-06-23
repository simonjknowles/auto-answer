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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogEmailWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs.get(ctx)
        val url = prefs.logEmailUrl.value.trim()
        if (!prefs.logEmailEnabled.value || url.isEmpty()) {
            Log.i(TAG, "Log email disabled or no URL; skipping")
            return Result.success()
        }
        return try {
            val report = DiagnosticChecks.collect(ctx)
            val logBody = CrashLog.read(ctx).takeLast(60_000)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.UK).format(Date())
            val subject = "Auto Answer log · ${report.device} · $timestamp"
            val body = buildString {
                appendLine("=== STATUS SNAPSHOT ===")
                append(report.toBriefBody())
                appendLine()
                appendLine()
                appendLine("=== LOG (most recent first) ===")
                append(logBody.lines().reversed().joinToString("\n"))
            }
            postJson(url, subject, body)
            CrashLog.append(ctx, "log-email POST ok subject=\"$subject\" bytes=${body.length}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Log email failed", e)
            CrashLog.append(ctx, "log-email error: ${e.message}")
            Result.retry()
        }
    }

    private fun postJson(target: String, subject: String, body: String) {
        val payload = buildString {
            append("{")
            append("\"subject\":\"${escape(subject)}\",")
            append("\"body\":\"${escape(body)}\"")
            append("}")
        }
        val conn = URL(target).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "AutoAnswer/LogEmail")
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            Log.i(TAG, "Apps Script responded $code")
            if (code !in 200..399) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                throw RuntimeException("HTTP $code: $err")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")

    companion object {
        private const val TAG = "LogEmailWorker"
    }
}
