package de.codinix.videoeditor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Baut die komplette Tonspur eines Overlay-Videos für den Export selbst zusammen:
 * Stille bis zum Einfügezeitpunkt, dann der Ton des Videos in Schleife bis zum Ende,
 * mit Lautstärke – als eine einzige WAV-Datei. Media3 bekommt nur noch diese Datei.
 *
 * Vorteil: Zeitverlauf, Pausen und Lautstärke liegen vollständig in unserer Hand,
 * unabhängig davon, wie Media3 Effekte auf mehrteilige Sequenzen anwendet.
 */
object OverlayAudioRenderer {

    private const val TAG = "OverlayAudio"

    /** Ein Abschnitt der Zeitleiste: ab [fromMs] gilt [gain]; [playing]=false bedeutet Stille. */
    data class Segment(val fromMs: Long, val gain: Float, val playing: Boolean)

    class Decoded(val pcm: File, val sampleRate: Int, val channels: Int) {
        val bytesPerFrame get() = channels * 2
        val frames get() = pcm.length() / bytesPerFrame
        val durationMs get() = frames * 1000 / sampleRate
    }

    /**
     * Dekodiert den Audio-Track einer Datei in rohes 16-Bit-PCM (interleaved).
     * Ergebnis wird im Cache abgelegt und beim nächsten Aufruf wiederverwendet.
     */
    fun decode(src: File, cacheDir: File): Decoded? {
        val key = "${src.name}_${src.length()}"
        val cachedPcm = File(cacheDir, "pcm_$key.raw")
        val cachedMeta = File(cacheDir, "pcm_$key.meta")
        if (cachedPcm.exists() && cachedMeta.exists() && cachedPcm.length() > 0) {
            try {
                val (sr, ch) = cachedMeta.readText().split(",").map { it.trim().toInt() }
                return Decoded(cachedPcm, sr, ch)
            } catch (_: Exception) { }
        }
        val fresh = decodeFresh(src, cacheDir) ?: return null
        return try {
            if (!fresh.pcm.renameTo(cachedPcm)) { fresh.pcm.copyTo(cachedPcm, overwrite = true); fresh.pcm.delete() }
            cachedMeta.writeText("${fresh.sampleRate},${fresh.channels}")
            Decoded(cachedPcm, fresh.sampleRate, fresh.channels)
        } catch (e: Exception) { fresh }
    }

    private fun decodeFresh(src: File, cacheDir: File): Decoded? {
        val extractor = MediaExtractor()
        extractor.setDataSource(src.absolutePath)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; format = f; break }
        }
        if (track < 0 || format == null) { extractor.release(); return null }
        extractor.selectTrack(track)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val out = File(cacheDir, "ovl_pcm_${System.currentTimeMillis()}.raw")
        val raf = RandomAccessFile(out, "rw")
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val oidx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oidx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    oidx >= 0 -> {
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(oidx)!!
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buf.get(bytes)
                            raf.write(bytes)
                        }
                        codec.releaseOutputBuffer(oidx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            raf.close()
            codec.stop(); codec.release()
            extractor.release()
        }
        if (out.length() == 0L) { out.delete(); return null }
        return Decoded(out, sampleRate, channels)
    }

    /**
     * Rendert die Zeitleiste in eine WAV-Datei.
     *
     * @param timeline Abschnitte in aufsteigender Zeit. Vor dem ersten Abschnitt ist Stille.
     *        Während "playing" läuft das Quellmaterial weiter (Schleife), bei Pause steht es.
     * @param totalMs Länge der fertigen Spur = Länge des Videos.
     */
    fun render(decoded: Decoded, timeline: List<Segment>, totalMs: Long, out: File) {
        val sr = decoded.sampleRate
        val ch = decoded.channels
        val bpf = decoded.bytesPerFrame
        val totalFrames = totalMs * sr / 1000
        val srcFrames = decoded.frames
        if (srcFrames <= 0) throw IllegalStateException("Kein Ton im Overlay")

        val src = RandomAccessFile(decoded.pcm, "r")
        val wav = RandomAccessFile(out, "rw")
        wav.setLength(0)
        wav.write(ByteArray(44)) // Header später

        val chunkFrames = sr / 10 // 100 ms
        val inBuf = ByteArray(chunkFrames * bpf)
        val outBuf = ByteBuffer.allocate(chunkFrames * bpf).order(ByteOrder.LITTLE_ENDIAN)
        val inView = ByteBuffer.wrap(inBuf).order(ByteOrder.LITTLE_ENDIAN)

        var frame = 0L          // Position in der Ausgabe
        var srcPos = 0L         // Position im Quellmaterial (Frames, mit Schleife)
        var segIdx = -1
        fun currentSegment(atFrame: Long): Segment? {
            val ms = atFrame * 1000 / sr
            var s: Segment? = null
            for (i in timeline.indices) if (timeline[i].fromMs <= ms) s = timeline[i] else break
            return s
        }

        try {
            while (frame < totalFrames) {
                val n = minOf(chunkFrames.toLong(), totalFrames - frame).toInt()
                val seg = currentSegment(frame)
                outBuf.clear()
                if (seg == null || !seg.playing || seg.gain <= 0.001f) {
                    // Stille
                    for (i in 0 until n * bpf) outBuf.put(0)
                } else {
                    // n Frames aus der Quelle lesen, ggf. über das Ende hinaus umbrechen
                    var got = 0
                    while (got < n) {
                        val pos = srcPos % srcFrames
                        val avail = minOf((n - got).toLong(), srcFrames - pos).toInt()
                        src.seek(pos * bpf)
                        src.readFully(inBuf, got * bpf, avail * bpf)
                        got += avail
                        srcPos += avail
                    }
                    inView.clear()
                    val g = seg.gain
                    for (i in 0 until n * ch) {
                        val v = (inView.getShort(i * 2) * g).toInt().coerceIn(-32768, 32767)
                        outBuf.putShort(v.toShort())
                    }
                }
                wav.write(outBuf.array(), 0, n * bpf)
                frame += n
            }
            // Header
            val dataLen = (totalFrames * bpf).toInt()
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(ch.toShort())
            header.putInt(sr).putInt(sr * bpf).putShort(bpf.toShort()).putShort(16)
            header.put("data".toByteArray()).putInt(dataLen)
            wav.seek(0); wav.write(header.array())
        } finally {
            src.close(); wav.close()
        }
        Log.i(TAG, "WAV gerendert: ${out.length()} Bytes, ${totalMs} ms, gain-Abschnitte=${timeline.size}")
    }
}
