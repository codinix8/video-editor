package de.codinix.videoeditor

/**
 * Header of a crash report. The file stores stable, language-neutral keys
 * ("time", "thread", "version", "device"); the labels are localised only when the
 * report is shown, with the activity context, so the in-app language applies.
 * Android-free so it can be tested on the JVM.
 */
object CrashReportText {
    const val TIME = "time"
    const val THREAD = "thread"
    const val VERSION = "version"
    const val DEVICE = "device"

    /** Keys written by versions up to 1.1.4 (German labels), mapped to the neutral keys. */
    private val legacy = mapOf("Zeit" to TIME, "Thread" to THREAD, "Version" to VERSION, "Gerät" to DEVICE)
    private val keys = setOf(TIME, THREAD, VERSION, DEVICE)

    /** Builds the stored report: neutral header lines, a blank line, then the stack trace. */
    fun build(time: String, thread: String, version: String, device: String, stackTrace: String): String = buildString {
        append(TIME).append(": ").append(time).append('\n')
        append(THREAD).append(": ").append(thread).append('\n')
        append(VERSION).append(": ").append(version).append('\n')
        append(DEVICE).append(": ").append(device).append("\n\n")
        append(stackTrace)
    }

    /**
     * Replaces the header keys (up to the first blank line) with the localised [labels]
     * (key -> label). Unknown lines, missing labels and the stack trace stay unchanged.
     */
    fun localise(report: String, labels: Map<String, String>): String {
        val lines = report.split('\n')
        val headerEnd = lines.indexOfFirst { it.isEmpty() }.let { if (it < 0) lines.size else it }
        return lines.mapIndexed { i, line ->
            if (i >= headerEnd) return@mapIndexed line
            val sep = line.indexOf(": ")
            if (sep <= 0) return@mapIndexed line
            val raw = line.substring(0, sep)
            val key = if (raw in keys) raw else legacy[raw] ?: return@mapIndexed line
            val label = labels[key] ?: return@mapIndexed line
            label + line.substring(sep)
        }.joinToString("\n")
    }
}
