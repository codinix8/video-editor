package de.codinix.videoeditor.whisper

import de.codinix.videoeditor.OverlayAudioRenderer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bereitet die Mikrofonspur der Segmente für Whisper auf: 16 kHz, Mono, Float.
 * Jedes Segment wird auf seine Aufnahmedauer gebracht (auffüllen/kürzen), damit die
 * Zeitstempel zur Zeitleiste der Review passen.
 */
object AudioPrep {
    const val RATE = 16000

    fun prepare(segments: List<Pair<File, Long>>, cacheDir: File, onProgress: (Int) -> Unit): FloatArray {
        val totalMs = segments.sumOf { it.second }
        val out = FloatArray((totalMs * RATE / 1000).toInt())
        var offset = 0
        segments.forEachIndexed { idx, (file, durMs) ->
            val frames = (durMs * RATE / 1000).toInt()
            val decoded = OverlayAudioRenderer.decode(file, cacheDir)
            if (decoded != null) {
                val raf = RandomAccessFile(decoded.pcm, "r")
                try {
                    val srcRate = decoded.sampleRate; val ch = decoded.channels
                    val srcFrames = decoded.frames
                    val step = srcRate.toDouble() / RATE
                    val chunk = 4096
                    val buf = ByteArray((chunk * step).toInt() * ch * 2 + ch * 2 * 4)
                    var i = 0
                    while (i < frames) {
                        val n = minOf(chunk, frames - i)
                        val srcStart = (i * step).toLong()
                        val need = (n * step).toInt() + 2
                        val avail = (srcFrames - srcStart).coerceAtLeast(0).toInt()
                        val read = minOf(need, avail)
                        if (read <= 0) break
                        raf.seek(srcStart * ch * 2)
                        raf.readFully(buf, 0, read * ch * 2)
                        val view = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                        for (k in 0 until n) {
                            val si = ((k * step).toInt()).coerceAtMost(read - 1)
                            var acc = 0f
                            for (c in 0 until ch) acc += view.getShort((si * ch + c) * 2)
                            out[offset + i + k] = acc / (ch * 32768f)
                        }
                        i += n
                    }
                } finally { raf.close() }
            }
            offset += frames
            onProgress(((idx + 1) * 100) / segments.size)
        }
        return out
    }
}
