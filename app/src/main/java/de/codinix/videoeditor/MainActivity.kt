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
import de.codinix.videoeditor.overlay.TextOverlay
import de.codinix.videoeditor.overlay.TextRenderer
import de.codinix.videoeditor.overlay.Overlay
import de.codinix.videoeditor.overlay.OverlayStore
import de.codinix.videoeditor.overlay.VideoOverlay
import androidx.media3.common.VideoSize
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
    private val drafts by lazy { DraftStore(this) }

    // Render-Pipeline und Overlays
    private val overlayStore = OverlayStore()
    private lateinit var compositor: CompositorProcessor
    private lateinit var compositorEffect: CompositorEffect

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addImageOverlay(uri)
    }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addVideoOverlay(uri)
    }

    /** Player für das Video-Overlay; läuft nur während der Aufnahme, immer stumm. */
    private var overlayPlayer: ExoPlayer? = null
    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /**
     * Tonspuren bereits entfernter Video-Overlays. Ihr Bild steckt in den Segmenten,
     * ihr Ton muss beim Export weiter gemischt werden – von Einfügen bis Entfernen.
     */
    data class AudioTrackEntry(
        val file: File, val startOffsetMs: Long, val endOffsetMs: Long, val volume: Float, val durationMs: Long,
        val timeline: List<OverlayAudioRenderer.Segment> = emptyList()
    )

    private fun VideoOverlay.rendererTimeline() = timeline().map { OverlayAudioRenderer.Segment(it.atMs, it.gain, it.playing, it.seekMs) }
    private val audioHistory = mutableListOf<AudioTrackEntry>()

    private fun currentTotalMs() = segments.sumOf { it.durationMs } + liveDurationMs

    /** Alle Tonspuren für Export und Review: Historie plus aktuelles Overlay. */
    private fun allAudioMixes(): List<Exporter.AudioMix> {
        val past = audioHistory.map {
            Exporter.AudioMix(it.file, it.startOffsetMs, it.durationMs, it.volume,
                timeline = it.timeline.takeIf { t -> t.isNotEmpty() }, endOffsetMs = it.endOffsetMs)
        }
        val current = overlayStore.videoOverlay()?.takeIf { it.soundOn && it.durationMs > 0 }
            ?.let { listOf(Exporter.AudioMix(it.file, it.startOffsetMs, it.durationMs, it.volume, timeline = it.rendererTimeline())) }
            ?: emptyList()
        val tiles = tileVideos().filter { it.soundOn && it.durationMs > 0 }
            .map { Exporter.AudioMix(it.file, it.startOffsetMs, it.durationMs, it.volume, timeline = it.rendererTimeline()) }
        return past + current + tiles
    }

    /** Nach dem Löschen von Segmenten: Spuren hinter dem neuen Ende verwerfen, Rest kürzen. */
    private fun trimAudioHistory(totalMs: Long) {
        val it = audioHistory.listIterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.startOffsetMs >= totalMs) { it.remove(); if (!isFileReferenced(e.file)) e.file.delete() }
            else if (e.endOffsetMs > totalMs) it.set(e.copy(endOffsetMs = totalMs, timeline = e.timeline.filter { t -> t.fromMs <= totalMs }))
        }
        overlayStore.videoOverlay()?.trimEvents(totalMs)
        tileVideos().forEach { it.trimEvents(totalMs) }
    }

    private fun isFileReferenced(f: File): Boolean =
        audioHistory.any { it.file == f } || (overlayStore.videoOverlay()?.file == f) || tileVideos().any { it.file == f }

    // ---- Freistellung ----
    private val greenscreenActive: Boolean get() = overlayStore.videoOverlay()?.isBackground == true

    // ---- Mosaik ----
    private var mosaic = de.codinix.videoeditor.overlay.Mosaic(de.codinix.videoeditor.overlay.Mosaic.LAYOUT_NONE)
    private val mosaicActive: Boolean get() = mosaic.layout != de.codinix.videoeditor.overlay.Mosaic.LAYOUT_NONE
    private val pickTileImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) setTileImage(uri)
    }
    private val pickTileVideo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) setTileVideo(uri)
    }
    /** Player der Kachelvideos, per Video-ID. */
    private val tilePlayers = HashMap<Long, ExoPlayer>()
    private fun tileVideos() = if (mosaicActive) mosaic.videos() else emptyList()
    /** Alle Videos, die während der Aufnahme laufen (Overlay/Hintergrund + Kacheln). */
    private fun anyLiveVideo(): Boolean = overlayStore.videoOverlay() != null || tileVideos().isNotEmpty()

    // ---- Untertitel ----
    private val captions = mutableListOf<de.codinix.videoeditor.whisper.Caption>()
    private val modelManager by lazy { de.codinix.videoeditor.whisper.ModelManager(this) }
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var transcribing = false
    private val whisperExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var captionSettings = de.codinix.videoeditor.whisper.CaptionSettings()

    /** Zuletzt benutzte Untertitel-Einstellungen als Standard für neue Videos. */
    private fun loadDefaultCaptionSettings(): de.codinix.videoeditor.whisper.CaptionSettings {
        val json = prefs.getString("captions_settings", null) ?: return de.codinix.videoeditor.whisper.CaptionSettings()
        return try { de.codinix.videoeditor.whisper.CaptionSettings.fromJson(org.json.JSONObject(json)) }
        catch (e: Exception) { de.codinix.videoeditor.whisper.CaptionSettings() }
    }
    private fun saveDefaultCaptionSettings() {
        prefs.edit().putString("captions_settings", captionSettings.toJson().toString()).apply()
    }
    private val captionModel: de.codinix.videoeditor.whisper.ModelManager.Model
        get() = de.codinix.videoeditor.whisper.ModelManager.Model.values()
            .getOrElse(prefs.getInt("captions_model", 1)) { de.codinix.videoeditor.whisper.ModelManager.Model.BASE }
    private val pickBackground = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addVideoOverlay(uri, asBackground = true)
    }

    /** Overlay-Ton in der Vorschau hörbar? Bewusster Schalter, standardmäßig aus. */
    private var previewSoundOn = false
    /** Mikrofon-Verstärkung für den Export (1.0 = unverändert). */
    private var micGain = 1f

    /** Zweiter Player in der Review: nur der Ton des Overlay-Videos, synchron zur Aufnahme. */
    private var reviewOverlayPlayer: ExoPlayer? = null

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
        // Kamera-App: Bildschirm bleibt an, sonst schaltet Android bei längeren Aufnahmen ab
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        CrashLog.install(applicationContext)
        showCrashReportIfAny()
        recoverSessionIfAny()
        prefetchCaptionModel()
        captionSettings = loadDefaultCaptionSettings()
        cacheDir.listFiles()?.filter { it.name.startsWith("overlay_video_") || it.name.startsWith("export_") }
            ?.forEach { it.delete() }

        compositor = CompositorProcessor(overlayStore)
        compositorEffect = CompositorEffect(compositor)
        compositor.onFrameAspectChanged = { aspect -> main.post {
            binding.gestureView.frameAspect = aspect
            de.codinix.videoeditor.overlay.CameraOverlay.cameraAspect = 1f / aspect   // H/B
            overlayStore.publish(); binding.gestureView.invalidate()
        } }

        binding.gestureView.store = overlayStore
        binding.gestureView.onSelectionChanged = { sel -> updateOverlayButtons(sel) }
        binding.addVideoButton.setOnClickListener {
            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.soundButton.setOnClickListener {
            val bg = overlayStore.videoOverlay()?.takeIf { it.isBackground }
            val sel = overlayStore.selected() as? VideoOverlay
            when {
                sel != null && !sel.isBackground -> showVolumeDialog(sel)
                bg != null -> showTileVolumeDialog(bg)
            }
        }
        binding.micButton.setOnClickListener { showMicDialog() }
        binding.addTextButton.setOnClickListener { showTextDialog(null) }
        binding.greenscreenButton.setOnClickListener { onGreenscreenPressed() }
        binding.mosaicButton.setOnClickListener { showMosaicDialog() }
        binding.filterButton.setOnClickListener { showFilterDialog() }
        compositor.colorFilter = prefs.getInt("color_filter", 0)
        updateFilterButton()
        binding.tileMediaButton.setOnClickListener {
            if (mosaic.selected >= 0) pickTileImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.tileCameraButton.setOnClickListener {
            mosaic.tiles.getOrNull(mosaic.selected)?.let { t ->
                releaseTileVideo(t)
                t.kind = de.codinix.videoeditor.overlay.Mosaic.KIND_CAMERA; t.bitmap = null; t.zoom = 1f; t.offX = 0f; t.offY = 0f
                publishMosaic(); updateTileButtons()
            }
        }
        binding.tileFillButton.setOnClickListener {
            mosaic.tiles.getOrNull(mosaic.selected)?.let { t -> showTileFillDialog(t) }
        }
        binding.tileVideoButton.setOnClickListener {
            if (mosaic.selected >= 0) pickTileVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.tileSoundButton.setOnClickListener {
            mosaic.tiles.getOrNull(mosaic.selected)?.video?.let { showVolumeDialog(it) }
        }
        binding.gestureView.mosaic = mosaic
        binding.gestureView.onMosaicChanged = { publishMosaic() }
        binding.gestureView.onMosaicTileSelected = { updateTileButtons() }
        binding.shapeButton.setOnClickListener {
            (overlayStore.selected() as? de.codinix.videoeditor.overlay.CameraOverlay)?.let { c ->
                c.shape = (c.shape + 1) % 3
                overlayStore.publish(); binding.gestureView.invalidate(); updateOverlayButtons(c); persistSession()
                Toast.makeText(this, when (c.shape) {
                    de.codinix.videoeditor.overlay.CameraOverlay.SHAPE_SQUARE -> R.string.shape_square
                    de.codinix.videoeditor.overlay.CameraOverlay.SHAPE_PORTRAIT -> R.string.shape_portrait
                    else -> R.string.shape_circle }, Toast.LENGTH_SHORT).show()
            }
        }
        binding.borderButton.setOnClickListener {
            (overlayStore.selected() as? de.codinix.videoeditor.overlay.CameraOverlay)?.let { c ->
                c.border = !c.border
                overlayStore.publish(); binding.gestureView.invalidate(); updateOverlayButtons(c); persistSession()
            }
        }
        binding.editTextButton.setOnClickListener {
            (overlayStore.selected() as? TextOverlay)?.let { showTextDialog(it) }
        }
        binding.previewSoundButton.setOnClickListener {
            previewSoundOn = !previewSoundOn
            binding.previewSoundButton.alpha = if (previewSoundOn) 1f else 0.5f
            binding.previewSoundButton.setBackgroundResource(
                if (previewSoundOn) R.drawable.bg_round_button_accent else R.drawable.bg_round_button)
            overlayStore.videoOverlay()?.let { o -> overlayPlayer?.volume = previewVolume(o) }
            tileVideos().forEach { v -> tilePlayers[v.id]?.volume = previewVolume(v) }
            Toast.makeText(this, if (previewSoundOn) R.string.preview_sound_on else R.string.preview_sound_off,
                Toast.LENGTH_LONG).show()
        }
        binding.addImageButton.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.removeOverlayButton.setOnClickListener {
            overlayStore.selectedId?.let { removeOverlay(it) }
            updateOverlayButtons(null)
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
        binding.review.saveDraftButton.setOnClickListener { saveDraft() }
        binding.review.captionsButton.setOnClickListener { showCaptionsDialog() }
        binding.review.captionsEditButton.setOnClickListener { showCaptionEditor(-1) }
        binding.review.scrubBar.onScrubStart = { player?.pause(); reviewOverlayPlayer?.pause() }
        binding.review.scrubBar.onScrub = { ms -> seekReviewTo(ms, play = false) }
        binding.review.scrubBar.onScrubEnd = { ms -> seekReviewTo(ms, play = true) }
        binding.review.scrubBar.onReorder = { from, to -> reorderSegments(from, to) }
        binding.review.scrubBar.onDelete = { idx -> confirmDeleteSegment(idx) }
        binding.draftsButton.setOnClickListener { showDrafts() }
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

        cameraInfo?.let {
            compositor.sensorRotationDegrees = it.getSensorRotationDegrees(android.view.Surface.ROTATION_0)
        }
        compositor.frontFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
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
            compositor.backgroundOverlayId = overlayStore.videoOverlay()?.takeIf { it.isBackground }?.id ?: 0L
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
        if (activeRecording == null) {
            if (currentTotalMs() >= MAX_TOTAL_MS) {
                Toast.makeText(this, R.string.limit_reached, Toast.LENGTH_LONG).show(); return
            }
            checkFreeSpace()
        }

        activeRecording?.let {
            it.stop()          // Finalize-Event legt das Segment an
            activeRecording = null
            return
        }

        val file = File(segmentDir, "seg_${System.currentTimeMillis()}.mp4")
        val pending = capture.output.prepareRecording(this,
            FileOutputOptions.Builder(file).setFileSizeLimit(3_500L * 1024 * 1024).build())
        val micGranted = PermissionChecker.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PermissionChecker.PERMISSION_GRANTED
        if (micGranted) pending.withAudioEnabled()

        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    liveDurationMs = 0
                    if (overlayStore.videoOverlay()?.playing != false) overlayPlayer?.play()
                    setTileVideosPlaying(true)
                    refreshUi()
                }
                is VideoRecordEvent.Status -> {
                    liveDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                    if (currentTotalMs() >= MAX_TOTAL_MS) {
                        // Limit erreicht: Aufnahme stoppt von selbst
                        activeRecording?.stop(); activeRecording = null
                        Toast.makeText(this, R.string.limit_reached, Toast.LENGTH_LONG).show()
                    }
                    refreshUi()
                }
                is VideoRecordEvent.Finalize -> onSegmentFinalized(event, file)
            }
        }
    }

    /** Warnt, wenn der freie Speicher für die restliche mögliche Aufnahme knapp wird. */
    private fun checkFreeSpace() {
        try {
            val stat = android.os.StatFs(cacheDir.absolutePath)
            val freeMb = stat.availableBytes / (1024 * 1024)
            val perMinMb = if (preferredQuality == Quality.UHD || (preferredQuality == null && supportedQualities.firstOrNull() == Quality.UHD)) 600 else 150
            val remainingMin = ((MAX_TOTAL_MS - currentTotalMs()) / 60000.0).coerceAtLeast(0.5)
            val neededMb = (perMinMb * remainingMin * 2).toLong()   // Aufnahme + Export
            if (freeMb < neededMb) Toast.makeText(this, getString(R.string.low_space, freeMb / 1024.0), Toast.LENGTH_LONG).show()
        } catch (_: Exception) { }
    }

    private fun onSegmentFinalized(event: VideoRecordEvent.Finalize, file: File) {
        val hitSizeLimit = event.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
        activeRecording = null
        overlayPlayer?.pause()
        setTileVideosPlaying(false)
        main.post { persistSession() }
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
        syncOverlayPlayer()
        refreshUi()
    }

    // ---------------------------------------------------------------- Löschen / Fertig

    private fun onDeletePressed() {
        if (activeRecording != null) {
            if (anyLiveVideo()) toggleOverlayPlayPause()
            return
        }
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
            trimAudioHistory(currentTotalMs())
            trimCaptions(currentTotalMs())
            persistSession()
            syncOverlayPlayer(afterDelete = true)
            Toast.makeText(this, R.string.segment_deleted, Toast.LENGTH_SHORT).show()
            if (inReview) {
                if (segments.isEmpty()) exitReview()
                else { player?.let { it.removeMediaItem(segments.size); it.seekTo(0, 0); it.play() }; buildReviewOverlayPlayer() }
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
            val bmp = loadBitmap(uri, 1280)
            val overlay = ImageOverlay(Overlay.newId(), bmp, cx = 0.5f, cy = 0.5f, widthFrac = 0.45f)
            overlayStore.add(overlay)
            updateOverlayButtons(overlay)
            binding.gestureView.invalidate()
            Toast.makeText(this, R.string.overlay_hint, Toast.LENGTH_SHORT).show()
            persistSession()
        } catch (e: Exception) {
            Log.e(TAG, "Overlay laden fehlgeschlagen", e)
            Toast.makeText(this, getString(R.string.error, e.message ?: "Bild"), Toast.LENGTH_LONG).show()
        }
    }

    /** Text-Overlay anlegen (existing == null) oder bearbeiten. */
    private fun showTextDialog(existing: TextOverlay?) {
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()

        var textRgb = (existing?.colorArgb ?: android.graphics.Color.WHITE) or 0xFF000000.toInt()
        var textAlpha = existing?.let { android.graphics.Color.alpha(it.colorArgb) } ?: 255
        var bgOn = existing?.bgColorArgb != null
        var bgRgb = (existing?.bgColorArgb ?: android.graphics.Color.BLACK) or 0xFF000000.toInt()
        var bgAlpha = existing?.bgColorArgb?.let { android.graphics.Color.alpha(it) } ?: 200

        fun textColor() = (textRgb and 0x00FFFFFF) or (textAlpha shl 24)
        fun bgColor(): Int? = if (bgOn) (bgRgb and 0x00FFFFFF) or (bgAlpha shl 24) else null

        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.text_hint)
            setText(existing?.text ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2; maxLines = 5
            gravity = android.view.Gravity.CENTER
            textSize = 22f
            setPadding(pad, pad, pad, pad)
        }
        val previewBg = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 12 * dp }
        fun refreshPreview() {
            input.setTextColor(textColor())
            input.setHintTextColor(0x88888888.toInt())
            val bg = bgColor()
            previewBg.setColor(bg ?: 0x22888888)
            input.background = previewBg
        }

        fun label(res: Int) = android.widget.TextView(this).apply {
            text = getString(res); textSize = 13f; setPadding(0, pad, 0, pad / 4)
        }

        /** Zwei Reihen Farbfelder; onPick liefert die RGB-Farbe. */
        fun hideKeyboard() {
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(input.windowToken, 0)
        }
        fun swatchGrid(selected: () -> Int, onPick: (Int) -> Unit): android.view.View {
            val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            val views = mutableListOf<Pair<Int, android.view.View>>()
            fun refresh() { views.forEach { (c, v) -> v.scaleX = if (c == (selected() or 0xFF000000.toInt())) 1.2f else 1f; v.scaleY = v.scaleX } }
            TextRenderer.COLORS.forEach { c ->
                val size = (34 * dp).toInt()
                val v = android.view.View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply { setMargins(pad / 3, pad / 4, pad / 3, pad / 4) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(c); setStroke(3, 0xFF888888.toInt())
                    }
                    setOnClickListener { hideKeyboard(); onPick(c); refresh(); refreshPreview() }
                }
                views.add(c to v); row.addView(v)
            }
            refresh()
            return android.widget.HorizontalScrollView(this).apply { addView(row); isHorizontalScrollBarEnabled = false }
        }

        fun slider(initial: Int, onChange: (Int) -> Unit) = android.widget.SeekBar(this).apply {
            max = 100; progress = initial
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar, v: Int, fromUser: Boolean) { onChange(v); refreshPreview() }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar) { hideKeyboard() }
                override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
            })
        }

        val bgSection = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            visibility = if (bgOn) android.view.View.VISIBLE else android.view.View.GONE
            addView(label(R.string.bg_color))
            addView(swatchGrid({ bgRgb }) { bgRgb = it })
            addView(label(R.string.bg_opacity))
            addView(slider(bgAlpha * 100 / 255) { bgAlpha = it * 255 / 100 })
        }
        val bgSwitch = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
            text = getString(R.string.text_background)
            isChecked = bgOn
            setPadding(0, pad, 0, 0)
            setOnCheckedChangeListener { _, on ->
                bgOn = on
                bgSection.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
                refreshPreview()
            }
        }

        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
            addView(label(R.string.text_color))
            addView(swatchGrid({ textRgb }) { textRgb = it })
            addView(label(R.string.text_opacity))
            addView(slider(textAlpha * 100 / 255) { textAlpha = it * 255 / 100 })
            addView(bgSwitch)
            addView(bgSection)
        }
        refreshPreview()
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE or android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { hideKeyboard(); true } else false
        }

        val textDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.text_dialog_title)
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                if (existing == null) {
                    val o = TextOverlay(Overlay.newId(), text, textColor(), bgColor(), widthFrac = 0.6f)
                    overlayStore.add(o)
                    updateOverlayButtons(o)
                } else {
                    existing.text = text; existing.colorArgb = textColor(); existing.bgColorArgb = bgColor()
                    existing.rerender()
                    overlayStore.publish()
                    updateOverlayButtons(existing)
                }
                binding.gestureView.invalidate()
                persistSession()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        // Bei offener Tastatur schrumpft der Dialog und bleibt scrollbar, statt nach oben zu rutschen
        textDialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        textDialog.show()
        input.requestFocus()
    }

    // ---------------------------------------------------------------- Farbfilter

    private fun updateFilterButton() {
        binding.filterButton.setBackgroundResource(if (compositor.colorFilter != 0) R.drawable.bg_round_button_accent else R.drawable.bg_round_button)
    }

    /** Filter-Auswahl als Vorschaukarten; wirkt sofort in Vorschau und Aufnahme (auch mitten im Segment). */
    private fun showFilterDialog() {
        val dp = resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        val cardW = (104 * dp).toInt(); val cardH = (74 * dp).toInt()
        val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(pad, pad, pad, 0) }
        lateinit var dlg: AlertDialog
        fun render() {
            row.removeAllViews()
            de.codinix.videoeditor.gl.ColorFilters.NAMES.forEachIndexed { idx, name ->
                val img = android.widget.ImageView(this).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(de.codinix.videoeditor.gl.ColorFilters.preview(idx, 96, 68))
                    clipToOutline = true
                    background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * dp }
                }
                val label = android.widget.TextView(this).apply { text = name; textSize = 11f; gravity = android.view.Gravity.CENTER; setPadding(0, pad / 3, 0, 0) }
                val card = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(cardW, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(pad / 3, 0, pad / 3, 0) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 10 * dp; setColor(0xFF2A2A34.toInt())
                        setStroke((2.5f * dp).toInt(), if (idx == compositor.colorFilter) 0xFFFF3B4E.toInt() else 0x00000000)
                    }
                    setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
                    addView(img, android.widget.LinearLayout.LayoutParams(cardW - pad, cardH))
                    addView(label)
                    setOnClickListener {
                        compositor.colorFilter = idx
                        prefs.edit().putInt("color_filter", idx).apply()
                        updateFilterButton(); persistSession(); render()
                    }
                }
                row.addView(card)
            }
        }
        render()
        dlg = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filter)
            .setView(android.widget.HorizontalScrollView(this).apply { addView(row); isHorizontalScrollBarEnabled = false })
            .setPositiveButton(R.string.ok, null)
            .create()
        dlg.show()
    }

    // ---------------------------------------------------------------- Mosaik

    private fun publishMosaic() {
        compositor.mosaic = if (mosaicActive) mosaic.snapshot() else null
        binding.gestureView.invalidate()
        persistSession()
    }

    private fun updateTileButtons() {
        val show = mosaicActive && mosaic.selected >= 0
        if (show) {
            // Overlay-Knopfspalte ausblenden, solange eine Kachel ausgewählt ist
            listOf(binding.removeOverlayButton, binding.soundButton, binding.editTextButton, binding.shapeButton, binding.borderButton)
                .forEach { it.visibility = android.view.View.GONE }
        } else if (!mosaicActive || mosaic.selected < 0) {
            updateOverlayButtons(overlayStore.selected())
        }
        val v = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.tileMediaButton.visibility = v
        binding.tileCameraButton.visibility = v
        binding.tileFillButton.visibility = v
        binding.tileVideoButton.visibility = v
        binding.tileSoundButton.visibility = if (show && mosaic.tiles[mosaic.selected].kind == de.codinix.videoeditor.overlay.Mosaic.KIND_VIDEO)
            android.view.View.VISIBLE else android.view.View.GONE
        if (show) {
            val t = mosaic.tiles[mosaic.selected]
            binding.tileCameraButton.alpha = if (t.kind == de.codinix.videoeditor.overlay.Mosaic.KIND_CAMERA) 0.4f else 1f
        }
        binding.mosaicButton.setBackgroundResource(if (mosaicActive) R.drawable.bg_round_button_accent else R.drawable.bg_round_button)
    }

    private var mosaicPopup: android.widget.PopupWindow? = null

    /** TikTok-artiges Panel neben dem Knopf: „Aus“ + fünf Raster-Symbole, aktives weiß hinterlegt. */
    private fun showMosaicDialog() {
        if (activeRecording != null) return
        if (greenscreenActive || overlayStore.cameraOverlay() != null) { Toast.makeText(this, R.string.mosaic_conflict, Toast.LENGTH_SHORT).show(); return }
        mosaicPopup?.dismiss()
        val dp = resources.displayMetrics.density
        val pad = (8 * dp).toInt()
        val col = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            background = getDrawable(R.drawable.bg_panel)
            setPadding(pad, pad, pad, pad)
        }
        lateinit var popup: android.widget.PopupWindow
        val icons = listOf(0, R.drawable.ic_layout_2rows, R.drawable.ic_layout_2cols, R.drawable.ic_layout_3rows, R.drawable.ic_layout_3cols, R.drawable.ic_layout_2x2)
        fun pick(layout: Int) {
            mosaic.changeLayout(layout)
            if (!mosaicActive) mosaic.selected = -1
            publishMosaic(); updateTileButtons()
            if (mosaicActive) showTip(getString(R.string.mosaic_tip))
            popup.dismiss()
        }
        icons.forEachIndexed { idx, res ->
            val selected = idx == mosaic.layout
            val item: android.view.View = if (idx == 0) android.widget.TextView(this).apply {
                text = "Aus"; textSize = 15f; setTextColor(if (selected) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
            } else android.widget.ImageView(this).apply {
                setImageResource(res)
                if (selected) setColorFilter(0xFF000000.toInt())
            }
            item.layoutParams = android.widget.LinearLayout.LayoutParams((56 * dp).toInt(), (48 * dp).toInt()).apply { setMargins(0, pad / 2, 0, pad / 2) }
            item.setPadding(pad, pad, pad, pad)
            if (selected) item.background = getDrawable(R.drawable.bg_panel_selected)
            item.setOnClickListener { pick(idx) }
            col.addView(item)
        }
        // Trennlinien-Optionen
        col.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_border)
            alpha = if (mosaic.rainbowGaps || mosaic.gapWhite) 1f else 0.6f
            layoutParams = android.widget.LinearLayout.LayoutParams((56 * dp).toInt(), (44 * dp).toInt()).apply { setMargins(0, pad, 0, 0) }
            setPadding(pad + 4, pad, pad + 4, pad)
            setOnClickListener { popup.dismiss(); showMosaicLineOptions() }
        })
        popup = android.widget.PopupWindow(col, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 8 * dp
            isOutsideTouchable = true
        }
        mosaicPopup = popup
        // Links neben dem Knopf, vertikal am Knopf ausgerichtet
        val anchor = binding.mosaicButton
        col.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
        popup.showAsDropDown(anchor, -(col.measuredWidth + (8 * dp).toInt()), -(anchor.height + col.measuredHeight / 2 - anchor.height / 2).coerceAtLeast(0) * 0 - anchor.height, android.view.Gravity.START)
    }

    private fun showMosaicLineOptions() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val gapWhite = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
            text = getString(R.string.mosaic_gap_white); isChecked = mosaic.gapWhite
        }
        val rainbow = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
            text = getString(R.string.mosaic_rainbow); isChecked = mosaic.rainbowGaps; setPadding(0, pad / 2, 0, 0)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0)
            addView(gapWhite); addView(rainbow)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mosaic_title)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                mosaic.gapWhite = gapWhite.isChecked; mosaic.rainbowGaps = rainbow.isChecked
                publishMosaic()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setTileImage(uri: Uri) {
        val idx = mosaic.selected
        if (idx < 0) return
        try {
            val bmp = loadBitmap(uri, 1920)
            val t = mosaic.tiles[idx]
            releaseTileVideo(t)
            t.kind = de.codinix.videoeditor.overlay.Mosaic.KIND_IMAGE; t.bitmap = bmp; t.zoom = 1f; t.offX = 0f; t.offY = 0f
            publishMosaic(); updateTileButtons()
        } catch (e: Exception) {
            Log.e(TAG, "Kachelbild laden fehlgeschlagen", e)
            Toast.makeText(this, getString(R.string.error, e.message ?: "Bild"), Toast.LENGTH_LONG).show()
        }
    }

    /** Video in die ausgewählte Kachel: kopieren, ggf. auf 1080p rechnen, Player anlegen. */
    private fun setTileVideo(uri: Uri) {
        val idx = mosaic.selected
        if (idx < 0) return
        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tile_video)
            .setMessage(R.string.video_copying)
            .setCancelable(false)
            .show()
        bgExecutor.execute {
            try {
                val raw = File(cacheDir, "tile_${System.currentTimeMillis()}_raw.mp4")
                contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalStateException("Video konnte nicht gelesen werden")
                main.post {
                    if (Downscaler.needsDownscale(raw, 1080)) {
                        val dest = File(cacheDir, "tile_${System.currentTimeMillis()}.mp4")
                        dialog.setMessage(getString(R.string.tile_video_downscale, 0))
                        Downscaler.run(this, raw, dest, 1080,
                            onProgress = { p -> dialog.setMessage(getString(R.string.tile_video_downscale, p)) },
                            onDone = { out ->
                                raw.delete()
                                dialog.dismiss()
                                if (out != null) installTileVideo(idx, out)
                                else Toast.makeText(this, getString(R.string.error, "Umrechnung"), Toast.LENGTH_LONG).show()
                            })
                    } else {
                        dialog.dismiss()
                        installTileVideo(idx, raw)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Kachelvideo laden fehlgeschlagen", e)
                main.post { dialog.dismiss(); Toast.makeText(this, getString(R.string.error, e.message ?: "Video"), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun installTileVideo(idx: Int, file: File) {
        val t = mosaic.tiles.getOrNull(idx) ?: return
        releaseTileVideo(t)
        bgExecutor.execute { try { OverlayAudioRenderer.decode(file, cacheDir) } catch (e: Exception) { Log.w(TAG, "Vorab-Dekodierung", e) } }
        val total = currentTotalMs()
        val v = VideoOverlay(Overlay.newId(), file, startOffsetMs = total)
        v.addEvent(total, 1f, true)
        t.kind = de.codinix.videoeditor.overlay.Mosaic.KIND_VIDEO; t.bitmap = null; t.video = v
        t.zoom = 1f; t.offX = 0f; t.offY = 0f
        attachTileVideo(v)
        publishMosaic(); updateTileButtons()
        persistSession()
    }

    /** Player + GL-Ebene für ein Kachelvideo. */
    private fun attachTileVideo(v: VideoOverlay) {
        tilePlayers.remove(v.id)?.release()
        val p = newLeanPlayer()
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(v.file)))
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.volume = previewVolume(v)
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val rot = videoSize.unappliedRotationDegrees
                    val w = if (rot == 90 || rot == 270) videoSize.height else videoSize.width
                    val h = if (rot == 90 || rot == 270) videoSize.width else videoSize.height
                    v.videoAspect = h.toFloat() / w.toFloat()
                    publishMosaic()
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && v.durationMs <= 0) {
                    v.durationMs = p.duration.coerceAtLeast(0)
                    p.seekTo(v.sourcePositionAt(currentTotalMs()))
                }
            }
        })
        p.prepare()
        p.playWhenReady = activeRecording != null && v.playing
        tilePlayers[v.id] = p
        compositor.createVideoLayer(v.id) { surface -> main.post { tilePlayers[v.id]?.setVideoSurface(surface) } }
    }

    /** Kachelvideo entfernen: Ton bis hierher in die Historie, Player und Ebene freigeben. */
    private fun releaseTileVideo(t: de.codinix.videoeditor.overlay.Mosaic.Tile) {
        val v = t.video ?: return
        val end = currentTotalMs()
        if (v.soundOn && v.durationMs > 0 && end > v.startOffsetMs) {
            audioHistory.add(AudioTrackEntry(v.file, v.startOffsetMs, end, v.volume, v.durationMs, v.rendererTimeline()))
        }
        tilePlayers.remove(v.id)?.release()
        compositor.releaseVideoLayer(v.id)
        if (!isFileReferenced(v.file)) v.file.delete()
        t.video = null
    }

    private fun setTileVideosPlaying(playing: Boolean) {
        tileVideos().forEach { v -> tilePlayers[v.id]?.let { if (playing && v.playing) it.play() else it.pause() } }
    }

    private fun syncTilePlayers() {
        val total = currentTotalMs()
        tileVideos().forEach { v -> tilePlayers[v.id]?.let { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.seekTo(v.sourcePositionAt(total)) } }
    }

    private fun resetMosaic() {
        tilePlayers.values.forEach { it.release() }; tilePlayers.clear()
        mosaic.tiles.forEach { t -> t.video?.let { compositor.releaseVideoLayer(it.id) } }
        mosaic = de.codinix.videoeditor.overlay.Mosaic(de.codinix.videoeditor.overlay.Mosaic.LAYOUT_NONE)
        binding.tileVideoButton.setOnClickListener {
            if (mosaic.selected >= 0) pickTileVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.tileSoundButton.setOnClickListener {
            mosaic.tiles.getOrNull(mosaic.selected)?.video?.let { showVolumeDialog(it) }
        }
        binding.gestureView.mosaic = mosaic
        compositor.mosaic = null
        updateTileButtons()
    }

    private fun onGreenscreenPressed() {
        if (activeRecording != null) return
        if (mosaicActive) { Toast.makeText(this, "Erst das Mosaik ausschalten.", Toast.LENGTH_SHORT).show(); return }
        val bg = overlayStore.videoOverlay()?.takeIf { it.isBackground }
        if (bg == null) {
            pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.greenscreen_remove_title)
                .setMessage(R.string.greenscreen_remove_msg)
                .setPositiveButton(R.string.remove) { _, _ ->
                    removeOverlay(bg.id)
                    updateOverlayButtons(null)
                    applyGreenscreenState()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /** Kachel-Modus an/aus: Hintergrund-ID an den Renderer, Kamera-Kachel anlegen/entfernen, Knopf einfärben. */
    private fun applyGreenscreenState() {
        val on = greenscreenActive
        binding.greenscreenButton.setBackgroundResource(if (on) R.drawable.bg_round_button_accent else R.drawable.bg_round_button)
        compositor.backgroundOverlayId = if (on) overlayStore.videoOverlay()!!.id else 0L
        if (on && overlayStore.cameraOverlay() == null) {
            overlayStore.add(de.codinix.videoeditor.overlay.CameraOverlay(Overlay.newId()))
            overlayStore.selectedId = null
        }
        if (!on) overlayStore.cameraOverlay()?.let { overlayStore.remove(it.id) }
        binding.gestureView.invalidate()
    }

    private fun updateOverlayButtons(sel: Overlay?) {
        if (mosaicActive && mosaic.selected >= 0) return   // Kachel hat Vorrang, siehe updateTileButtons
        binding.removeOverlayButton.visibility = if (sel != null) android.view.View.VISIBLE else android.view.View.GONE
        binding.editTextButton.visibility = if (sel is TextOverlay) android.view.View.VISIBLE else android.view.View.GONE
        val cam = sel as? de.codinix.videoeditor.overlay.CameraOverlay
        binding.shapeButton.visibility = if (cam != null) android.view.View.VISIBLE else android.view.View.GONE
        binding.borderButton.visibility = if (cam != null) android.view.View.VISIBLE else android.view.View.GONE
        cam?.let { binding.borderButton.alpha = if (it.border) 1f else 0.5f }
        // Die Kachel selbst hat kein X – sie geht nur mit dem Hintergrund
        if (cam != null) binding.removeOverlayButton.visibility = android.view.View.GONE
        // Hintergrundvideo ist nicht anwählbar – sein Lautstärke-Knopf ist immer sichtbar
        val video = (sel as? VideoOverlay) ?: overlayStore.videoOverlay()?.takeIf { it.isBackground }
        binding.soundButton.visibility = if (video != null) android.view.View.VISIBLE else android.view.View.GONE
        video?.let {
            binding.soundButton.setImageResource(if (it.soundOn) R.drawable.ic_overlay_volume else R.drawable.ic_overlay_volume_off)
            binding.soundButton.contentDescription = getString(if (it.soundOn) R.string.sound_on else R.string.sound_off)
        }
    }

    private fun removeOverlay(id: Long) {
        val o = overlayStore.items.firstOrNull { it.id == id }
        if (o is VideoOverlay) {
            val end = currentTotalMs()
            if (o.soundOn && o.durationMs > 0 && end > o.startOffsetMs) {
                audioHistory.add(AudioTrackEntry(o.file, o.startOffsetMs, end, o.volume, o.durationMs, o.rendererTimeline()))
            }
        }
        overlayStore.remove(id)
        if (o is VideoOverlay) {
            overlayPlayer?.release(); overlayPlayer = null
            compositor.releaseVideoLayer(o.id)
            if (!isFileReferenced(o.file)) o.file.delete()
        }
        persistSession()
    }

    private fun clearOverlays() {
        val hadBg = greenscreenActive
        overlayStore.items.map { it.id }.forEach { removeOverlay(it) }
        overlayStore.clear()
        if (hadBg) { compositor.backgroundOverlayId = 0L
            binding.greenscreenButton.setBackgroundResource(R.drawable.bg_round_button) }
        updateOverlayButtons(null)
    }

    private fun addVideoOverlay(uri: Uri, asBackground: Boolean = false) {
        if (asBackground && mosaicActive) { Toast.makeText(this, "Erst das Mosaik ausschalten.", Toast.LENGTH_SHORT).show(); return }
        if (overlayStore.videoOverlay() != null) {
            Toast.makeText(this, if (asBackground) R.string.greenscreen_conflict else R.string.only_one_video, Toast.LENGTH_LONG).show(); return
        }
        Toast.makeText(this, R.string.video_copying, Toast.LENGTH_SHORT).show()
        val dest = File(cacheDir, "overlay_video_${System.currentTimeMillis()}.mp4")
        bgExecutor.execute {
            try {
                contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalStateException("Video konnte nicht gelesen werden")
                main.post {
                    // Einfügezeitpunkt = fertige Segmente + bereits laufende Aufnahme
                    val total = segments.sumOf { it.durationMs } + liveDurationMs
                    // Ton im Hintergrund vorab dekodieren, damit Review und Export nicht warten müssen
                    bgExecutor.execute { try { OverlayAudioRenderer.decode(dest, cacheDir) } catch (e: Exception) { Log.w(TAG, "Vorab-Dekodierung", e) } }
                    val overlay = VideoOverlay(Overlay.newId(), dest, startOffsetMs = total)
                    overlay.isBackground = asBackground
                    overlay.addEvent(total, 1f, true)
                    overlayStore.add(overlay)
                    attachVideoOverlay(overlay)
                    if (asBackground) {
                        overlayStore.selectedId = null
                        updateOverlayButtons(null)
                        applyGreenscreenState()
                        Toast.makeText(this, R.string.greenscreen_on, Toast.LENGTH_LONG).show()
                    } else {
                        updateOverlayButtons(overlay)
                        Toast.makeText(this, R.string.overlay_hint, Toast.LENGTH_SHORT).show()
                    }
                    binding.gestureView.invalidate()
                    persistSession()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video-Overlay fehlgeschlagen", e)
                dest.delete()
                main.post { Toast.makeText(this, getString(R.string.error, e.message ?: "Video"), Toast.LENGTH_LONG).show() }
            }
        }
    }

    /**
     * Player mit kleinem Puffer. Der Standard puffert bis zu 50 s Material im Speicher –
     * bei 4K über 100 MB pro Player. Mit mehreren Playern und dem Export daneben
     * führt das zu OutOfMemory. Lokale Dateien brauchen keinen großen Puffer.
     */
    private fun newLeanPlayer(): ExoPlayer {
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 4000, 500, 1000)
            .setTargetBufferBytes(6 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        return ExoPlayer.Builder(this).setLoadControl(loadControl).build()
    }

    /** Player anlegen und in die GL-Textur des Overlays rendern lassen. */
    private fun attachVideoOverlay(overlay: VideoOverlay) {
        overlayPlayer?.release()
        val p = newLeanPlayer()
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(overlay.file)))
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.volume = previewVolume(overlay)  // Nur mit Kopfhörern hörbar, sonst Mikrofon-Übersprechen
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val rot = videoSize.unappliedRotationDegrees
                    val w = if (rot == 90 || rot == 270) videoSize.height else videoSize.width
                    val h = if (rot == 90 || rot == 270) videoSize.width else videoSize.height
                    overlay.videoAspect = h.toFloat() / w.toFloat()
                    overlayStore.publish()
                    binding.gestureView.invalidate()
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && overlay.durationMs <= 0) {
                    overlay.durationMs = p.duration.coerceAtLeast(0)
                    syncOverlayPlayer()
                }
            }
        })
        p.prepare()
        p.playWhenReady = activeRecording != null
        overlayPlayer = p
        compositor.createVideoLayer(overlay.id) { surface -> main.post { overlayPlayer?.setVideoSurface(surface) } }
    }

    /** Vorschau-Lautstärke: nur wenn bewusst eingeschaltet, sonst nähme das Mikrofon den Lautsprecher auf. */
    private fun previewVolume(o: VideoOverlay): Float =
        if (previewSoundOn) Loudness.gain(o.volume).coerceIn(0f, 1f) else 0f

    private fun showMicDialog() {
        showSliderDialog(R.string.mic_volume_title, R.string.mic_hint, (micGain * 100).toInt()) { v ->
            micGain = v / 100f
        }
    }

    /** Einfacher Prozent-Regler 0–200 in einem Dialog. */
    private fun showSliderDialog(titleRes: Int, hintRes: Int, initial: Int, onChange: (Int) -> Unit) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val label = android.widget.TextView(this).apply {
            textSize = 18f
            text = getString(R.string.volume_percent, initial)
            gravity = android.view.Gravity.CENTER
        }
        val seek = android.widget.SeekBar(this).apply {
            max = 200
            progress = initial
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar, value: Int, fromUser: Boolean) {
                    label.text = getString(R.string.volume_percent, value)
                    onChange(value)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
            })
        }
        val hint = android.widget.TextView(this).apply {
            textSize = 13f
            text = getString(hintRes)
            setPadding(0, pad / 2, 0, 0)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(label); addView(seek); addView(hint)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setView(box)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /** Kachel-Modus: Hintergrundvideo und Mikrofon in einem Dialog, klar beschriftet. */
    private fun showTileVolumeDialog(bg: VideoOverlay) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        fun section(title: String, initial: Int, onChange: (Int) -> Unit): List<android.view.View> {
            val label = android.widget.TextView(this).apply {
                textSize = 15f; text = "$title: " + getString(R.string.volume_percent, initial); setPadding(0, pad / 2, 0, 0)
            }
            val seek = android.widget.SeekBar(this).apply {
                max = 200; progress = initial
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: android.widget.SeekBar, v: Int, fromUser: Boolean) {
                        label.text = "$title: " + getString(R.string.volume_percent, v); onChange(v)
                    }
                    override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                    override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
                })
            }
            return listOf(label, seek)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            section(getString(R.string.tile_bg_volume), (bg.volume * 100).toInt()) { v ->
                bg.volume = v / 100f
                if (activeRecording != null) bg.addEvent(currentTotalMs(), bg.volume, bg.playing)
                else if (bg.events.isNotEmpty()) {
                    val end = currentTotalMs()
                    if (bg.events.last().atMs >= end) bg.events[bg.events.lastIndex] = bg.events.last().copy(gain = bg.volume)
                    else bg.addEvent(end, bg.volume, bg.playing)
                }
                overlayPlayer?.volume = previewVolume(bg)
                updateOverlayButtons(overlayStore.selected())
            }.forEach { addView(it) }
            section(getString(R.string.tile_mic_volume), (micGain * 100).toInt()) { v -> micGain = v / 100f }.forEach { addView(it) }
            addView(android.widget.TextView(this@MainActivity).apply {
                textSize = 12f; text = getString(R.string.tile_volume_hint); setPadding(0, pad / 2, 0, 0)
            })
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tile_volume_title)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ -> persistSession() }
            .setOnDismissListener { persistSession() }
            .show()
    }

    private fun showVolumeDialog(o: VideoOverlay) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val label = android.widget.TextView(this).apply {
            textSize = 18f
            text = getString(R.string.volume_percent, (o.volume * 100).toInt())
            gravity = android.view.Gravity.CENTER
        }
        val seek = android.widget.SeekBar(this).apply {
            max = 200
            progress = (o.volume * 100).toInt()
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar, value: Int, fromUser: Boolean) {
                    o.volume = value / 100f
                    if (activeRecording != null) {
                        // Live: gilt ab jetzt
                        o.addEvent(currentTotalMs(), o.volume, o.playing)
                    } else if (o.events.isNotEmpty()) {
                        // Zwischen Segmenten: gilt ab dem Ende der Aufnahme (vorheriges bleibt)
                        val end = currentTotalMs()
                        if (o.events.last().atMs >= end) o.events[o.events.lastIndex] = o.events.last().copy(gain = o.volume)
                        else o.addEvent(end, o.volume, o.playing)
                    }
                    label.text = getString(R.string.volume_percent, value)
                    overlayPlayer?.volume = previewVolume(o)
                    tilePlayers[o.id]?.volume = previewVolume(o)
                    updateOverlayButtons(o)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
            })
        }
        val hint = android.widget.TextView(this).apply {
            textSize = 13f
            text = getString(R.string.volume_hint)
            setPadding(0, pad / 2, 0, 0)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(label); addView(seek); addView(hint)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.overlay_volume_title)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ -> persistSession() }
            .setOnDismissListener { persistSession() }
            .show()
    }

    /** Position des Overlay-Videos an die Gesamtlänge der Aufnahme angleichen. */
    /**
     * @param afterDelete true, wenn gerade ein Segment gelöscht wurde: liegt der Einfügezeitpunkt
     * jetzt hinter dem Ende der Aufnahme, wird er auf das Ende gezogen. In allen anderen Fällen
     * bleibt der Einfügezeitpunkt unangetastet.
     */
    private fun syncOverlayPlayer(afterDelete: Boolean = false) {
        val o = overlayStore.videoOverlay() ?: return
        val p = overlayPlayer ?: return
        val total = segments.sumOf { it.durationMs } + liveDurationMs
        if (afterDelete) {
            o.trimEvents(total)
            if (total < o.startOffsetMs) {
                o.startOffsetMs = total
                o.events.clear(); o.addEvent(total, o.volume, true)
            }
        }
        p.seekTo(o.sourcePositionAt(total))
    }

    /** Play/Pause des Overlay-Videos während der Aufnahme – als Protokolleintrag. */
    private fun toggleOverlayPlayPause() {
        val now = currentTotalMs()
        val o = overlayStore.videoOverlay()
        val current = o?.playing ?: tileVideos().firstOrNull()?.playing ?: return
        val playing = !current
        o?.let { it.addEvent(now, it.volume, playing); if (playing) overlayPlayer?.play() else overlayPlayer?.pause() }
        tileVideos().forEach { v -> v.addEvent(now, v.volume, playing); tilePlayers[v.id]?.let { if (playing) it.play() else it.pause() } }
        refreshUi()
    }

    /**
     * Lädt ein Bild mit korrekt angewendeter EXIF-Drehung als Software-Bitmap (für OpenGL nötig),
     * auf maxEdge Pixel Kantenlänge begrenzt.
     */
    private fun loadBitmap(uri: Uri, maxEdge: Int): android.graphics.Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(contentResolver, uri)
            return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                var sample = 1
                while (maxOf(info.size.width, info.size.height) / sample > maxEdge) sample *= 2
                decoder.setTargetSampleSize(sample)
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // Android 8: BitmapFactory + manuelle EXIF-Drehung
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val raw = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("Bild konnte nicht gelesen werden")
        val orientation = contentResolver.openInputStream(uri)?.use {
            androidx.exifinterface.media.ExifInterface(it)
                .getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
        } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        val m = android.graphics.Matrix()
        when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return raw
        }
        return android.graphics.Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    // ---------------------------------------------------------------- Untertitel

    private val captionLanguages = listOf(
        null to "Automatisch erkennen", "de" to "Deutsch", "en" to "English", "tr" to "Türkçe",
        "fr" to "Français", "es" to "Español", "it" to "Italiano", "ru" to "Русский", "ar" to "العربية", "pl" to "Polski"
    )

    /** Sprachmodell einmalig im Hintergrund laden, damit die erste Erkennung nicht warten muss. */
    private fun prefetchCaptionModel() {
        val model = captionModel
        if (modelManager.isAvailable(model)) return
        Toast.makeText(this, getString(R.string.captions_prefetch, model.approxMb), Toast.LENGTH_LONG).show()
        whisperExecutor.execute {
            try {
                modelManager.download(model) { }
                main.post { Toast.makeText(this, R.string.captions_prefetch_done, Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                Log.w(TAG, "Modell-Vorabladen fehlgeschlagen", e)
            }
        }
    }

    private fun showCaptionsDialog() {
        if (transcribing) return
        val pad = (16 * resources.displayMetrics.density).toInt()
        val savedLang = prefs.getString("captions_lang", null)
        val spinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                captionLanguages.map { it.second })
            setSelection(captionLanguages.indexOfFirst { it.first == savedLang }.coerceAtLeast(0))
        }
        // Vorschaukarten der Vorlagen (echter Renderer, Beispieltext)
        val dp = resources.displayMetrics.density
        val cardW = (150 * dp).toInt(); val cardH = (84 * dp).toInt()
        val cardRow = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        val cards = ArrayList<android.view.View>()
        fun renderCards() {
            cardRow.removeAllViews(); cards.clear()
            de.codinix.videoeditor.whisper.CaptionStyle.TEMPLATE_NAMES.forEachIndexed { idx, name ->
                val img = android.widget.ImageView(this).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
                    setImageBitmap(de.codinix.videoeditor.whisper.CaptionStyle.preview(idx, captionSettings.accentColor, 720, 720))
                }
                val label = android.widget.TextView(this).apply { text = name; textSize = 11f; gravity = android.view.Gravity.CENTER }
                val card = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(cardW, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(pad / 4, 0, pad / 4, 0) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 10 * dp
                        setColor(0xFF2A2A34.toInt())
                        setStroke((2.5f * dp).toInt(), if (idx == captionSettings.template) captionSettings.accentColor else 0x00000000)
                    }
                    addView(img, android.widget.LinearLayout.LayoutParams(cardW, cardH))
                    addView(label)
                    setOnClickListener {
                        captionSettings.template = idx
                        binding.review.captionView.settings = captionSettings
                        saveDefaultCaptionSettings()
                        persistSession()
                        renderCards()
                    }
                }
                cards.add(card); cardRow.addView(card)
            }
        }
        renderCards()
        val cardScroll = android.widget.HorizontalScrollView(this).apply { addView(cardRow); isHorizontalScrollBarEnabled = false }

        // Akzentfarbe
        val accentRow = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(0, pad / 2, 0, 0) }
        de.codinix.videoeditor.whisper.CaptionStyle.ACCENT_COLORS.forEach { c ->
            val size = (30 * dp).toInt()
            accentRow.addView(android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply { setMargins(pad / 4, 0, pad / 4, 0) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c)
                    setStroke((if (c == captionSettings.accentColor) 4 else 1) * dp.toInt().coerceAtLeast(1), 0xFFFFFFFF.toInt())
                }
                setOnClickListener {
                    captionSettings.accentColor = c
                    binding.review.captionView.settings = captionSettings
                    saveDefaultCaptionSettings()
                    persistSession(); renderCards()
                    for (i in 0 until accentRow.childCount) {
                        (accentRow.getChildAt(i).background as android.graphics.drawable.GradientDrawable)
                            .setStroke((if (de.codinix.videoeditor.whisper.CaptionStyle.ACCENT_COLORS[i] == c) 4 else 1) * dp.toInt().coerceAtLeast(1), 0xFFFFFFFF.toInt())
                    }
                }
            })
        }

        val models = de.codinix.videoeditor.whisper.ModelManager.Model.values()
        val modelSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                models.map { it.label + if (modelManager.isAvailable(it)) "" else " · ${it.approxMb} MB Download" })
            setSelection(prefs.getInt("captions_model", 1))
        }
        val emojiSwitch = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
            text = getString(R.string.captions_emojis)
            isChecked = captionSettings.emojis
            setPadding(0, pad / 2, 0, 0)
            setOnCheckedChangeListener { _, on ->
                captionSettings.emojis = on
                binding.review.captionView.settings = captionSettings
                saveDefaultCaptionSettings(); persistSession()
            }
        }
        val auto = android.widget.CheckBox(this).apply {
            text = getString(R.string.captions_always)
            isChecked = prefs.getBoolean("captions_auto", false)
        }
        val note = android.widget.TextView(this).apply {
            text = getString(R.string.captions_export_note); textSize = 12f; setPadding(0, pad / 2, 0, 0)
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(android.widget.TextView(this@MainActivity).apply { text = getString(R.string.captions_template) })
            addView(cardScroll)
            addView(android.widget.HorizontalScrollView(this@MainActivity).apply { addView(accentRow); isHorizontalScrollBarEnabled = false })
            addView(emojiSwitch)
            addView(android.widget.TextView(this@MainActivity).apply { text = getString(R.string.captions_language); setPadding(0, pad / 2, 0, 0) })
            addView(spinner)
            addView(android.widget.TextView(this@MainActivity).apply { text = getString(R.string.captions_model); setPadding(0, pad / 2, 0, 0) })
            addView(modelSpinner)
            addView(auto); addView(note)
        }
        val b = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.captions_title)
            .setView(box)
            .setPositiveButton(if (captions.isEmpty()) R.string.captions_generate else R.string.captions_regenerate) { _, _ ->
                val lang = captionLanguages[spinner.selectedItemPosition].first
                prefs.edit().putString("captions_lang", lang).putBoolean("captions_auto", auto.isChecked)
                    .putInt("captions_model", modelSpinner.selectedItemPosition).apply()
                startTranscription(lang)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> prefs.edit().putBoolean("captions_auto", auto.isChecked).apply() }
        var removeBtn: android.widget.Button? = null
        if (captions.isNotEmpty()) {
            b.setNeutralButton(R.string.captions_edit) { _, _ -> showCaptionEditor(-1) }
            removeBtn = android.widget.Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                text = getString(R.string.captions_remove)
            }
            box.addView(removeBtn)
        }
        val dlg = b.create()
        removeBtn?.setOnClickListener {
            captions.clear(); refreshCaptionUi(); persistSession(); dlg.dismiss()
        }
        dlg.show()
    }

    /**
     * Untertitel-Editor: Liste aller Blöcke mit Zeit, Textfeld, Verschiebe-Pfeilen und Löschen.
     * Antippen der Zeit springt in der Review zum Block. [focusIdx] wird beim Öffnen fokussiert.
     */
    private fun showCaptionEditor(focusIdx: Int) {
        if (captions.isEmpty()) return
        player?.pause()
        val dp = resources.displayMetrics.density
        val pad = (10 * dp).toInt()
        val list = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(pad, 0, pad, 0) }
        val working = captions.toMutableList()
        var focusView: android.view.View? = null

        fun t(ms: Long) = "%d:%02d.%d".format(ms / 60000, (ms / 1000) % 60, (ms % 1000) / 100)

        fun rebuild() {
            list.removeAllViews()
            working.forEachIndexed { idx, c ->
                val row = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(0, pad / 2, 0, pad / 2)
                }
                fun smallBtn(label: String, onClick: () -> Unit) = android.widget.Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                    text = label; textSize = 13f; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                    setPadding(pad, pad / 2, pad, pad / 2)
                    setOnClickListener { onClick() }
                }
                fun seekTo(ms: Long) { player?.let { p ->
                    var rest = ms; var item = 0
                    for (seg in segments) { if (rest < seg.durationMs) break; rest -= seg.durationMs; item++ }
                    p.seekTo(item.coerceAtMost(segments.lastIndex), rest); p.play()
                } }
                // Zeile 1: Anfang ◀ ▶ | Ende ◀ ▶ | 🗑
                val head = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                val segNo = run { var acc = 0L; var n = 1; for (seg in segments) { if (c.startMs < acc + seg.durationMs) break; acc += seg.durationMs; n++ }; n }
                val startLabel = android.widget.TextView(this).apply {
                    text = "S$segNo · Anfang ${t(c.startMs)}"; textSize = 12f; alpha = 0.85f
                    setOnClickListener { seekTo(c.startMs) }
                }
                val endLabel = android.widget.TextView(this).apply {
                    text = "Ende ${t(c.endMs)}"; textSize = 12f; alpha = 0.85f
                    setOnClickListener { seekTo((c.endMs - 1500).coerceAtLeast(c.startMs)) }
                }
                head.addView(startLabel, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                head.addView(smallBtn("◀") { shiftStart(working, idx, -250); rebuild() })
                head.addView(smallBtn("▶") { shiftStart(working, idx, +250); rebuild() })
                head.addView(endLabel, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                head.addView(smallBtn("◀") { shiftEnd(working, idx, -250); rebuild() })
                head.addView(smallBtn("▶") { shiftEnd(working, idx, +250); rebuild() })
                head.addView(smallBtn("🗑") { working.removeAt(idx); rebuild() })
                val edit = android.widget.EditText(this).apply {
                    setText(c.text)
                    textSize = 15f
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE or android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(t: android.text.Editable?) {
                            val newText = t?.toString()?.trim() ?: return
                            if (idx < working.size && working[idx].text != newText) working[idx] = retext(working[idx], newText)
                        }
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    })
                }
                if (idx == focusIdx) focusView = edit
                row.addView(head); row.addView(edit)
                list.addView(row)
            }
        }
        rebuild()
        val scroll = android.widget.ScrollView(this).apply { addView(list) }
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.captions_edit_title)
            .setView(scroll)
            .setPositiveButton(R.string.ok) { _, _ ->
                captions.clear(); captions.addAll(working.sortedBy { it.startMs })
                refreshCaptionUi()
                persistSession()
                if (inReview) player?.play()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> if (inReview) player?.play() }
            .setOnCancelListener { if (inReview) player?.play() }
            .create()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dlg.show()
        focusView?.let { v -> v.requestFocus(); scroll.post { scroll.smoothScrollTo(0, (v.parent as android.view.View).top) } }
    }

    /**
     * Nur den ANFANG eines Blocks verschieben, das Ende bleibt. Nach vorn: der vorherige Block
     * wird gekürzt, damit nichts überlappt. Nach hinten: mindestens 0,3 s Anzeige bleiben.
     * Wörter werden proportional in die neue Dauer eingepasst.
     */
    private fun shiftStart(list: MutableList<de.codinix.videoeditor.whisper.Caption>, idx: Int, deltaMs: Long) {
        val c = list[idx]
        var newStart = (c.startMs + deltaMs).coerceAtLeast(0)
        if (newStart > c.endMs - 300) newStart = c.endMs - 300
        if (newStart == c.startMs) return
        val oldDur = (c.endMs - c.startMs).coerceAtLeast(1)
        val newDur = c.endMs - newStart
        val words = c.words.map { w ->
            val rs = (w.startMs - c.startMs).toDouble() / oldDur
            val re = (w.endMs - c.startMs).toDouble() / oldDur
            w.copy(startMs = newStart + (rs * newDur).toLong(), endMs = newStart + (re * newDur).toLong())
        }
        list[idx] = c.copy(startMs = newStart, words = words)
        if (deltaMs < 0 && idx > 0) {
            val prev = list[idx - 1]
            if (prev.endMs > newStart - 40) {
                val prevEnd = maxOf(newStart - 40, prev.startMs + 300)
                list[idx - 1] = prev.copy(endMs = prevEnd, words = prev.words.map { w -> w.copy(endMs = minOf(w.endMs, prevEnd)) })
            }
        }
    }

    /** Neuer Text: bei gleicher Wortzahl Zeiten behalten, sonst gleichmäßig über die Dauer verteilen. */
    private fun retext(c: de.codinix.videoeditor.whisper.Caption, text: String): de.codinix.videoeditor.whisper.Caption {
        val parts = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return c.copy(text = text, words = emptyList())
        val words = if (parts.size == c.words.size) {
            c.words.mapIndexed { i, w -> w.copy(text = parts[i]) }
        } else {
            val dur = (c.endMs - c.startMs).coerceAtLeast(parts.size * 120L)
            val per = dur / parts.size
            parts.mapIndexed { i, w -> de.codinix.videoeditor.whisper.Word(c.startMs + i * per, c.startMs + (i + 1) * per - 20, w) }
        }
        return c.copy(text = parts.joinToString(" "), words = words)
    }

    /**
     * Nur das ENDE verschieben, Anfang bleibt. Nach hinten: der nächste Block beginnt später,
     * damit nichts überlappt. Nach vorn: mindestens 0,3 s Anzeige bleiben.
     */
    private fun shiftEnd(list: MutableList<de.codinix.videoeditor.whisper.Caption>, idx: Int, deltaMs: Long) {
        val c = list[idx]
        var newEnd = c.endMs + deltaMs
        if (newEnd < c.startMs + 300) newEnd = c.startMs + 300
        if (newEnd == c.endMs) return
        val oldDur = (c.endMs - c.startMs).coerceAtLeast(1)
        val newDur = newEnd - c.startMs
        val words = c.words.map { w ->
            val rs = (w.startMs - c.startMs).toDouble() / oldDur
            val re = (w.endMs - c.startMs).toDouble() / oldDur
            w.copy(startMs = c.startMs + (rs * newDur).toLong(), endMs = c.startMs + (re * newDur).toLong())
        }
        list[idx] = c.copy(endMs = newEnd, words = words)
        if (deltaMs > 0 && idx + 1 < list.size) {
            val next = list[idx + 1]
            if (next.startMs < newEnd + 40) {
                val nextStart = minOf(newEnd + 40, next.endMs - 300)
                val nd = (next.endMs - nextStart).coerceAtLeast(1); val od = (next.endMs - next.startMs).coerceAtLeast(1)
                list[idx + 1] = next.copy(startMs = nextStart, words = next.words.map { w ->
                    val rs = (w.startMs - next.startMs).toDouble() / od; val re = (w.endMs - next.startMs).toDouble() / od
                    w.copy(startMs = nextStart + (rs * nd).toLong(), endMs = nextStart + (re * nd).toLong())
                })
            }
        }
    }

    private fun startTranscription(language: String?) {
        if (transcribing || segments.isEmpty()) return
        transcribing = true
        val model = captionModel
        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.captions_title)
            .setMessage(getString(R.string.captions_preparing, 0))
            .setCancelable(false)
            .show()
        player?.pause()
        val segs = segments.map { it.file to it.durationMs }
        whisperExecutor.execute {
            var engine: de.codinix.videoeditor.whisper.WhisperEngine? = null
            try {
                if (!modelManager.isAvailable(model)) {
                    modelManager.download(model) { p ->
                        main.post { dialog.setMessage(getString(R.string.captions_model_download, model.approxMb, p)) }
                    }
                }
                val pcm = de.codinix.videoeditor.whisper.AudioPrep.prepare(segs, cacheDir) { p ->
                    main.post { dialog.setMessage(getString(R.string.captions_preparing, p)) }
                }
                main.post { dialog.setMessage(getString(R.string.captions_running, 0)) }
                engine = de.codinix.videoeditor.whisper.WhisperEngine.load(modelManager.file(model))

                // Jedes Segment einzeln erkennen: Whisper kann so kein Wort in ein Nachbarsegment legen.
                val rate = de.codinix.videoeditor.whisper.AudioPrep.RATE
                val sentences = ArrayList<List<de.codinix.videoeditor.whisper.Word>>()
                val sentenceStarts = ArrayList<Long>()
                var detected = "?"
                var lang = language
                var offsetMs = 0L
                var frameOffset = 0
                val totalFrames = pcm.size
                segs.forEachIndexed { si, (_, durMs) ->
                    val frames = (durMs * rate / 1000).toInt().coerceAtMost(totalFrames - frameOffset).coerceAtLeast(0)
                    if (frames > rate / 2) {   // unter 0,5 s lohnt keine Erkennung
                        val slice = pcm.copyOfRange(frameOffset, frameOffset + frames)
                        val base = si * 100 / segs.size; val span = 100 / segs.size
                        val r = engine.transcribe(slice, lang, object : de.codinix.videoeditor.whisper.WhisperEngine.Progress {
                            override fun onProgress(percent: Int) { main.post { dialog.setMessage(getString(R.string.captions_running, base + percent * span / 100)) } }
                        })
                        if (si == 0 || detected == "?") { detected = r.language; if (lang == null && r.language != "?" && r.language != "auto") lang = r.language }
                        val segEnd = offsetMs + durMs
                        r.rawSegments.forEach { seg ->
                            val ws = seg.words.map { w ->
                                de.codinix.videoeditor.whisper.Word((w.startMs + offsetMs).coerceIn(offsetMs, segEnd - 1),
                                    (w.endMs + offsetMs).coerceIn(offsetMs + 1, segEnd), w.text)
                            }
                            if (ws.isNotEmpty()) { sentences.add(ws); sentenceStarts.add((seg.startMs + offsetMs).coerceIn(offsetMs, segEnd - 1)) }
                        }
                    }
                    frameOffset += frames
                    offsetMs += durMs
                }
                val boundaries = ArrayList<Long>(); var acc = 0L
                segs.forEach { acc += it.second; boundaries.add(acc) }
                val chunks = de.codinix.videoeditor.whisper.Caption.chunkSentences(
                    sentences, sentenceStarts = sentenceStarts, boundariesMs = boundaries.dropLast(1))
                val result = object { val language = detected }
                main.post {
                    dialog.dismiss()
                    transcribing = false
                    captions.clear(); captions.addAll(chunks)
                    refreshCaptionUi()
                    persistSession()
                    Toast.makeText(this, if (chunks.isEmpty()) getString(R.string.captions_none)
                        else getString(R.string.captions_done, chunks.size, result.language) + "\n" + getString(R.string.captions_hint), Toast.LENGTH_LONG).show()
                    if (inReview) player?.play()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Transkription fehlgeschlagen", e)
                main.post {
                    dialog.dismiss(); transcribing = false
                    Toast.makeText(this, getString(R.string.error, e.message ?: "Spracherkennung"), Toast.LENGTH_LONG).show()
                    if (inReview) player?.play()
                }
            } finally {
                engine?.close()
            }
        }
    }

    // ---------------------------------------------------------------- Segmente umordnen / löschen

    private fun reorderSegments(from: Int, to: Int) {
        val order = (0 until segments.size).toMutableList()
        val item = order.removeAt(from)
        val insertAt = if (to > from) to - 1 else to
        order.add(insertAt.coerceIn(0, order.size), item)
        applySegmentOrder(order)
        Toast.makeText(this, R.string.segment_moved, Toast.LENGTH_SHORT).show()
    }

    /** Zustand vor einem Löschen – für „Rückgängig“ (15 s). */
    private class UndoState(
        val segments: List<Segment>,
        val captions: List<de.codinix.videoeditor.whisper.Caption>,
        val history: List<AudioTrackEntry>,
        val overlayEvents: Pair<Long, List<VideoOverlay.Event>>?,
        val tileEvents: Map<Long, Pair<Long, List<VideoOverlay.Event>>>,
        val removedFile: File
    )
    private var undoState: UndoState? = null
    private val undoExpire = Runnable {
        undoState?.removedFile?.delete()
        undoState = null
        binding.review.undoButton.visibility = android.view.View.GONE
    }

    private fun confirmDeleteSegment(idx: Int) {
        if (segments.size <= 1) {
            Toast.makeText(this, R.string.segment_last_keep, Toast.LENGTH_SHORT).show(); return
        }
        // Vorherigen Undo-Zustand endgültig machen
        main.removeCallbacks(undoExpire); undoExpire.run()
        undoState = UndoState(
            segments.toList(), captions.toList(), audioHistory.toList(),
            overlayStore.videoOverlay()?.let { it.startOffsetMs to it.events.toList() },
            tileVideos().associate { it.id to (it.startOffsetMs to it.events.toList()) },
            segments[idx].file
        )
        val order = (0 until segments.size).filter { it != idx }
        applySegmentOrder(order)
        binding.review.undoButton.visibility = android.view.View.VISIBLE
        binding.review.undoButton.setOnClickListener { undoDelete() }
        main.postDelayed(undoExpire, 15_000)
        Toast.makeText(this, R.string.segment_deleted, Toast.LENGTH_SHORT).show()
    }

    private fun undoDelete() {
        val u = undoState ?: return
        main.removeCallbacks(undoExpire)
        undoState = null
        binding.review.undoButton.visibility = android.view.View.GONE
        segments.clear(); segments.addAll(u.segments)
        captions.clear(); captions.addAll(u.captions)
        audioHistory.clear(); audioHistory.addAll(u.history)
        overlayStore.videoOverlay()?.let { v -> u.overlayEvents?.let { (so, ev) -> v.startOffsetMs = so; v.events.clear(); v.events.addAll(ev) } }
        tileVideos().forEach { v -> u.tileEvents[v.id]?.let { (so, ev) -> v.startOffsetMs = so; v.events.clear(); v.events.addAll(ev) } }
        main.removeCallbacks(playbackTicker)
        reviewOverlayPlayer?.release(); reviewOverlayPlayer = null
        buildPlayer()
        main.post(playbackTicker)
        refreshCaptionUi()
        persistSession()
    }

    /**
     * Neue Segmentreihenfolge anwenden (fehlende Indizes = gelöscht) und alles, was an der
     * Zeitachse hängt, umrechnen: Untertitel, Ton-Protokolle der Overlay- und Kachelvideos,
     * Ton-Historie. Danach Review neu aufbauen.
     */
    private fun applySegmentOrder(newOrder: List<Int>) {
        val remap = TimelineRemap(segments.map { it.durationMs }, newOrder)
        val reordered = newOrder.map { segments[it] }
        segments.clear(); segments.addAll(reordered)
        val total = remap.totalMs

        val newCaptions = remap.captions(captions)
        captions.clear(); captions.addAll(newCaptions)

        fun remapVideo(v: VideoOverlay) {
            val ev = remap.timeline(v.timeline(), v.durationMs, null)
            v.events.clear(); v.events.addAll(ev)
            v.startOffsetMs = ev.firstOrNull()?.atMs ?: total
            if (ev.isEmpty()) v.addEvent(total, v.volume, true)   // erst ab jetzt wieder aktiv
        }
        overlayStore.videoOverlay()?.let { remapVideo(it) }
        tileVideos().forEach { remapVideo(it) }

        val newHistory = audioHistory.mapNotNull { e ->
            val base = e.timeline.takeIf { it.isNotEmpty() }?.map { VideoOverlay.Event(it.fromMs, it.gain, it.playing, it.seekMs) }
                ?: listOf(VideoOverlay.Event(e.startOffsetMs, e.volume, true))
            val ev = remap.timeline(base, e.durationMs, e.endOffsetMs)
            if (ev.none { it.playing }) { if (!isFileReferenced(e.file)) e.file.delete(); null }
            else e.copy(startOffsetMs = ev.first().atMs, endOffsetMs = total,
                timeline = ev.map { OverlayAudioRenderer.Segment(it.atMs, it.gain, it.playing, it.seekMs) })
        }
        audioHistory.clear(); audioHistory.addAll(newHistory)

        // Review neu aufbauen
        main.removeCallbacks(playbackTicker)
        reviewOverlayPlayer?.release(); reviewOverlayPlayer = null
        buildPlayer()
        main.post(playbackTicker)
        refreshCaptionUi()
        persistSession()
    }

    /** Review an Gesamtposition [ms] setzen (Segment und Position in der Playlist berechnen). */
    private fun seekReviewTo(ms: Long, play: Boolean) {
        val p = player ?: return
        var rest = ms.coerceAtLeast(0); var item = 0
        for (seg in segments) { if (rest < seg.durationMs) break; rest -= seg.durationMs; item++ }
        p.seekTo(item.coerceAtMost(segments.lastIndex.coerceAtLeast(0)), rest)
        if (play) { p.play(); binding.review.playIcon.visibility = android.view.View.GONE }
        binding.review.captionView.setTime(ms)
    }

    private fun refreshCaptionUi() {
        binding.review.captionView.captions = captions
        binding.review.captionsEditButton.visibility =
            if (captions.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    /** Nach dem Löschen von Segmenten: Untertitel hinter dem neuen Ende verwerfen. */
    private fun trimCaptions(totalMs: Long) {
        captions.removeAll { it.startMs >= totalMs }
        refreshCaptionUi()
    }

    // ---------------------------------------------------------------- Review

    private fun enterReview() {
        inReview = true
        cameraProvider?.unbindAll()          // Kamera freigeben, spart Akku und Decoder
        overlayPlayer?.stop()                // Puffer des Vorschau-Overlay-Players freigeben
        tilePlayers.values.forEach { it.stop() }
        binding.review.root.visibility = android.view.View.VISIBLE
        binding.previewView.visibility = android.view.View.INVISIBLE
        binding.gestureView.visibility = android.view.View.GONE
        binding.review.playIcon.visibility = android.view.View.GONE
        binding.review.captionView.settings = captionSettings
        refreshCaptionUi()
        binding.review.captionView.onSettingsChanged = { persistSession(); saveDefaultCaptionSettings() }
        binding.review.captionView.onEditRequested = { idx -> showCaptionEditor(idx) }
        buildPlayer()
        if (prefs.getBoolean("captions_auto", false) && captions.isEmpty() && !transcribing) {
            main.postDelayed({ if (inReview) startTranscription(prefs.getString("captions_lang", null)) }, 300)
        }
        main.post(playbackTicker)
    }

    private fun buildPlayer() {
        player?.release()
        val p = newLeanPlayer()
        p.setMediaItems(segments.map { MediaItem.fromUri(Uri.fromFile(it.file)) })
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.prepare()
        p.playWhenReady = true
        binding.review.playerView.player = p
        // Mikrofon-Regler in der Review hörbar machen (Anhebung über 100 % kann ein Player nicht)
        p.volume = Loudness.gain(micGain).coerceIn(0f, 1f)
        player = p
        buildReviewOverlayPlayer()
    }

    private var reviewWavGeneration = 0

    /**
     * Rendert die Overlay-Tonspur genau so, wie sie der Export mischen wird (Stille bis zum
     * Einfügezeitpunkt, Schleife, Lautstärke eingerechnet) und spielt DIESE Datei in der Review.
     * Damit ist die Review per Konstruktion identisch mit dem Ergebnis.
     */
    private fun buildReviewOverlayPlayer() {
        reviewOverlayPlayer?.release(); reviewOverlayPlayer = null
        val mixes = allAudioMixes()
        if (mixes.isEmpty()) return
        val totalMs = segments.sumOf { it.durationMs }
        if (totalMs <= 0) return
        val gen = ++reviewWavGeneration
        bgExecutor.execute {
            try {
                val wav = File(cacheDir, "review_ovl_$gen.wav")
                if (!Exporter.renderMixWav(this, mixes, totalMs, wav)) {
                    main.post { reviewWaitingForAudio = false; player?.play() }
                    return@execute
                }
                main.post {
                    if (gen != reviewWavGeneration || !inReview) { wav.delete(); return@post }
                    val p = newLeanPlayer()
                    p.setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
                    p.prepare()
                    p.playWhenReady = false
                    reviewOverlayPlayer = p
                    lastOverlaySeekAt = 0L
                    reviewWaitingForAudio = false
                    // Jetzt gemeinsam von vorn starten
                    player?.let { it.seekTo(0, 0); it.play() }
                    binding.review.playIcon.visibility = android.view.View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Review-Tonspur fehlgeschlagen", e)
                main.post { reviewWaitingForAudio = false; player?.play() }
            }
        }
        // Bild anhalten, bis der Ton bereit ist – sonst ist der Anfang stumm
        reviewWaitingForAudio = true
        player?.pause()
        binding.review.reviewStatus.text = getString(R.string.preparing_audio)
    }

    private var reviewWaitingForAudio = false

    private var lastOverlaySeekAt = 0L

    /**
     * Hält den Overlay-Ton in der Review am Abspielstand: vor dem Einfügezeitpunkt still,
     * danach an der passenden Stelle (Schleife eingerechnet). Weicht der Player mehr als
     * eine Viertelsekunde ab, wird nachgezogen.
     */
    private fun syncReviewOverlayAudio(reviewPosMs: Long, mainPlaying: Boolean) {
        val p = reviewOverlayPlayer ?: return
        if (!mainPlaying) {
            if (p.playWhenReady) p.pause()
            return
        }
        // Die Review-Tonspur ist bereits die fertige Mischung: Position = Videoposition
        val target = reviewPosMs
        val now = System.currentTimeMillis()
        val drift = kotlin.math.abs(p.currentPosition - target)
        // Nur nachziehen, wenn die Abweichung groß ist und der letzte Sprung lange genug her ist –
        // sonst entsteht eine Rückkopplung aus Springen und Anlaufen.
        val wasPlaying = p.playWhenReady
        if (drift > 700 && now - lastOverlaySeekAt > 1500 && (p.playbackState == Player.STATE_READY || !wasPlaying)) {
            p.seekTo(target)
            lastOverlaySeekAt = now
        }
        if (!wasPlaying) {
            // Beim (Wieder-)Start exakt positionieren
            if (drift > 150) { p.seekTo(target); lastOverlaySeekAt = now }
            p.play()
        }
    }

    private fun exitReview() {
        main.removeCallbacks(undoExpire); undoExpire.run()
        inReview = false
        main.removeCallbacks(playbackTicker)
        player?.release(); player = null
        reviewOverlayPlayer?.release(); reviewOverlayPlayer = null
        binding.review.playerView.player = null
        binding.review.root.visibility = android.view.View.GONE
        binding.previewView.visibility = android.view.View.VISIBLE
        binding.gestureView.visibility = android.view.View.VISIBLE
        disarmDelete()
        bindCamera()
        // Vorschau-Overlay-Player wieder vorbereiten (in der Review gestoppt)
        overlayPlayer?.let { if (it.playbackState == Player.STATE_IDLE) { it.prepare(); syncOverlayPlayer() } }
        syncTilePlayers()
        refreshUi()
    }

    private fun togglePlayback() {
        if (reviewWaitingForAudio) return
        val p = player ?: return
        if (p.isPlaying) {
            p.pause(); reviewOverlayPlayer?.pause()
            binding.review.playIcon.visibility = android.view.View.VISIBLE
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
        syncReviewOverlayAudio(pos, p.isPlaying)
        binding.review.reviewBar.updatePlayback(segments.map { it.durationMs }, pos, deleteArmed)
        binding.review.scrubBar.segmentsMs = segments.map { it.durationMs }
        binding.review.scrubBar.positionMs = pos
        binding.review.captionView.setTime(pos)
        if (!deleteArmed && !reviewWaitingForAudio) {
            val vol = overlayStore.videoOverlay()?.let { " · Overlay ${(it.volume * 100).toInt()} %" } ?: ""
            binding.review.reviewStatus.text = getString(R.string.review_position, fmt(pos), fmt(total), segments.size) + vol
        }
    }

    private fun showExportDialog() {
        if (segments.isEmpty()) return
        disarmDelete()
        player?.pause()
        val info = VideoConcat.inspect(segments.first().file)
        val recordedHeight = if (info.rotation == 90 || info.rotation == 270) info.width else info.height
        val totalSec = segments.sumOf { it.durationMs } / 1000.0
        val needsReencode = allAudioMixes().isNotEmpty() || captions.isNotEmpty() || kotlin.math.abs(micGain - 1f) >= 0.01f
        fun sizeText(bytes: Double) = if (bytes >= 1e9) "≈ %.1f GB".format(Locale.GERMANY, bytes / 1e9) else "≈ %.0f MB".format(Locale.GERMANY, bytes / 1e6)
        fun estimate(height: Int) = (Exporter.videoBitrateFor(height) + Exporter.AUDIO_BITRATE) / 8.0 * totalSec
        val originalSize = if (needsReencode) estimate(recordedHeight) else segments.sumOf { it.file.length() }.toDouble()
        val options = mutableListOf<Pair<String, Int?>>((getString(R.string.export_original) + "  " + sizeText(originalSize)) to null)
        listOf(2160 to "4K (2160p)", 1440 to "2K (1440p)", 1080 to "1080p", 720 to "720p", 480 to "480p")
            .filter { it.first < recordedHeight }
            .forEach { options.add((it.second + "  " + sizeText(estimate(it.first))) to it.first) }

        val pad = (20 * resources.displayMetrics.density).toInt()
        val radios = android.widget.RadioGroup(this)
        options.forEachIndexed { i, (name, _) ->
            radios.addView(android.widget.RadioButton(this).apply { id = 1000 + i; text = name; isChecked = i == 0 })
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(radios)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.save) { _, _ ->
                val idx = (radios.checkedRadioButtonId - 1000).coerceIn(0, options.lastIndex)
                runExport(options[idx].second, micGain)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> player?.play() }
            .setOnCancelListener { player?.play() }
            .show()
    }

    private fun runExport(targetHeight: Int?, micGain: Float = 1f) {
        val dialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setMessage(getString(R.string.export_running, 0))
            .setCancelable(false)
            .show()
        setControlsEnabled(false)
        // Speicher für den Export freimachen: Player der Review komplett freigeben
        main.removeCallbacks(playbackTicker)
        player?.release(); player = null
        reviewOverlayPlayer?.release(); reviewOverlayPlayer = null
        binding.review.playerView.player = null

        val ex = Exporter(this)
        ex.captions = captions.toList()
        ex.captionSettings = captionSettings.copy()
        exporter = ex
        val audioMix = allAudioMixes()
        val progressRes = if (audioMix.isEmpty() && kotlin.math.abs(micGain - 1f) < 0.01f)
            R.string.export_running else R.string.export_running_mix
        if (audioMix.isNotEmpty()) {
            val levels = audioMix.joinToString("/") { "${(it.gain * 100).toInt()} %" }
            Toast.makeText(this, "Export: ${audioMix.size} Overlay-Tonspur(en) $levels, Mikrofon ${(micGain * 100).toInt()} %", Toast.LENGTH_LONG).show()
        }
        ex.export(segments.map { it.file }, targetHeight, object : Exporter.Listener {
            override fun onProgress(percent: Int) {
                dialog.setMessage(getString(progressRes, percent))
            }
            override fun onDone(uri: Uri) {
                dialog.dismiss()
                ex.release(); exporter = null
                segments.forEach { it.file.delete() }
                segments.clear()
                audioHistory.forEach { it.file.delete() }
                audioHistory.clear()
                captions.clear()
                captionSettings = loadDefaultCaptionSettings()
                clearOverlays()
                resetMosaic()
                setControlsEnabled(true)
                if (inReview) exitReview() else refreshUi()
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.saved_title)
                    .setMessage(R.string.saved_msg)
                    .setPositiveButton(R.string.share) { _, _ -> shareVideo(uri) }
                    .setNegativeButton("OK", null)
                    .show()
            }
            override fun onError(message: String) {
                dialog.dismiss()
                ex.release(); exporter = null
                setControlsEnabled(true)
                if (inReview) { buildPlayer(); main.post(playbackTicker) }
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(getString(R.string.error, ""))
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }, audioMix, micGain)
    }

    private fun shareVideo(uri: Uri) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun confirmDiscard() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.discard_title)
            .setMessage(getString(R.string.discard_msg, segments.size))
            .setPositiveButton(R.string.discard) { _, _ ->
                activeRecording?.stop(); activeRecording = null
                segments.forEach { it.file.delete() }
                segments.clear()
                audioHistory.forEach { it.file.delete() }
                audioHistory.clear()
                captions.clear()
                captionSettings = loadDefaultCaptionSettings()
                resetMosaic()
                clearSession()
                clearOverlays()
                refreshUi()
            }
            .setNeutralButton(R.string.save_draft) { _, _ -> saveDraft() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- Absturzschutz

    private val sessionFile by lazy { File(cacheDir, "session.json") }
    private val sessionImgDir by lazy { File(cacheDir, "session_img").apply { mkdirs() } }

    /**
     * Schreibt den Arbeitsstand (Segmente, Overlays, Kameraeinstellung) in eine Datei.
     * Nach einem Absturz wird daraus beim nächsten Start ein Entwurf.
     */
    private fun persistSession() {
        try {
            if (segments.isEmpty()) { sessionFile.delete(); return }
            val segs = org.json.JSONArray()
            segments.forEach { segs.put(org.json.JSONObject().put("path", it.file.absolutePath).put("durationMs", it.durationMs)) }
            val ovs = org.json.JSONArray()
            overlayStore.items.forEach { o ->
                val j = org.json.JSONObject()
                    .put("cx", o.cx.toDouble()).put("cy", o.cy.toDouble())
                    .put("widthFrac", o.widthFrac.toDouble()).put("rotationDeg", o.rotationDeg.toDouble())
                when (o) {
                    is ImageOverlay -> {
                        val png = File(sessionImgDir, "img_${o.id}.png")
                        if (!png.exists()) png.outputStream().use { o.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        j.put("type", "image").put("path", png.absolutePath)
                    }
                    is de.codinix.videoeditor.overlay.CameraOverlay -> j.put("type", "camera")
                        .put("shape", o.shape).put("border", o.border)
                    is TextOverlay -> j.put("type", "text").put("text", o.text)
                        .put("color", o.colorArgb).put("background", o.background)
                        .put("bgColor", o.bgColorArgb ?: org.json.JSONObject.NULL)
                    is VideoOverlay -> j.put("type", "video").put("path", o.file.absolutePath)
                        .put("volume", o.volume.toDouble()).put("startOffsetMs", o.startOffsetMs)
                        .put("isBackground", o.isBackground)
                        .put("events", eventsJson(o.events))
                }
                ovs.put(j)
            }
            val tracks = org.json.JSONArray()
            audioHistory.forEach { t ->
                tracks.put(org.json.JSONObject().put("path", t.file.absolutePath)
                    .put("startOffsetMs", t.startOffsetMs).put("endOffsetMs", t.endOffsetMs)
                    .put("volume", t.volume.toDouble()).put("durationMs", t.durationMs)
                    .put("events", eventsJson(t.timeline.map { VideoOverlay.Event(it.fromMs, it.gain, it.playing, it.seekMs) })))
            }
            val mosaicJson = if (mosaicActive) mosaic.toJson(mosaic.tiles.mapIndexed { i, t ->
                t.video?.file?.absolutePath ?: t.bitmap?.let { b ->
                    val png = File(sessionImgDir, "tile_${i}_${System.identityHashCode(b)}.png")
                    if (!png.exists()) png.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    png.absolutePath
                }
            }) else null
            val root = org.json.JSONObject()
                .put("segments", segs).put("overlays", ovs).put("audioTracks", tracks)
                .put("colorFilter", compositor.colorFilter)
                .put("mosaic", mosaicJson ?: org.json.JSONObject.NULL)
                .put("captions", de.codinix.videoeditor.whisper.Caption.listToJson(captions))
                .put("captionSettings", captionSettings.toJson())
                .put("lensFacing", lensFacing)
                .put("quality", preferredQuality?.let { label(it) } ?: org.json.JSONObject.NULL)
            sessionFile.writeText(root.toString())
        } catch (e: Exception) { Log.w(TAG, "Sitzung sichern fehlgeschlagen", e) }
    }

    private fun eventsJson(events: List<VideoOverlay.Event>): org.json.JSONArray {
        val a = org.json.JSONArray()
        events.forEach { a.put(org.json.JSONObject().put("atMs", it.atMs).put("gain", it.gain.toDouble()).put("playing", it.playing).put("seek", it.seekMs ?: org.json.JSONObject.NULL)) }
        return a
    }

    private fun clearSession() {
        sessionFile.delete()
        sessionImgDir.listFiles()?.forEach { it.delete() }
    }

    private fun recoverSessionIfAny() {
        try {
            if (sessionFile.exists()) {
                val info = drafts.recoverFromSession(org.json.JSONObject(sessionFile.readText()))
                if (info != null) Toast.makeText(this, R.string.recovered, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) { Log.w(TAG, "Wiederherstellung fehlgeschlagen", e) }
        clearSession()
        // Was jetzt noch herumliegt, gehört niemandem mehr
        segmentDir.listFiles()?.forEach { it.delete() }
    }

    private fun showCrashReportIfAny() {
        val report = CrashLog.takeLast(this) ?: return
        val view = android.widget.ScrollView(this).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
            addView(android.widget.TextView(this@MainActivity).apply {
                text = report; textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextIsSelectable(true)
            })
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crash_title)
            .setMessage(R.string.crash_msg)
            .setView(view)
            .setPositiveButton(R.string.copy) { _, _ ->
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("crash", report))
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.ok, null)
            .show()
    }

    // ---------------------------------------------------------------- Entwürfe

    private fun saveDraft() {
        if (activeRecording != null) return
        if (segments.isEmpty()) {
            Toast.makeText(this, R.string.no_segments, Toast.LENGTH_SHORT).show(); return
        }
        try {
            drafts.save(
                segments.map { it.file to it.durationMs },
                overlayStore.items.toList(),
                lensFacing,
                preferredQuality?.let { label(it) },
                audioHistory.map { DraftStore.AudioTrack(it.file, it.startOffsetMs, it.endOffsetMs, it.volume, it.durationMs,
                    it.timeline.map { t -> VideoOverlay.Event(t.fromMs, t.gain, t.playing, t.seekMs) }) },
                captions.toList(), captionSettings,
                if (mosaicActive) mosaic else null
            )
            captions.clear()
            resetMosaic()
            segments.clear()
            audioHistory.clear()
            // Dateien der Video-Overlays wurden in den Entwurf verschoben – nur Player/Textur freigeben
            overlayStore.items.filterIsInstance<VideoOverlay>().forEach {
                overlayPlayer?.release(); overlayPlayer = null
                compositor.releaseVideoLayer(it.id)
            }
            overlayStore.clear()
            updateOverlayButtons(null)
            clearSession()
            Toast.makeText(this, R.string.draft_saved, Toast.LENGTH_SHORT).show()
            if (inReview) exitReview() else refreshUi()
        } catch (e: Exception) {
            Log.e(TAG, "Entwurf speichern fehlgeschlagen", e)
            Toast.makeText(this, getString(R.string.error, e.message ?: "Entwurf"), Toast.LENGTH_LONG).show()
        }
    }

    private fun showDrafts() {
        if (activeRecording != null || segments.isNotEmpty()) return
        val list = drafts.list()
        if (list.isEmpty()) {
            Toast.makeText(this, R.string.no_drafts, Toast.LENGTH_SHORT).show(); return
        }
        val fmtDate = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        val dp = resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = list.size
            override fun getItem(i: Int) = list[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convert: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val info = list[i]
                val row = (convert as? android.widget.LinearLayout) ?: android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(pad, pad / 2, pad, pad / 2)
                    addView(android.widget.ImageView(this@MainActivity).apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        layoutParams = android.widget.LinearLayout.LayoutParams((54 * dp).toInt(), (96 * dp).toInt())
                        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * dp; setColor(0xFF333340.toInt()) }
                        clipToOutline = true
                    })
                    addView(android.widget.LinearLayout(this@MainActivity).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(pad, 0, 0, 0)
                        addView(android.widget.TextView(this@MainActivity).apply { textSize = 16f })
                        addView(android.widget.TextView(this@MainActivity).apply { textSize = 13f; alpha = 0.7f })
                    })
                }
                val img = row.getChildAt(0) as android.widget.ImageView
                val texts = row.getChildAt(1) as android.widget.LinearLayout
                if (info.thumb.exists()) img.setImageBitmap(BitmapFactory.decodeFile(info.thumb.absolutePath)) else img.setImageDrawable(null)
                (texts.getChildAt(0) as android.widget.TextView).text = fmtDate.format(info.createdAt)
                (texts.getChildAt(1) as android.widget.TextView).text =
                    resources.getQuantityString(R.plurals.segments, info.segmentCount, info.segmentCount) + " · " + fmt(info.durationMs)
                return row
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drafts)
            .setAdapter(adapter) { _, which -> askDraftAction(list[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun askDraftAction(info: DraftStore.Info) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drafts)
            .setMessage(getString(R.string.draft_item,
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(info.createdAt),
                info.segmentCount, fmt(info.durationMs)))
            .setPositiveButton(R.string.open) { _, _ -> loadDraft(info) }
            .setNeutralButton(R.string.delete) { _, _ -> drafts.delete(info) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadDraft(info: DraftStore.Info) {
        try {
            val loaded = drafts.load(info, segmentDir)
            segments.clear()
            loaded.segments.forEach { (f, d) -> segments.add(Segment(f, d)) }
            clearOverlays()
            loaded.overlays.forEach { overlayStore.add(it) }
            captions.clear(); captions.addAll(loaded.captions)
            captionSettings = loaded.captionSettings
            mosaic = loaded.mosaic ?: de.codinix.videoeditor.overlay.Mosaic(de.codinix.videoeditor.overlay.Mosaic.LAYOUT_NONE)
            binding.tileVideoButton.setOnClickListener {
            if (mosaic.selected >= 0) pickTileVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.tileSoundButton.setOnClickListener {
            mosaic.tiles.getOrNull(mosaic.selected)?.video?.let { showVolumeDialog(it) }
        }
        binding.gestureView.mosaic = mosaic
            publishMosaic(); updateTileButtons()
            audioHistory.clear()
            loaded.audioTracks.forEach { audioHistory.add(AudioTrackEntry(it.file, it.startOffsetMs, it.endOffsetMs, it.volume, it.durationMs,
                it.events.map { e -> OverlayAudioRenderer.Segment(e.atMs, e.gain, e.playing, e.seekMs) })) }
            overlayStore.selectedId = null
            overlayStore.videoOverlay()?.let { attachVideoOverlay(it) }
            applyGreenscreenState()
            updateOverlayButtons(null)

            // Kameraeinstellungen wiederherstellen, damit neue Segmente zu den alten passen
            lensFacing = loaded.lensFacing
            preferredQuality = QUALITY_ORDER.firstOrNull { label(it) == loaded.qualityLabel }
            bindCamera()

            binding.gestureView.invalidate()
            refreshUi()
            persistSession()
            Toast.makeText(this, R.string.draft_loaded, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Entwurf laden fehlgeschlagen", e)
            Toast.makeText(this, getString(R.string.error, e.message ?: "Entwurf"), Toast.LENGTH_LONG).show()
        }
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
        binding.segmentBar.limitMs = MAX_TOTAL_MS
        val remaining = MAX_TOTAL_MS - currentTotalMs()
        binding.segmentBar.warnLevel = when { remaining <= 30_000 -> 2; remaining <= 60_000 -> 1; else -> 0 }

        binding.draftsButton.visibility =
            if (segments.isEmpty() && !recording) android.view.View.VISIBLE else android.view.View.GONE

        // Während der Aufnahme sind Auflösung, Löschen und Fertig gesperrt (Kamera-Wechsel nicht).
        listOf(binding.qualityButton, binding.deleteButton, binding.finishButton).forEach {
            it.alpha = if (recording) 0.35f else 1f
        }
        // Während der Aufnahme wird die Löschtaste zum Play/Pause-Knopf fürs Overlay-Video
        val vo = overlayStore.videoOverlay()
        val anyVideoPlaying = vo?.playing ?: tileVideos().firstOrNull()?.playing
        if (recording && anyVideoPlaying != null) {
            binding.deleteButton.alpha = 1f
            binding.deleteButton.setImageResource(if (anyVideoPlaying) R.drawable.ic_pause else R.drawable.ic_play_small)
            binding.deleteButton.contentDescription = getString(if (anyVideoPlaying) R.string.overlay_pause else R.string.overlay_play)
        } else {
            binding.deleteButton.setImageResource(R.drawable.ic_backspace)
            binding.deleteButton.contentDescription = getString(R.string.delete_last)
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
        reviewOverlayPlayer?.pause()
        overlayPlayer?.pause()
        tilePlayers.values.forEach { it.pause() }
    }

    override fun onStart() {
        super.onStart()
        if (inReview) { player?.play(); binding.review.playIcon.visibility = android.view.View.GONE }
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(playbackTicker)
        player?.release(); player = null
        reviewOverlayPlayer?.release(); reviewOverlayPlayer = null
        exporter?.release()
        overlayPlayer?.release(); overlayPlayer = null
        tilePlayers.values.forEach { it.release() }; tilePlayers.clear()
        compositor.release()
        whisperExecutor.shutdown()
        bgExecutor.shutdown()
    }

    companion object {
        private const val TAG = "VideoEditor"
        /** Gesamtlänge einer Aufnahme (Produktlimit, wie TikTok-Kurzvideos). */
        const val MAX_TOTAL_MS = 5 * 60_000L
        private val QUALITY_ORDER = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
    }
}
