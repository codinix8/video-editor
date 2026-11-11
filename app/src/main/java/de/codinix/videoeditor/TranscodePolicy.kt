package de.codinix.videoeditor

/**
 * Pure decision rule behind [Downscaler.transcodeReason], free of Android classes so it can be
 * unit-tested on the JVM. Downscaler reads the track format; this object decides.
 *
 * Rule (unchanged): convert when the video is not H.264 (e.g. HEVC/AV1), higher than 1080p
 * (rotation considered), above 31 fps, or HDR (PQ/ST 2084 or HLG). Checked in that order.
 */
object TranscodePolicy {
    /** Same values as android.media.MediaFormat.COLOR_TRANSFER_ST2084 / COLOR_TRANSFER_HLG. */
    const val COLOR_TRANSFER_ST2084 = 6
    const val COLOR_TRANSFER_HLG = 7

    /** Visible height: width and height swap for a 90°/270° rotation. */
    fun visibleHeight(width: Int, height: Int, rotation: Int): Int =
        if (rotation == 90 || rotation == 270) width else height

    /** Reason as display text (e.g. "HEVC", "2160p", "60 fps", "HDR") or null if no conversion is needed. */
    fun reason(mime: String, width: Int, height: Int, rotation: Int, fps: Int, colorTransfer: Int): String? {
        val visH = visibleHeight(width, height, rotation)
        val hdr = colorTransfer == COLOR_TRANSFER_ST2084 || colorTransfer == COLOR_TRANSFER_HLG
        return when {
            mime != "video/avc" -> mime.removePrefix("video/").uppercase()
            visH > 1080 -> "${visH}p"
            fps > 31 -> "$fps fps"
            hdr -> "HDR"
            else -> null
        }
    }
}
