package de.codinix.videoeditor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Fügt mehrere MP4-Dateien ohne Neukodierung zusammen (reines Umkopieren der
 * komprimierten Frames). Das ist in Sekunden fertig, funktioniert aber nur,
 * wenn alle Dateien dieselben Codec-Parameter haben. Andernfalls liefert
 * [canFastConcat] false und der Aufrufer muss über Media3 neu kodieren.
 */
object VideoConcat {

    private const val TAG = "VideoConcat"

    data class StreamInfo(
        val videoFormat: MediaFormat?,
        val audioFormat: MediaFormat?,
        val rotation: Int,
        val width: Int,
        val height: Int
    )

    fun inspect(file: File): StreamInfo {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.absolutePath)
            var video: MediaFormat? = null
            var audio: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && video == null) video = f
                if (mime.startsWith("audio/") && audio == null) audio = f
            }
            val rotation = video?.let {
                if (it.containsKey(MediaFormat.KEY_ROTATION)) it.getInteger(MediaFormat.KEY_ROTATION) else 0
            } ?: 0
            return StreamInfo(
                video, audio, rotation,
                video?.getInteger(MediaFormat.KEY_WIDTH) ?: 0,
                video?.getInteger(MediaFormat.KEY_HEIGHT) ?: 0
            )
        } finally {
            ex.release()
        }
    }

    /** Prüft, ob alle Dateien kompatibel für das schnelle Zusammenfügen sind. */
    fun canFastConcat(files: List<File>): Boolean {
        if (files.isEmpty()) return false
        val first = inspect(files.first())
        if (first.videoFormat == null) return false
        return files.drop(1).all { f ->
            val info = inspect(f)
            sameVideo(first, info) && sameAudio(first.audioFormat, info.audioFormat)
        }
    }

    private fun sameVideo(a: StreamInfo, b: StreamInfo): Boolean {
        val fa = a.videoFormat ?: return false
        val fb = b.videoFormat ?: return false
        return fa.getString(MediaFormat.KEY_MIME) == fb.getString(MediaFormat.KEY_MIME) &&
            a.width == b.width && a.height == b.height && a.rotation == b.rotation
    }

    private fun sameAudio(a: MediaFormat?, b: MediaFormat?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a.getString(MediaFormat.KEY_MIME) == b.getString(MediaFormat.KEY_MIME) &&
            a.getInteger(MediaFormat.KEY_SAMPLE_RATE) == b.getInteger(MediaFormat.KEY_SAMPLE_RATE) &&
            a.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == b.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    }

    /**
     * Führt das Zusammenfügen aus. Wirft bei Fehlern eine Exception.
     * [onProgress] erhält Werte 0..100.
     */
    fun concat(files: List<File>, output: File, onProgress: (Int) -> Unit = {}) {
        require(files.isNotEmpty())
        val first = inspect(files.first())
        val videoFmt = requireNotNull(first.videoFormat) { "Kein Video-Track" }

        if (output.exists()) output.delete()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.setOrientationHint(first.rotation)
        val outVideo = muxer.addTrack(videoFmt)
        val outAudio = first.audioFormat?.let { muxer.addTrack(it) } ?: -1
        muxer.start()

        val bufferSize = maxOf(
            videoFmt.optInt(MediaFormat.KEY_MAX_INPUT_SIZE, 0),
            first.audioFormat?.optInt(MediaFormat.KEY_MAX_INPUT_SIZE, 0) ?: 0,
            4 * 1024 * 1024
        )
        val buffer = ByteBuffer.allocateDirect(bufferSize)
        val info = MediaCodec.BufferInfo()

        var offsetUs = 0L
        val totalDurationUs = files.sumOf { durationUs(it) }.coerceAtLeast(1)

        try {
            files.forEach { file ->
                val ex = MediaExtractor()
                ex.setDataSource(file.absolutePath)
                var videoIdx = -1
                var audioIdx = -1
                var videoDurUs = 0L
                var audioDurUs = 0L
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && videoIdx < 0) {
                        videoIdx = i; videoDurUs = f.optLong(MediaFormat.KEY_DURATION, 0)
                    }
                    if (mime.startsWith("audio/") && audioIdx < 0) {
                        audioIdx = i; audioDurUs = f.optLong(MediaFormat.KEY_DURATION, 0)
                    }
                }

                var lastVideoUs = 0L
                var lastAudioUs = 0L
                if (videoIdx >= 0) {
                    lastVideoUs = copyTrack(ex, videoIdx, muxer, outVideo, buffer, info, offsetUs) {
                        onProgress((((offsetUs + it) * 100) / totalDurationUs).toInt().coerceIn(0, 99))
                    }
                }
                if (audioIdx >= 0 && outAudio >= 0) {
                    lastAudioUs = copyTrack(ex, audioIdx, muxer, outAudio, buffer, info, offsetUs) {}
                }
                ex.release()

                // Nächste Datei beginnt nach dem längeren der beiden Tracks.
                val fileEnd = maxOf(videoDurUs, audioDurUs, lastVideoUs + 33_000, lastAudioUs + 23_000)
                offsetUs += fileEnd
            }
        } finally {
            try { muxer.stop() } catch (e: Exception) { Log.w(TAG, "muxer.stop", e) }
            muxer.release()
        }
        onProgress(100)
    }

    /** Kopiert einen Track. Gibt die letzte (unverschobene) Präsentationszeit zurück. */
    private fun copyTrack(
        ex: MediaExtractor, trackIdx: Int, muxer: MediaMuxer, outTrack: Int,
        buffer: ByteBuffer, info: MediaCodec.BufferInfo, offsetUs: Long,
        onPts: (Long) -> Unit
    ): Long {
        ex.selectTrack(trackIdx)
        ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        var lastUs = 0L
        var counter = 0
        while (true) {
            buffer.clear()
            val size = ex.readSampleData(buffer, 0)
            if (size < 0) break
            val pts = ex.sampleTime
            if (pts < 0) { ex.advance(); continue }
            info.offset = 0
            info.size = size
            info.presentationTimeUs = pts + offsetUs
            info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            muxer.writeSampleData(outTrack, buffer, info)
            lastUs = pts
            if (++counter % 30 == 0) onPts(pts)
            ex.advance()
        }
        ex.unselectTrack(trackIdx)
        return lastUs
    }

    fun durationUs(file: File): Long {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(file.absolutePath)
            var d = 0L
            for (i in 0 until ex.trackCount) {
                d = maxOf(d, ex.getTrackFormat(i).optLong(MediaFormat.KEY_DURATION, 0))
            }
            d
        } catch (e: Exception) { 0L } finally { ex.release() }
    }

    private fun MediaFormat.optInt(key: String, def: Int) = if (containsKey(key)) getInteger(key) else def
    private fun MediaFormat.optLong(key: String, def: Long) = if (containsKey(key)) getLong(key) else def
}
