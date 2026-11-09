package de.codinix.videoeditor

import android.media.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TranscodePolicy copies two MediaFormat constants so it stays free of Android classes.
 * This guards the copy (compile-time constants, so the android.jar stub is enough).
 */
class MediaFormatConstantsTest {
    @Test fun hdrTransferConstantsMatchAndroid() {
        assertEquals(MediaFormat.COLOR_TRANSFER_ST2084, TranscodePolicy.COLOR_TRANSFER_ST2084)
        assertEquals(MediaFormat.COLOR_TRANSFER_HLG, TranscodePolicy.COLOR_TRANSFER_HLG)
    }
}
