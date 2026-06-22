package com.simon.autoanswer.debug

import android.content.Context
import android.util.Log
import com.simon.autoanswer.audio.Chime
import com.simon.autoanswer.data.Prefs
import com.simon.autoanswer.diag.CrashLog
import com.simon.autoanswer.diag.DiagnosticChecks
import com.simon.autoanswer.tts.CallerAnnouncer
import com.simon.autoanswer.work.HeartbeatScheduler
import fi.iki.elonen.NanoHTTPD

class DebugServer(
    private val context: Context,
    port: Int,
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        return try {
            when {
                uri == "/" -> html(Dashboard.PAGE)
                uri == "/status.json" -> json(buildStatusJson())
                uri == "/log.txt" -> plain(CrashLog.read(context))
                uri.startsWith("/action/") && method == Method.POST ->
                    handleAction(uri.removePrefix("/action/"), session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
        }
    }

    private fun handleAction(action: String, session: IHTTPSession): Response {
        val prefs = Prefs.get(context)
        val params = session.parms
        when (action) {
            "chime" -> Chime.play(context)
            "tts" -> CallerAnnouncer.announce(context, params["name"] ?: "Test caller")
            "heartbeat" -> HeartbeatScheduler.pingNow(context)
            "toggle-enabled" -> prefs.setEnabled(!prefs.enabled.value)
            "toggle-test-mode" -> prefs.setTestMode(!prefs.testMode.value)
            "dnd" -> {
                val minutes = params["minutes"]?.toIntOrNull() ?: 60
                prefs.setDndUntil(System.currentTimeMillis() + minutes * 60_000L)
            }
            "dnd-clear" -> prefs.setDndUntil(0L)
            "clear-log" -> CrashLog.clear(context)
            else -> return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "unknown action: $action"
            )
        }
        CrashLog.append(context, "debug-server action: $action ${params.filterKeys { it != "NanoHttpd.QUERY_STRING" }}")
        return plain("ok")
    }

    private fun buildStatusJson(): String {
        val prefs = Prefs.get(context)
        val r = DiagnosticChecks.collect(context)
        val sb = StringBuilder()
        sb.append("{")
        sb.append(""""device": "${escape(r.device)}","""
            + """ "sdk": ${r.sdkInt},"""
            + """ "collectedAt": ${r.collectedAt},""")
        sb.append(""""required": {""")
        sb.append("""  "accessibility": ${r.accessibility},""")
        sb.append("""  "notification": ${r.notification},""")
        sb.append("""  "batteryUnrestricted": ${r.batteryUnrestricted},""")
        sb.append("""  "internet": ${r.internet}""")
        sb.append("""},""")
        sb.append(""""recommended": {""")
        sb.append("""  "overlay": ${r.overlay},""")
        sb.append("""  "stayAwake": ${r.stayAwake}""")
        sb.append("""},""")
        sb.append(""""audio": {""")
        sb.append("""  "bluetoothOn": ${r.bluetoothOn},""")
        sb.append("""  "bluetoothA2dpConnected": ${r.bluetoothA2dpConnected}""")
        sb.append("""},""")
        sb.append(""""cellular": {""")
        sb.append("""  "enabled": ${r.cellularEnabled},""")
        sb.append("""  "permissionGranted": ${r.answerCallsGranted},""")
        sb.append("""  "whitelistOnly": ${prefs.cellularWhitelistOnly.value},""")
        sb.append("""  "whitelistSize": ${prefs.cellularWhitelist.value.size}""")
        sb.append("""},""")
        sb.append(""""state": {""")
        sb.append("""  "enabled": ${prefs.enabled.value},""")
        sb.append("""  "testMode": ${prefs.testMode.value},""")
        sb.append("""  "dndActive": ${r.dndActive},""")
        sb.append("""  "dndUntilMs": ${prefs.dndUntilMs.value},""")
        sb.append("""  "answerDelayMs": ${prefs.delayMs.value},""")
        sb.append("""  "loudChime": ${prefs.loudChime.value},""")
        sb.append("""  "ttsAnnounce": ${prefs.ttsAnnounce.value},""")
        sb.append("""  "autoHangup": ${prefs.autoHangupSilence.value},""")
        sb.append("""  "autoHangupMinutes": ${prefs.autoHangupMinutes.value},""")
        sb.append("""  "forceBluetoothAudio": ${prefs.forceBluetoothAudio.value}""")
        sb.append("""},""")
        sb.append(""""healthcheck": {""")
        sb.append("""  "url": "${escape(prefs.heartbeatUrl.value)}",""")
        sb.append("""  "configured": ${prefs.heartbeatUrl.value.isNotBlank()}""")
        sb.append("""}""")
        sb.append("}")
        return sb.toString()
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun json(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)

    private fun plain(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, body)

    companion object {
        private const val TAG = "DebugServer"
    }
}
