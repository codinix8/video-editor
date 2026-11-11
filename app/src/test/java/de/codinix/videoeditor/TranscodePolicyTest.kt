package de.codinix.videoeditor

import de.codinix.videoeditor.TranscodePolicy.COLOR_TRANSFER_HLG
import de.codinix.videoeditor.TranscodePolicy.COLOR_TRANSFER_ST2084
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Rule behind Downscaler.transcodeReason: HEVC/AV1, >1080p, >31 fps, HDR. */
class TranscodePolicyTest {
    private val sdr = 0

    @Test fun h264_1080p_30fps_sdr_needsNoConversion() =
        assertNull(TranscodePolicy.reason("video/avc", 1920, 1080, 0, 30, sdr))

    @Test fun hevc_isConverted() =
        assertEquals("HEVC", TranscodePolicy.reason("video/hevc", 1920, 1080, 0, 30, sdr))

    @Test fun av1_isConverted() =
        assertEquals("AV01", TranscodePolicy.reason("video/av01", 1280, 720, 0, 30, sdr))

    @Test fun above1080p_isConverted() =
        assertEquals("2160p", TranscodePolicy.reason("video/avc", 3840, 2160, 0, 30, sdr))

    @Test fun exactly1080p_isNotConverted() =
        assertNull(TranscodePolicy.reason("video/avc", 1080, 1080, 0, 30, sdr))

    @Test fun rotatedLandscapeFile_usesWidthAsVisibleHeight() =
        assertEquals("1920p", TranscodePolicy.reason("video/avc", 1920, 1080, 90, 30, sdr))

    @Test fun portraitPhoneVideo_rotated270_1080wide_isNotConverted() =
        assertNull(TranscodePolicy.reason("video/avc", 1080, 1920, 270, 30, sdr))

    @Test fun rotation180_keepsHeight() =
        assertNull(TranscodePolicy.reason("video/avc", 1920, 1080, 180, 30, sdr))

    @Test fun fps31_isTolerated() =
        assertNull(TranscodePolicy.reason("video/avc", 1920, 1080, 0, 31, sdr))

    @Test fun fps60_isConverted() =
        assertEquals("60 fps", TranscodePolicy.reason("video/avc", 1920, 1080, 0, 60, sdr))

    @Test fun hdrPq_isConverted() =
        assertEquals("HDR", TranscodePolicy.reason("video/avc", 1920, 1080, 0, 30, COLOR_TRANSFER_ST2084))

    @Test fun hdrHlg_isConverted() =
        assertEquals("HDR", TranscodePolicy.reason("video/avc", 1920, 1080, 0, 30, COLOR_TRANSFER_HLG))

    @Test fun sdrVideoTransfer_isNotHdr() =
        assertNull(TranscodePolicy.reason("video/avc", 1920, 1080, 0, 30, 3 /* COLOR_TRANSFER_SDR_VIDEO */))

    @Test fun codecReasonWinsOverResolutionAndFps() =
        assertEquals("HEVC", TranscodePolicy.reason("video/hevc", 3840, 2160, 0, 60, COLOR_TRANSFER_HLG))

    @Test fun resolutionReasonWinsOverFpsAndHdr() =
        assertEquals("2160p", TranscodePolicy.reason("video/avc", 3840, 2160, 0, 60, COLOR_TRANSFER_HLG))

    @Test fun fpsReasonWinsOverHdr() =
        assertEquals("60 fps", TranscodePolicy.reason("video/avc", 1920, 1080, 0, 60, COLOR_TRANSFER_HLG))
}
