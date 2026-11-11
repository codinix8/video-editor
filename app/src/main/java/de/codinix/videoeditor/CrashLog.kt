package de.codinix.videoeditor

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Writes uncaught crashes to a file so they can be shown and copied on the next start.
 * Replaces the missing logcat for the tester.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "crash_last.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                // Neutral keys only; labels are localised when the report is shown (CrashReportText).
                val text = CrashReportText.build(
                    time = java.util.Date().toString(),
                    thread = thread.name,
                    version = BuildConfig.VERSION_NAME,
                    device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / Android ${android.os.Build.VERSION.RELEASE}",
                    stackTrace = sw.toString(),
                )
                file(context).writeText(text)
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Returns the last crash report and deletes it (a copy stays for the settings). */
    fun takeLast(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        val text = f.readText()
        File(context.filesDir, "crash_prev.txt").writeText(text)
        f.delete()
        return text
    }

    fun previous(context: Context): String? = File(context.filesDir, "crash_prev.txt").takeIf { it.exists() }?.readText()

    /** Report with header labels in the app language; pass the activity context, not the application context. */
    fun localised(context: Context, report: String): String = CrashReportText.localise(report, mapOf(
        CrashReportText.TIME to context.getString(R.string.crash_label_time),
        CrashReportText.THREAD to context.getString(R.string.crash_label_thread),
        CrashReportText.VERSION to context.getString(R.string.crash_label_version),
        CrashReportText.DEVICE to context.getString(R.string.crash_label_device),
    ))
}
