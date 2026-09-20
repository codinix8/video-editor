package de.codinix.videoeditor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File

/**
 * Rechnet ein Video auf höchstens [maxHeight] Zeilen herunter (für Kachel- und PiP-Videos).
 * Ist es bereits klein genug, bleibt die Datei unverändert. Läuft auf dem Main-Thread
 * (Media3-Vorgabe), meldet Fortschritt und Ergebnis zurück.
 */
@OptIn(UnstableApi::class)
object Downscaler {
    private val main = Handler(Looper.getMainLooper())

    /** Sichtbare Höhe (Drehung berücksichtigt) oder 0. */
    fun visibleHeight(file: File): Int = try {
        val mmr = MediaMetadataRetriever(); mmr.setDataSource(file.absolutePath)
        val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        mmr.release()
        if (rot == 90 || rot == 270) w else h
    } catch (e: Exception) { 0 }

    fun needsDownscale(file: File, maxHeight: Int = 1080) = visibleHeight(file) > maxHeight

    fun run(context: Context, src: File, dest: File, maxHeight: Int = 1080,
            onProgress: (Int) -> Unit, onDone: (File?) -> Unit) {
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(src)))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(maxHeight))))
            .build()
        val encoder = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(Exporter.videoBitrateFor(maxHeight)).build())
            .build()
        var transformer: Transformer? = null
        val t = Transformer.Builder(context)
            .setEncoderFactory(encoder)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { transformer = null; onDone(dest) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    transformer = null; dest.delete(); onDone(null)
                }
            })
            .build()
        transformer = t
        t.start(item, dest.absolutePath)
        val holder = ProgressHolder()
        main.postDelayed(object : Runnable {
            override fun run() {
                val tr = transformer ?: return
                if (tr.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                main.postDelayed(this, 400)
            }
        }, 400)
    }
}
