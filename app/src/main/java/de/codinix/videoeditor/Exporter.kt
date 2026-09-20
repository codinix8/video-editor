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
    /**
     * Ton eines Overlay-Videos. [timeline] beschreibt Abschnitte (ab wann, welche Lautstärke,
     * läuft/pausiert); ohne Angabe: ab [startOffsetMs] durchgehend mit [gain].
     */
    data class AudioMix(
        val file: File, val startOffsetMs: Long, val videoDurationMs: Long, val gain: Float = 1f,
        val timeline: List<OverlayAudioRenderer.Segment>? = null,
        /** Ende der Spur (Overlay entfernt) oder null = bis zum Ende des Videos. */
        val endOffsetMs: Long? = null
    ) {
        fun effectiveTimeline(): List<OverlayAudioRenderer.Segment> {
            val base = timeline?.takeIf { it.isNotEmpty() } ?: listOf(OverlayAudioRenderer.Segment(startOffsetMs, gain, true))
            val end = endOffsetMs ?: return base
            return base.filter { it.fromMs < end } + OverlayAudioRenderer.Segment(end, 0f, false)
        }
    }

    companion object {
        private const val TAG = "Exporter"
        const val AUDIO_BITRATE = 192_000

        /** Feste Video-Bitraten (H.264, 30 fps) je Ausgabehöhe – macht Größe vorhersagbar. */
        fun videoBitrateFor(height: Int): Int = when {
            height >= 2160 -> 45_000_000
            height >= 1440 -> 24_000_000
            height >= 1080 -> 14_000_000
            height >= 720 -> 7_000_000
            else -> 3_000_000
        }

        /** Rendert alle Overlay-Tonspuren zusammen in eine WAV-Datei. */
        fun renderMixWav(context: Context, mixes: List<AudioMix>, totalMs: Long, out: File): Boolean {
            val tracks = mixes.mapNotNull { mix ->
                val decoded = OverlayAudioRenderer.decode(mix.file, context.cacheDir) ?: return@mapNotNull null
                OverlayAudioRenderer.Track(decoded, mix.effectiveTimeline())
            }
            if (tracks.isEmpty()) return false
            OverlayAudioRenderer.renderMix(tracks, totalMs, out)
            return true
        }
    }

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
    /** Untertitel für den Export (leer = keine). */
    var captions: List<de.codinix.videoeditor.whisper.Caption> = emptyList()
    var captionSettings = de.codinix.videoeditor.whisper.CaptionSettings()

    fun export(
        segments: List<File>, targetHeight: Int?, listener: Listener,
        audioMix: List<AudioMix> = emptyList(),
        micGain: Float = 1f
    ) {
        if (segments.isEmpty()) { listener.onError("Keine Segmente"); return }
        val outFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")

        val info = VideoConcat.inspect(segments.first())
        // Sichtbare Höhe berücksichtigt die Rotation (Hochkant: Breite/Höhe vertauscht).
        val visibleHeight = if (info.rotation == 90 || info.rotation == 270) info.width else info.height
        val needsScale = targetHeight != null && targetHeight < visibleHeight

        if (audioMix.isNotEmpty()) {
            // Overlay-Ton zuerst selbst zu fertigen WAV-Spuren rendern, dann mit Media3 mischen
            listener.onProgress(0)
            worker.execute {
                try {
                    val totalMs = segments.sumOf { VideoConcat.durationUs(it) } / 1000
                    val wav = File(context.cacheDir, "ovl_mix_${System.currentTimeMillis()}.wav")
                    val wavs = if (renderMixWav(context, audioMix, totalMs, wav)) listOf(wav) else emptyList()
                    main.post { transform(segments, targetHeight, outFile, listener, wavs, micGain) }
                } catch (e: Exception) {
                    Log.e(TAG, "Overlay-Ton rendern fehlgeschlagen", e)
                    main.post { listener.onError("Overlay-Ton: ${e.message}") }
                }
            }
            return
        }
        if (!isUnity(micGain) || captions.isNotEmpty()) {
            transform(segments, targetHeight, outFile, listener, emptyList(), micGain)
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
        audioWavs: List<File> = emptyList(),
        micGain: Float = 1f
    ) {
        val videoEffects = buildList {
            if (targetHeight != null) add(Presentation.createForHeight(targetHeight))
        }
        val micProcessors = gainProcessors(micGain)

        // Ausgabegröße (aufrecht) für die Untertitel-Bitmaps
        val info0 = VideoConcat.inspect(segments.first())
        val rot = info0.rotation == 90 || info0.rotation == 270
        val visW = if (rot) info0.height else info0.width
        val visH = if (rot) info0.width else info0.height
        val outH = targetHeight?.takeIf { it < visH } ?: visH
        val outW = if (visH > 0) (visW.toLong() * outH / visH).toInt() else visW

        var offsetUs = 0L
        val items = segments.map { f ->
            val effects = ArrayList<androidx.media3.common.Effect>(videoEffects)
            if (captions.isNotEmpty() && outW > 0 && outH > 0) {
                val overlay = de.codinix.videoeditor.whisper.CaptionOverlay(captions, captionSettings, outW, outH, offsetUs)
                effects.add(androidx.media3.effect.OverlayEffect(com.google.common.collect.ImmutableList.of<androidx.media3.effect.TextureOverlay>(overlay)))
            }
            offsetUs += VideoConcat.durationUs(f)
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(f)))
                .setEffects(Effects(micProcessors, effects))
                .build()
        }
        val sequences = mutableListOf(EditedMediaItemSequence(items))
        // Fertig gerenderte Overlay-Tonspuren (eine Datei = eine Sequenz, exakt so lang wie das Video)
        for (wav in audioWavs) {
            sequences.add(EditedMediaItemSequence(listOf(
                EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(wav))).build()
            )))
        }
        val composition = Composition.Builder(sequences).build()

        val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                androidx.media3.transformer.VideoEncoderSettings.Builder().setBitrate(videoBitrateFor(outH)).build())
            .build()
        val t = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
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

    private fun isUnity(gain: Float) = kotlin.math.abs(gain - 1f) < 0.01f

    /**
     * Lautstärke-Anpassung als Audio-Prozessor: eine Kanal-Mischmatrix, die jeden Kanal
     * mit [gain] skaliert (Mono und Stereo). Bei 1.0 wird nichts eingehängt.
     */
    private fun gainProcessors(gain: Float): List<androidx.media3.common.audio.AudioProcessor> {
        if (isUnity(gain)) return emptyList()
        val g = Loudness.gain(gain.coerceIn(0f, 2f))
        val proc = androidx.media3.common.audio.ChannelMixingAudioProcessor()
        for (ch in 1..2) {
            proc.putChannelMixingMatrix(
                androidx.media3.common.audio.ChannelMixingMatrix.create(ch, ch).scaleBy(g)
            )
        }
        return listOf(proc)
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


}
