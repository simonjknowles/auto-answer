package com.simon.autoanswer.diag

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {

    private const val FILE_NAME = "crash.log"
    private const val MAX_BYTES = 256 * 1024

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun read(context: Context): String = runCatching {
        logFile(context).takeIf { it.exists() }?.readText() ?: ""
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { logFile(context).delete() }
    }

    fun append(context: Context, line: String) {
        runCatching {
            val file = logFile(context)
            if (file.exists() && file.length() > MAX_BYTES) file.delete()
            file.appendText("${timestamp()} $line\n")
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val message = buildString {
            append("=== CRASH ${timestamp()} ===\n")
            append("Thread: ${thread.name}\n")
            append("Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})\n")
            append("Device:  ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append(sw.toString())
            append("\n")
        }
        val file = logFile(context)
        if (file.exists() && file.length() > MAX_BYTES) file.delete()
        file.appendText(message)
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK).format(Date())
}
