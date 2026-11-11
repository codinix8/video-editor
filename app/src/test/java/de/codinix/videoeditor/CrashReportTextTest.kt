package de.codinix.videoeditor

import org.junit.Assert.assertEquals
import org.junit.Test

/** Crash report header: stored with neutral keys, labelled in the app language when shown. */
class CrashReportTextTest {
    private val labels = mapOf(
        CrashReportText.TIME to "T", CrashReportText.THREAD to "Th",
        CrashReportText.VERSION to "V", CrashReportText.DEVICE to "D",
    )
    private val trace = "java.lang.IllegalStateException: Zeit: x\n\tat a.b(C.kt:1)"

    @Test fun build_writesNeutralKeys() = assertEquals(
        "time: now\nthread: main\nversion: 1.1.5\ndevice: Pixel / Android 15\n\n$trace",
        CrashReportText.build("now", "main", "1.1.5", "Pixel / Android 15", trace),
    )

    @Test fun localise_replacesHeaderLabelsOnly() = assertEquals(
        "T: now\nTh: main\nV: 1.1.5\nD: Pixel / Android 15\n\n$trace",
        CrashReportText.localise(CrashReportText.build("now", "main", "1.1.5", "Pixel / Android 15", trace), labels),
    )

    @Test fun localise_mapsLegacyGermanReports() = assertEquals(
        "T: now\nTh: main\nV: 1.1.4\nD: X / Android 14\n\n$trace",
        CrashReportText.localise("Zeit: now\nThread: main\nVersion: 1.1.4\nGerät: X / Android 14\n\n$trace", labels),
    )

    @Test fun localise_keepsUnknownLinesAndMissingLabels() = assertEquals(
        "other: 1\ntime: now\n\nrest",
        CrashReportText.localise("other: 1\ntime: now\n\nrest", emptyMap()),
    )
}
