package de.codinix.videoeditor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.codinix.videoeditor.databinding.ActivityMainBinding
import de.codinix.videoeditor.gl.CompositorEffect
import de.codinix.videoeditor.gl.CompositorProcessor
import de.codinix.videoeditor.overlay.ImageOverlay
import de.codinix.videoeditor.overlay.OverlayStore
import android.graphics.BitmapFactory
import java.io.File
import java.util.Locale

/**
 * Schritt 1: Segment-Aufnahme im TikTok-Stil.
 *
 *  - Roter Knopf: startet ein neues Segment bzw. beendet das laufende (Pause).
 *  - Löschtaste: erster Tipp markiert das letzte Segment gelb, zweiter Tipp löscht es.
 *  - Häkchen: fügt alle Segmente zusammen und speichert in der Galerie.
 *  - Kamera-Wechsel und Auflösungswahl: nur zwischen Segmenten.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val main = Handler(Looper.getMainLooper())

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    /** Vom Nutzer gewünschte Qualität. Null = höchste, die die aktuelle Kamera kann. */
    private var preferredQuality: Quality? = null
    private var supportedQualities: List<Quality> = emptyList()

    private data class Segment(val file: File, val durationMs: Long)
    private val segments = mutableListOf<Segment>()
    private var liveDurationMs = 0L
    private var deleteArmed = false

    private var exporter: Exporter? = null

    // Render-Pipeline und Overlays
    private val overlayStore = OverlayStore()
    private lateinit var compositor: CompositorProcessor
    private lateinit var compositorEffect: CompositorEffect

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addImageOverlay(uri)
    }

    private var inReview = false
    private var player: ExoPlayer? = null
    private val playbackTicker = object : Runnable {
        override fun run() {
            updateReviewPosition()
            main.postDelayed(this, 100)
        }
    }

    private val segmentDir by lazy { File(cacheDir, "segments").apply { mkdirs() } }

    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (requiredPermissions.all { results[it] == true }) startCamera()
            else binding.statusText.text = getString(R.string.permission_needed)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Alte Segmente aus einer abgestürzten Sitzung wegräumen.
        segmentDir.listFiles()?.forEach { it.delete() }

        compositor = CompositorProcessor(overlayStore)
        compositorEffect = CompositorEffect(compositor)
        compositor.onFrameAspectChanged = { aspect -> main.post { binding.gestureView.frameAspect = aspect } }

        binding.gestureView.store = overlayStore
        binding.gestureView.onSelectionChanged = { sel ->
            binding.removeOverlayButton.visibility =
                if (sel != null) android.view.View.VISIBLE else android.view.View.GONE
        }
        binding.addImageButton.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.removeOverlayButton.setOnClickListener {
            overlayStore.selectedId?.let { overlayStore.remove(it) }
            binding.removeOverlayButton.visibility = android.view.View.GONE
            binding.gestureView.invalidate()
        }

        binding.recordButton.setOnClickListener { toggleRecording() }
        binding.flipButton.setOnClickListener { flipCamera() }
        binding.qualityButton.setOnClickListener { showQualityDialog() }
        binding.deleteButton.setOnClickListener { onDeletePressed() }
        binding.finishButton.setOnClickListener { onFinishPressed() }
        binding.review.backButton.setOnClickListener { exitReview() }
        binding.review.reviewSaveButton.setOnClickListener { showExportDialog() }
        binding.review.reviewDeleteButton.setOnClickListener { onDeletePressed() }
        binding.review.playerView.setOnClickListener { togglePlayback() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (inReview) { exitReview(); return }
                if (segments.isEmpty() && activeRecording == null) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    confirmDiscard()
                }
            }
        })

        if (hasAllPermissions()) startCamera() else permissionLauncher.launch(requiredPermissions)
        refreshUi()
    }

    private fun hasAllPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------- Kamera

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Herausfinden, was diese Kamera kann, und den Wunsch des Nutzers darauf abbilden.
        val cameraInfo = provider.availableCameraInfos.firstOrNull { selector.filter(listOf(it)).isNotEmpty() }
        val fromDevice = cameraInfo?.let {
            Recorder.getVideoCapabilities(it).getSupportedQualities(DynamicRange.SDR)
        }?.filter { it in QUALITY_ORDER }?.sortedBy { QUALITY_ORDER.indexOf(it) }
        supportedQualities = if (fromDevice.isNullOrEmpty()) QUALITY_ORDER else fromDevice

        val wanted = preferredQuality?.takeIf { it in supportedQualities } ?: supportedQualities.first()
        val qualitySelector = QualitySelector.from(wanted, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.previewView.surfaceProvider
        }
        val recorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
        // Frontkamera gespiegelt aufnehmen, damit Aufnahme = Vorschau (Overlays sitzen sonst falsch).
        val capture = VideoCapture.Builder(recorder)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
        videoCapture = capture

        try {
            provider.unbindAll()
            val group = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(capture)
                .addEffect(compositorEffect)
            binding.previewView.viewPort?.let { group.setViewPort(it) }
            camera = provider.bindToLifecycle(this, selector, group.build())
            binding.qualityButton.text = label(wanted)
        } catch (e: Exception) {
            Log.e(TAG, "bind fehlgeschlagen", e)
            binding.statusText.text = getString(R.string.error, e.message ?: "bind")
        }
    }

    private fun flipCamera() {
        if (activeRecording != null) {
            // Laufendes Segment sauber beenden, dann wechseln.
            activeRecording?.stop()
            activeRecording = null
            main.postDelayed({ doFlip() }, 350)
        } else doFlip()
    }

    private fun doFlip() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCamera()
    }

    private fun showQualityDialog() {
        if (activeRecording != null) return
        val labels = supportedQualities.map { label(it) }.toTypedArray()
        val current = supportedQualities.indexOf(preferredQuality ?: supportedQualities.first()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.quality_title)
            .setSingleChoiceItems(labels, current) { d, which ->
                preferredQuality = supportedQualities[which]
                bindCamera()
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- Aufnahme

    private fun toggleRecording() {
        val capture = videoCapture ?: return
        disarmDelete()

        activeRecording?.let {
            it.stop()          // Finalize-Event legt das Segment an
            activeRecording = null
            return
        }

        val file = File(segmentDir, "seg_${System.currentTimeMillis()}.mp4")
        val pending = capture.output.prepareRecording(this, FileOutputOptions.Builder(file).build())
        val micGranted = PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PermissionChecker.PERMISSION_GRANTED
        if (micGranted) pending.withAudioEnabled()

        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    liveDurationMs = 0
                    refreshUi()
                }
                is VideoRecordEvent.Status -> {
                    liveDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                    refreshUi()
                }
                is VideoRecordEvent.Finalize -> onSegmentFinalized(event, file)
            }
        }
    }

    private fun onSegmentFinalized(event: VideoRecordEvent.Finalize, file: File) {
        activeRecording = null
        val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
        // Auch bei manchen "Fehlern" (z.B. App in den Hintergrund) ist die Datei brauchbar.
        val usable = file.exists() && file.length() > 0 && durationMs > 200
        if (usable) {
            segments.add(Segment(file, durationMs))
        } else {
            file.delete()
            if (event.hasError()) {
                Log.e(TAG, "Segment fehlgeschlagen: ${event.error}", event.cause)
                Toast.makeText(this, getString(R.string.error, "Code ${event.error}"), Toast.LENGTH_SHORT).show()
            }
        }
        liveDurationMs = 0
        refreshUi()
    }

    // ---------------------------------------------------------------- Löschen / Fertig

    private fun onDeletePressed() {
        if (activeRecording != null) return
        if (segments.isEmpty()) {
            Toast.makeText(this, R.string.no_segments, Toast.LENGTH_SHORT).show(); return
        }
        if (!deleteArmed) {
            deleteArmed = true
            if (inReview) {
                binding.review.reviewStatus.text = getString(R.string.tap_again_delete)
                // Zum letzten Segment springen, damit man sieht, was weg käme.
                player?.let { it.seekTo(segments.lastIndex, 0); it.play() }
                binding.review.playIcon.visibility = android.view.View.GONE
            } else {
                binding.statusText.text = getString(R.string.tap_again_delete)
                binding.segmentBar.update(segments.map { it.durationMs }, 0, true)
            }
            main.postDelayed(disarmRunnable, 3000)
        } else {
            main.removeCallbacks(disarmRunnable)
            deleteArmed = false
            segments.removeAt(segments.lastIndex).file.delete()
            Toast.makeText(this, R.string.segment_deleted, Toast.LENGTH_SHORT).show()
            if (inReview) {
                if (segments.isEmpty()) exitReview()
                else player?.let { it.removeMediaItem(segments.size); it.seekTo(0, 0); it.play() }
            }
            refreshUi()
        }
    }

    private val disarmRunnable = Runnable { disarmDelete() }

    private fun disarmDelete() {
        if (!deleteArmed) return
        main.removeCallbacks(disarmRunnable)
        deleteArmed = false
        refreshUi()
    }

    private fun onFinishPressed() {
        if (activeRecording != null) return
        disarmDelete()
        if (segments.isEmpty()) {
            Toast.makeText(this, R.string.no_segments, Toast.LENGTH_SHORT).show(); return
        }
        enterReview()
    }

    // ---------------------------------------------------------------- Overlays

    private fun addImageOverlay(uri: Uri) {
        try {
            // Größe ermitteln, dann auf max. 1280 px Kante herunterrechnen
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1280) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IllegalStateException("Bild konnte nicht gelesen werden")
            val overlay = ImageOverlay(ImageOverlay.newId(), bmp, cx = 0.5f, cy = 0.5f, widthFrac = 0.45f)
            overlayStore.add(overlay)
            binding.removeOverlayButton.visibility = android.view.View.VISIBLE
            binding.gestureView.invalidate()
            Toast.makeText(this, R.string.overlay_hint, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Overlay laden fehlgeschlagen", e)
            Toast.makeText(this, getString(R.string.error, e.message ?: "Bild"), Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- Review

    private fun enterReview() {
        inReview = true
        cameraProvider?.unbindAll()          // Kamera freigeben, spart Akku und Decoder
        binding.review.root.visibility = android.view.View.VISIBLE
        binding.previewView.visibility = android.view.View.INVISIBLE
        binding.gestureView.visibility = android.view.View.GONE
        binding.review.playIcon.visibility = android.view.View.GONE
        buildPlayer()
        main.post(playbackTicker)
    }

    private fun buildPlayer() {
        player?.release()
        val p = ExoPlayer.Builder(this).build()
        p.setMediaItems(segments.map { MediaItem.fromUri(Uri.fromFile(it.file)) })
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.prepare()
        p.playWhenReady = true
        binding.review.playerView.player = p
        player = p
    }

    private fun exitReview() {
        inReview = false
        main.removeCallbacks(playbackTicker)
        player?.release(); player = null
        binding.review.playerView.player = null
        binding.review.root.visibility = android.view.View.GONE
        binding.previewView.visibility = android.view.View.VISIBLE
        binding.gestureView.visibility = android.view.View.VISIBLE
        disarmDelete()
        bindCamera()
        refreshUi()
    }

    private fun togglePlayback() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause(); binding.review.playIcon.visibility = android.view.View.VISIBLE
        } else {
            p.play(); binding.review.playIcon.visibility = android.view.View.GONE
        }
    }

    /** Gesamtposition = Dauer aller vorherigen Segmente + Position im aktuellen. */
    private fun updateReviewPosition() {
        val p = player ?: return
        val idx = p.currentMediaItemIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0))
        val before = segments.take(idx).sumOf { it.durationMs }
        val pos = before + p.currentPosition.coerceAtLeast(0)
        val total = segments.sumOf { it.durationMs }
        binding.review.reviewBar.updatePlayback(segments.map { it.durationMs }, pos, deleteArmed)
        if (!deleteArmed) {
            binding.review.reviewStatus.text = getString(R.string.review_position, fmt(pos), fmt(total), segments.size)
        }
    }

    private fun showExportDialog() {
        if (segments.isEmpty()) return
        disarmDelete()
        player?.pause()
        val info = VideoConcat.inspect(segments.first().file)
        val recordedHeight = if (info.rotation == 90 || info.rotation == 270) info.width else info.height
        val options = mutableListOf<Pair<String, Int?>>(getString(R.string.export_original) to null)
        listOf(2160 to "4K (2160p)", 1440 to "2K (1440p)", 1080 to "1080p", 720 to "720p", 480 to "480p")
            .filter { it.first < recordedHeight }
            .forEach { options.add(it.second to it.first) }

        var chosen = 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setSingleChoiceItems(options.map { it.first }.toTypedArray(), 0) { _, w -> chosen = w }
            .setPositiveButton(R.string.save) { _, _ -> runExport(options[chosen].second) }
            .setNegativeButton(R.string.cancel) { _, _ -> player?.play() }
            .setOnCancelListener { player?.play() }
            .show()
    }

    private fun runExport(targetHeight: Int?) {
        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setMessage(getString(R.string.export_running, 0))
            .setCancelable(false)
            .show()
        setControlsEnabled(false)

        val ex = Exporter(this)
        exporter = ex
        ex.export(segments.map { it.file }, targetHeight, object : Exporter.Listener {
            override fun onProgress(percent: Int) {
                dialog.setMessage(getString(R.string.export_running, percent))
            }
            override fun onDone(uri: Uri) {
                dialog.dismiss()
                ex.release(); exporter = null
                segments.forEach { it.file.delete() }
                segments.clear()
                setControlsEnabled(true)
                if (inReview) exitReview() else refreshUi()
                Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_LONG).show()
            }
            override fun onError(message: String) {
                dialog.dismiss()
                ex.release(); exporter = null
                setControlsEnabled(true)
                player?.play()
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(getString(R.string.error, ""))
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        })
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.discard_title)
            .setMessage(getString(R.string.discard_msg, segments.size))
            .setPositiveButton(R.string.discard) { _, _ ->
                activeRecording?.stop(); activeRecording = null
                segments.forEach { it.file.delete() }
                segments.clear()
                refreshUi()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- UI

    private fun setControlsEnabled(enabled: Boolean) {
        listOf(binding.recordButton, binding.flipButton, binding.qualityButton,
            binding.deleteButton, binding.finishButton).forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.4f
        }
    }

    private fun refreshUi() {
        if (inReview) return
        val recording = activeRecording != null
        binding.recordButton.isSelected = recording
        binding.recordButton.contentDescription = getString(if (recording) R.string.stop else R.string.record)

        val total = segments.sumOf { it.durationMs } + liveDurationMs
        binding.statusText.text = when {
            recording -> getString(R.string.recording, fmt(total))
            segments.isEmpty() -> getString(R.string.ready)
            else -> getString(R.string.paused, segments.size, fmt(total))
        }
        binding.segmentBar.update(segments.map { it.durationMs }, liveDurationMs, deleteArmed)

        // Während der Aufnahme sind Auflösung, Löschen und Fertig gesperrt (Kamera-Wechsel nicht).
        listOf(binding.qualityButton, binding.deleteButton, binding.finishButton).forEach {
            it.alpha = if (recording) 0.35f else 1f
        }
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.GERMANY, "%d:%02d", s / 60, s % 60)
    }

    private fun label(q: Quality) = when (q) {
        Quality.UHD -> "4K"
        Quality.FHD -> "1080p"
        Quality.HD -> "720p"
        Quality.SD -> "480p"
        else -> "?"
    }

    override fun onStop() {
        super.onStop()
        // Laufende Aufnahme beim Verlassen beenden; das Segment bleibt erhalten.
        activeRecording?.stop()
        activeRecording = null
        player?.pause()
    }

    override fun onStart() {
        super.onStart()
        if (inReview) { player?.play(); binding.review.playIcon.visibility = android.view.View.GONE }
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(playbackTicker)
        player?.release(); player = null
        exporter?.release()
        compositor.release()
    }

    companion object {
        private const val TAG = "VideoEditor"
        private val QUALITY_ORDER = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
    }
}
