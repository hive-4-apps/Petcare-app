package com.hive4apps.petcare.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId

/** Why the app closed, kept on the phone so that a crash can be sent and fixed. */
object CrashLog {
    private const val FILE = "last-crash.txt"

    /** Keeps the stack trace of an uncaught error, then lets Android close the app as usual. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(appContext, "Thread ${thread.name}", error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** An error caught on the way (kept, the app goes on). */
    fun record(context: Context, where: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        File(context.filesDir, FILE).writeText("${Instant.now()}\n$where\n$trace")
    }

    fun last(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    /** What Android says about the last times the app ended (crash, not responding...), Android 11+. */
    fun exitReasons(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val reasons = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, 5) }.getOrNull()
            ?: return null
        return reasons.filter {
            it.reason == ApplicationExitInfo.REASON_CRASH || it.reason == ApplicationExitInfo.REASON_ANR ||
                it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
        }.joinToString("\n\n") { info ->
            val kind = when (info.reason) {
                ApplicationExitInfo.REASON_ANR -> "ANR (ne répond pas)"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "Crash natif"
                else -> "Crash"
            }
            val at = Instant.ofEpochMilli(info.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0)
            // The trace of a "not responding" says what the main thread was doing.
            val trace = if (info.reason == ApplicationExitInfo.REASON_ANR) {
                runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText() }?.take(6000) }.getOrNull()
            } else {
                null
            }
            listOfNotNull("$at · $kind · ${info.description.orEmpty()}", trace).joinToString("\n")
        }.ifBlank { null }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
