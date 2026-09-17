package de.codinix.videoeditor

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Fügt Segmente zusammen und speichert das Ergebnis in der Galerie.
 *
 * Zwei Wege:
 *  - Schnell: alle Segmente haben identische Codec-Parameter und es soll die Original-
 *    auflösung bleiben → [VideoConcat] kopiert die Frames um, fertig in Sekunden.
 *  - Neukodierung: unterschiedliche Auflösungen (z.B. Front-/Rückkamera gemischt) oder
 *    eine kleinere Zielhöhe → Media3 Transformer rendert alles neu. Dauert je nach
 *    Gerät etwa Echtzeit oder länger.
 */
@OptIn(UnstableApi::class)
class Exporter(private val context: Context) {

    /**
     * Ton eines Video-Overlays, der unter die Aufnahme gemischt wird.
     * Das Overlay-Video begann bei [startOffsetMs] der Gesamtaufnahme und läuft
     * (ggf. in Schleife) bis zum Ende.
     */
    data class AudioMix(val file: File, val startOffsetMs: Long, val videoDurationMs: Long)

    interface Listener {
        fun onProgress(percent: Int)
        fun onDone(uri: Uri)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var transformer: Transformer? = null

    /**
     * @param targetHeight gewünschte Ausgabehöhe in Pixeln oder null für Original.
     */
    fun export(
        segments: List<File>, targetHeight: Int?, listener: Listener,
        audioMix: List<AudioMix> = emptyList()
    ) {
        if (segments.isEmpty()) { listener.onError("Keine Segmente"); return }
        val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")

        val info = VideoConcat.inspect(segments.first())
        // Sichtbare Höhe berücksichtigt die Rotation (Hochkant: Breite/Höhe vertauscht).
        val visibleHeight = if (info.rotation == 90 || info.rotation == 270) info.width else info.height
        val needsScale = targetHeight != null && targetHeight < visibleHeight

        if (audioMix.isNotEmpty()) {
            // Ton mischen geht nur über Media3 (Neukodierung)
            transform(segments, targetHeight, outFile, listener, audioMix)
            return
        }
        if (!needsScale && VideoConcat.canFastConcat(segments)) {
            worker.execute {
                try {
                    VideoConcat.concat(segments, outFile) { p -> main.post { listener.onProgress(p) } }
                    val uri = saveToGallery(outFile)
                    outFile.delete()
                    main.post { listener.onDone(uri) }
                } catch (e: Exception) {
                    Log.e(TAG, "Schnelles Zusammenfügen fehlgeschlagen, versuche Neukodierung", e)
                    main.post { transform(segments, targetHeight, outFile, listener) }
                }
            }
        } else {
            transform(segments, targetHeight, outFile, listener)
        }
    }

    private fun transform(
        segments: List<File>, targetHeight: Int?, outFile: File, listener: Listener,
        audioMix: List<AudioMix> = emptyList()
    ) {
        val videoEffects = buildList {
            if (targetHeight != null) add(Presentation.createForHeight(targetHeight))
        }
        val items = segments.map { f ->
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(f)))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence(items))

        val totalMs = segments.sumOf { VideoConcat.durationUs(it) } / 1000
        for (mix in audioMix) {
            try {
                buildAudioSequence(mix, totalMs)?.let { sequences.add(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Overlay-Ton konnte nicht vorbereitet werden", e)
            }
        }
        val composition = Composition.Builder(sequences).build()

        val t = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    worker.execute {
                        try {
                            val uri = saveToGallery(outFile)
                            outFile.delete()
                            main.post { listener.onDone(uri) }
                        } catch (e: Exception) {
                            main.post { listener.onError(e.message ?: "Speichern fehlgeschlagen") }
                        }
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    Log.e(TAG, "Transformer-Fehler", exportException)
                    transformer = null
                    listener.onError("Neukodierung fehlgeschlagen (Code ${exportException.errorCode})")
                }
            })
            .build()
        transformer = t
        t.start(composition, outFile.absolutePath)

        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                val tr = transformer ?: return
                val state = tr.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) listener.onProgress(holder.progress)
                main.postDelayed(this, 400)
            }
        }
        main.postDelayed(poll, 400)
    }

    /**
     * Baut die Tonspur eines Overlay-Videos: Stille bis zum Einfügezeitpunkt, dann das
     * Video (nur Ton) so oft wiederholt, bis die Gesamtlänge erreicht ist.
     */
    private fun buildAudioSequence(mix: AudioMix, totalMs: Long): EditedMediaItemSequence? {
        val audioFmt = VideoConcat.inspect(mix.file).audioFormat ?: return null
        val remaining = totalMs - mix.startOffsetMs
        if (remaining <= 0 || mix.videoDurationMs <= 0) return null

        val items = mutableListOf<EditedMediaItem>()
        if (mix.startOffsetMs > 200) {
            val sampleRate = audioFmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            val channels = audioFmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            val silence = writeSilenceWav(mix.startOffsetMs, sampleRate, channels)
            items.add(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silence))).build())
        }
        var left = remaining
        while (left > 0) {
            val clipMs = minOf(left, mix.videoDurationMs)
            val media = MediaItem.Builder()
                .setUri(Uri.fromFile(mix.file))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setEndPositionMs(clipMs).build()
                )
                .build()
            items.add(EditedMediaItem.Builder(media).setRemoveVideo(true).build())
            left -= clipMs
            if (items.size > 200) break
        }
        return EditedMediaItemSequence(items)
    }

    /** Erzeugt eine WAV-Datei mit Stille (16 Bit PCM) in Sample-Rate und Kanalzahl des Overlay-Tons. */
    private fun writeSilenceWav(durationMs: Long, sampleRate: Int, channels: Int): File {
        val file = File(context.cacheDir, "silence_${durationMs}_${sampleRate}_$channels.wav")
        if (file.exists()) return file
        val frames = (sampleRate * durationMs / 1000).toInt()
        val dataBytes = frames * channels * 2
        java.io.DataOutputStream(java.io.BufferedOutputStream(file.outputStream())).use { out ->
            fun le32(v: Int) { out.writeByte(v and 0xFF); out.writeByte((v shr 8) and 0xFF); out.writeByte((v shr 16) and 0xFF); out.writeByte((v shr 24) and 0xFF) }
            fun le16(v: Int) { out.writeByte(v and 0xFF); out.writeByte((v shr 8) and 0xFF) }
            out.writeBytes("RIFF"); le32(36 + dataBytes); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(channels)
            le32(sampleRate); le32(sampleRate * channels * 2); le16(channels * 2); le16(16)
            out.writeBytes("data"); le32(dataBytes)
            val zeros = ByteArray(64 * 1024)
            var written = 0
            while (written < dataBytes) {
                val n = minOf(zeros.size, dataBytes - written)
                out.write(zeros, 0, n); written += n
            }
        }
        return file
    }

    private fun saveToGallery(file: File): Uri {
        val name = "VideoEditor_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY)
            .format(System.currentTimeMillis()) + ".mp4"
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoEditor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore-Eintrag fehlgeschlagen")
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoEditor")
            dir.mkdirs()
            val dest = File(dir, name)
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
            return Uri.fromFile(dest)
        }
    }

    fun release() {
        transformer?.cancel()
        transformer = null
        worker.shutdown()
    }

    companion object {
        private const val TAG = "Exporter"
    }
}
