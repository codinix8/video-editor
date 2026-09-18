package de.codinix.videoeditor

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Schreibt unbehandelte Abstürze in eine Datei, damit sie beim nächsten Start angezeigt
 * und kopiert werden können. Ersetzt fehlendes Logcat für den Tester.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "crash_last.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val text = buildString {
                    append("Zeit: ").append(java.util.Date()).append('\n')
                    append("Thread: ").append(thread.name).append('\n')
                    append("Version: ").append(BuildConfig.VERSION_NAME).append('\n')
                    append("Gerät: ").append(android.os.Build.MANUFACTURER).append(' ')
                        .append(android.os.Build.MODEL).append(" / Android ")
                        .append(android.os.Build.VERSION.RELEASE).append("\n\n")
                    append(sw.toString())
                }
                file(context).writeText(text)
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Liefert den letzten Absturzbericht und löscht ihn. */
    fun takeLast(context: Context): String? {
        val f = file(context)
        if (!f.exists()) return null
        val text = f.readText()
        f.delete()
        return text
    }
}
