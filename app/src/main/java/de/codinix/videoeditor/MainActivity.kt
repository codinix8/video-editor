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

    private data class Segment(val file: File, val durationMs: Long, var micGain: Float = 1f)
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
    /** BabaCut Pro (Google Play Billing). */
    private val pro by lazy { Pro(this).also { it.onChanged = { main.post { onProChanged() } } } }
    private val isPro: Boolean get() = Pro.isActive(this)

    private val pickTileImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) setTileImage(uri)
    }
    private val pickTileVideo = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) setTileVideo(uri)
    }
    /** Datei-Browser als Ausweg für Videos, die der Galerie-Picker nicht anzeigt (z. B. Downloads). */
    private enum class VideoTarget { OVERLAY, BACKGROUND, TILE }
    private var pendingVideoTarget = VideoTarget.OVERLAY
    private val pickVideoDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        when (pendingVideoTarget) {
            VideoTarget.OVERLAY -> addVideoOverlay(uri)
            VideoTarget.BACKGROUND -> addVideoOverlay(uri, asBackground = true)
            VideoTarget.TILE -> setTileVideo(uri)
        }
    }
    /** Beim Antippen eines Medien-Knopfs: die ersten drei Male auf den Datei-Browser hinweisen. */
    private fun hintFileBrowser() {
        val n = prefs.getInt("file_browser_hint_count", 0)
        if (n >= 3) return
        prefs.edit().putInt("file_browser_hint_count", n + 1).apply()
        showTip(getString(R.string.tip_file_browser), 6000, gesture = false)
    }

    private fun openVideoDocument(target: VideoTarget) {
        pendingVideoTarget = target
        Toast.makeText(this, R.string.file_browser_hint, Toast.LENGTH_SHORT).show()
        pickVideoDocument.launch(arrayOf("video/*", "application/octet-stream"))
    }

    /** Player der Kachelvideos, per Video-ID. */
    private val tilePlayers = HashMap<Long, ExoPlayer>()
    private fun tileVideos() = if (mosaicActive) mosaic.videos() else emptyList()
    /** Alle Videos, die während der Aufnahme laufen (Overlay/Hintergrund + Kacheln). */
    private fun anyLiveVideo(): Boolean = overlayStore.videoOverlay() != null || tileVideos().isNotEmpty()

    // ---- Untertitel ----
    private val captions = mutableListOf<de.codinix.videoeditor.whisper.Caption>()
    /** Texte, die in der Review über das ganze Video gelegt werden. */
    private val reviewTexts = mutableListOf<de.codinix.videoeditor.whisper.ReviewText>()
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
        prefs.getString("default_quality", null)?.let { q -> preferredQuality = QUALITY_ORDER.firstOrNull { label(it) == q } }
        cacheDir.listFiles()?.filter { it.name.startsWith("overlay_video_") || it.name.startsWith("export_") }
            ?.forEach { it.delete() }

        compositor = CompositorProcessor(overlayStore)
        compositorEffect = CompositorEffect(compositor)
        binding.safeZone.platform = prefs.getInt("safe_zone", 0)
        compositor.onFrameAspectChanged = { aspect -> main.post {
            binding.gestureView.frameAspect = aspect
            binding.safeZone.frameAspect = aspect
            de.codinix.videoeditor.overlay.CameraOverlay.cameraAspect = 1f / aspect   // H/B
            overlayStore.publish(); binding.gestureView.invalidate()
        } }

        binding.gestureView.store = overlayStore
        binding.gestureView.onSelectionChanged = { sel -> updateOverlayButtons(sel) }
        binding.addVideoButton.setOnClickListener {
            hintFileBrowser()
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
        binding.settingsButton.setOnClickListener { showSettings() }
        pro.connect()
        if (!prefs.getBoolean("tips_shown", false)) { main.postDelayed({ showFirstRunTips() }, 1200) }
        bgExecutor.execute { try { drafts.pruneAuto(prefs.getInt("auto_backups", 3)) } catch (_: Exception) {} }
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
            if (mosaic.selected >= 0) { hintFileBrowser(); pickTileVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
        }
        binding.tileVideoButton.setOnLongClickListener { if (mosaic.selected >= 0) openVideoDocument(VideoTarget.TILE); true }
        binding.addVideoButton.setOnLongClickListener { openVideoDocument(VideoTarget.OVERLAY); true }
        binding.greenscreenButton.setOnLongClickListener {
            if (!greenscreenActive && !mosaicActive && activeRecording == null) openVideoDocument(VideoTarget.BACKGROUND); true
        }
        binding.tileSoundButton.setOnClickListener {
            mosaic.tiles.getOrNull(mosaic.selected)?.video?.let { showVolumeDialog(it) }
        }
        binding.gestureView.mosaic = mosaic
        binding.gestureView.onMosaicChanged = { publishMosaic() }
        binding.gestureView.onMosaicTileSelected = { updateTileButtons() }
        binding.gestureView.onVideoTap = { vo, tileIdx, bg -> onVideoTapped(vo, tileIdx, bg) }
        binding.shapeButton.setOnClickListener {
            ((overlayStore.selected() as? de.codinix.videoeditor.overlay.CameraOverlay) ?: overlayStore.cameraOverlay())?.let { c ->
                c.shape = (c.shape + 1) % 3
                overlayStore.publish(); binding.gestureView.invalidate(); updateOverlayButtons(c); persistSession()
                Toast.makeText(this, when (c.shape) {
                    de.codinix.videoeditor.overlay.CameraOverlay.SHAPE_SQUARE -> R.string.shape_square
                    de.codinix.videoeditor.overlay.CameraOverlay.SHAPE_PORTRAIT -> R.string.shape_portrait
                    else -> R.string.shape_circle }, Toast.LENGTH_SHORT).show()
            }
        }
        binding.borderButton.setOnClickListener {
            ((overlayStore.selected() as? de.codinix.videoeditor.overlay.CameraOverlay) ?: overlayStore.cameraOverlay())?.let { c ->
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
            onVideoPresenceChanged()
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
        binding.review.reviewTextButton.setOnClickListener { showReviewTextDialog(null) }
        binding.review.reviewSoundButton.setOnClickListener { showReviewSoundDialog() }
        binding.review.captionView.reviewTexts = reviewTexts
        binding.review.captionView.onReviewTextChanged = {
            reviewTexts.lastOrNull()?.let { t ->
                prefs.edit().putFloat("review_text_cx", t.cx).putFloat("review_text_cy", t.cy)
                    .putFloat("text_default_width", t.widthFrac).putFloat("review_text_rot", t.rotationDeg).apply()
            }
            persistSession()
        }
        binding.review.captionView.onReviewTextEdit = { t -> showReviewTextDialog(t) }
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
        lateinit var dlg: android.app.Dialog
        val list = radioList(labels.toList(), current) { which ->
            preferredQuality = supportedQualities[which]
            bindCamera()
            dlg.dismiss()
        }
        dlg = Sheet(this).setTitle(R.string.quality_title).setView(list).show()
    }

    /** Auswahlliste im Karten-Stil: Zeile mit Text, rechts roter Auswahlpunkt. */
    private fun radioList(labels: List<String>, selected: Int, onPick: (Int) -> Unit): android.view.View {
        val dp = resources.displayMetrics.density
        val col = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        labels.forEachIndexed { i, label ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
                addView(android.widget.TextView(this@MainActivity).apply { text = label; textSize = 16f; setTextColor(0xFFFFFFFF.toInt()) },
                    android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(com.google.android.material.radiobutton.MaterialRadioButton(this@MainActivity).apply {
                    isChecked = i == selected; isClickable = false
                    buttonTintList = android.content.res.ColorStateList.valueOf(if (i == selected) Sheet.ACCENT else 0xFF8A8B96.toInt())
                })
                setOnClickListener { onPick(i) }
            }
            col.addView(row)
            if (i < labels.lastIndex) col.addView(android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1); setBackgroundColor(0x22FFFFFF)
            })
        }
        return col
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
                    overlayStore.videoOverlay()?.let { o -> if (o.playing) overlayPlayer?.let { resumeAt(it, o.sourcePositionAt(currentTotalMs())) } }
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
            var free = freeMb
            var dropped = 0
            while (free < neededMb && drafts.dropOldestAuto()) { dropped++; free = android.os.StatFs(cacheDir.absolutePath).availableBytes / (1024 * 1024) }
            if (dropped > 0) Toast.makeText(this, getString(R.string.backup_dropped, dropped), Toast.LENGTH_LONG).show()
            if (free < neededMb) Toast.makeText(this, getString(R.string.low_space, free / 1024.0), Toast.LENGTH_LONG).show()
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
            segments.add(Segment(file, durationMs, micGain))
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
            overlay.createdAtMs = currentTotalMs()
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

    /** Zuletzt gewählten Textstil als Standard für den nächsten Text merken. */
    private fun rememberTextStyle(color: Int, bg: Int?) {
        val e = prefs.edit().putInt("text_default_color", color)
        if (bg != null) e.putInt("text_default_bg", bg) else e.remove("text_default_bg")
        e.apply()
    }

    /** Text-Overlay anlegen (existing == null) oder bearbeiten. */
    private fun showTextDialog(existing: TextOverlay?) {
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()

        val defColor = prefs.getInt("text_default_color", android.graphics.Color.WHITE)
        val defBg = if (prefs.contains("text_default_bg")) prefs.getInt("text_default_bg", 0) else null
        var textRgb = (existing?.colorArgb ?: defColor) or 0xFF000000.toInt()
        var textAlpha = existing?.let { android.graphics.Color.alpha(it.colorArgb) } ?: android.graphics.Color.alpha(defColor)
        var bgOn = if (existing != null) existing.bgColorArgb != null else defBg != null
        var bgRgb = (existing?.bgColorArgb ?: defBg ?: android.graphics.Color.BLACK) or 0xFF000000.toInt()
        var bgAlpha = existing?.bgColorArgb?.let { android.graphics.Color.alpha(it) } ?: defBg?.let { android.graphics.Color.alpha(it) } ?: 200

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

        val sec = Sections(this)
        sec.section(getString(R.string.text_dialog_title))
        sec.custom(input)
        sec.section(getString(R.string.text_color))
        sec.custom(swatchGrid({ textRgb }) { textRgb = it })
        sec.slider(getString(R.string.text_opacity), textAlpha * 100 / 255, onChange = { textAlpha = it * 255 / 100; refreshPreview() })
        sec.section(getString(R.string.text_background))
        val bgCard = Sections(this)
        bgCard.section(null)
        bgCard.custom(swatchGrid({ bgRgb }) { bgRgb = it })
        bgCard.slider(getString(R.string.bg_opacity), bgAlpha * 100 / 255, onChange = { bgAlpha = it * 255 / 100; refreshPreview() })
        bgCard.root.visibility = if (bgOn) android.view.View.VISIBLE else android.view.View.GONE
        sec.switch(getString(R.string.text_background), bgOn) { on -> bgOn = on; bgCard.root.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE; refreshPreview() }
        sec.root.addView(bgCard.root)
        val box = sec.root
        refreshPreview()
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE or android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { hideKeyboard(); true } else false
        }

        val textDialog = Sheet(this)
            .setTitle(R.string.text_dialog_title)
            .setSections(sec)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                if (existing == null) {
                    val o = TextOverlay(Overlay.newId(), text, textColor(), bgColor(), widthFrac = prefs.getFloat("text_default_width", 0.6f))
                    o.createdAtMs = currentTotalMs()
                    overlayStore.add(o)
                    rememberTextStyle(textColor(), bgColor())
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

    // ---------------------------------------------------------------- Einstellungen & Tipps

    private var proSheet: android.app.Dialog? = null
    private fun onProChanged() {
        // Offenes Pro-Fenster aktualisieren (Preise geladen / Kauf abgeschlossen)
        if (proSheet?.isShowing == true) { proSheet?.dismiss(); showProSheet() }
        if (isPro && proJustBought) { proJustBought = false; Toast.makeText(this, R.string.pro_thanks, Toast.LENGTH_LONG).show() }
    }
    private var proJustBought = false

    /** Pro-Seite: Vorteile, zwei Preiskarten (Jahr hervorgehoben), Wiederherstellen. */
    fun showProSheet() {
        val dp = resources.displayMetrics.density; val pad = (16 * dp).toInt()
        val box = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        fun text(t: CharSequence, size: Float, color: Int, bold: Boolean = false, center: Boolean = false) = android.widget.TextView(this).apply {
            this.text = t; textSize = size; setTextColor(color); if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            if (center) gravity = android.view.Gravity.CENTER
        }
        if (isPro) {
            box.addView(text(getString(R.string.pro_active_title), 20f, 0xFFFFFFFF.toInt(), bold = true, center = true).apply { setPadding(0, pad, 0, pad / 2) })
            box.addView(text(getString(R.string.pro_active_text), 14f, 0xFFB7CFCB.toInt(), center = true).apply { setPadding(0, 0, 0, pad) })
            box.addView(android.widget.TextView(this).apply {
                text = getString(R.string.pro_manage); textSize = 15f; setTextColor(0xFFFFFFFF.toInt()); gravity = android.view.Gravity.CENTER
                setPadding(pad, (13 * dp).toInt(), pad, (13 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply { setColor(Sheet.CARD); cornerRadius = 12 * dp }
                setOnClickListener {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=${Pro.SUB_ID}&package=$packageName")))
                }
            })
        } else {
            // Vorteile
            val benefits = resources.getStringArray(R.array.pro_benefits)
            benefits.forEach { b ->
                box.addView(android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(0, (5 * dp).toInt(), 0, (5 * dp).toInt())
                    addView(text("👑", 15f, 0xFFE4B85A.toInt()).apply { setPadding(0, 0, (10 * dp).toInt(), 0) })
                    addView(text(b, 15f, 0xFFFFFFFF.toInt()))
                })
            }
            // Preiskarten
            val plans = pro.plans()
            val yearly = plans.firstOrNull { it.planId == Pro.PLAN_YEARLY }
            val monthly = plans.firstOrNull { it.planId == Pro.PLAN_MONTHLY }
            fun card(title: CharSequence, price: CharSequence, sub: CharSequence?, highlight: Boolean, onClick: () -> Unit) = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (highlight) 0xFF3A1F2A.toInt() else Sheet.CARD); cornerRadius = 16 * dp
                    setStroke((2 * dp).toInt(), if (highlight) Sheet.ACCENT else 0x33FFFFFF)
                }
                addView(text(title, 16f, 0xFFFFFFFF.toInt(), bold = true))
                addView(text(price, 22f, if (highlight) 0xFFE4B85A.toInt() else 0xFFFFFFFF.toInt(), bold = true).apply { setPadding(0, (4 * dp).toInt(), 0, 0) })
                sub?.let { addView(text(it, 13f, 0xFFB7CFCB.toInt()).apply { setPadding(0, (4 * dp).toInt(), 0, 0) }) }
                setOnClickListener { onClick() }
            }
            val lp = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * dp).toInt() }
            if (plans.isEmpty()) {
                box.addView(text(if (pro.lastError != null) getString(R.string.pro_prices_error) else getString(R.string.pro_prices_loading), 14f, 0xFFB7CFCB.toInt(), center = true).apply { setPadding(0, pad, 0, pad) })
            } else {
                yearly?.let { y ->
                    val sub = if (y.hasTrial) getString(R.string.pro_trial_then, y.trialDays, y.price) else getString(R.string.pro_per_year)
                    box.addView(card(getString(R.string.pro_yearly), y.price + " / " + getString(R.string.pro_year), sub, true) { proJustBought = true; if (!pro.buy(this, Pro.PLAN_YEARLY)) Toast.makeText(this, R.string.pro_prices_error, Toast.LENGTH_SHORT).show() }, lp)
                }
                monthly?.let { m ->
                    box.addView(card(getString(R.string.pro_monthly), m.price + " / " + getString(R.string.pro_month), getString(R.string.pro_cancel_anytime), false) { proJustBought = true; if (!pro.buy(this, Pro.PLAN_MONTHLY)) Toast.makeText(this, R.string.pro_prices_error, Toast.LENGTH_SHORT).show() }, lp)
                }
            }
            box.addView(text(getString(R.string.pro_terms), 11.5f, 0xFF8A8B96.toInt()).apply { setPadding(0, pad, 0, 0) })
            box.addView(android.widget.TextView(this).apply {
                text = getString(R.string.pro_restore); textSize = 14f; setTextColor(0xFFE4B85A.toInt()); gravity = android.view.Gravity.CENTER
                setPadding(0, pad, 0, 0); setOnClickListener { pro.refresh(); Toast.makeText(this@MainActivity, R.string.pro_restoring, Toast.LENGTH_SHORT).show() }
            })
        }
        proSheet = Sheet(this).setTitle(if (isPro) R.string.pro_title else R.string.pro_title_upsell).setView(box).show()
    }

    private fun isProFromPlay(): Boolean = getSharedPreferences("pro_prefs", MODE_PRIVATE).getLong("pro_valid_until", 0L) > System.currentTimeMillis()

    private fun showSettings() {
        val sec = Sections(this)

        // BabaCut Pro
        sec.section("BabaCut Pro")
        sec.button(getString(if (isPro) R.string.pro_active_title else R.string.pro_title_upsell),
            getString(if (isPro) R.string.pro_active_short else R.string.pro_upsell_short)) { showProSheet() }
        if (BuildConfig.DEBUG) sec.switch("Debug: Pro simulieren", Pro.isActive(this) && !isProFromPlay()) { on -> Pro.setDebugOverride(this, on) }

        // Sprache
        val langTags = listOf("", "en", "de", "tr", "es", "fr", "fa", "ar")
        val langNames = listOf(getString(R.string.language_system), "English", "Deutsch", "Türkçe", "Español", "Français", "فارسی", "العربية")
        val currentLang = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
        sec.section(getString(R.string.settings_language))
        sec.choice(getString(R.string.settings_language), langNames, langTags.indexOfFirst { it.isNotEmpty() && currentLang.startsWith(it) }.coerceAtLeast(0)) { pos ->
            val tag = langTags[pos]
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) androidx.core.os.LocaleListCompat.getEmptyLocaleList() else androidx.core.os.LocaleListCompat.forLanguageTags(tag))
        }

        // Untertitel
        sec.section(getString(R.string.settings_captions))
        sec.switch(getString(R.string.captions_always), prefs.getBoolean("captions_auto", false)) { on -> prefs.edit().putBoolean("captions_auto", on).apply() }
        sec.choice(getString(R.string.captions_language), captionLanguages.map { it.second },
            captionLanguages.indexOfFirst { it.first == prefs.getString("captions_lang", null) }.coerceAtLeast(0)) { pos ->
            prefs.edit().putString("captions_lang", captionLanguages[pos].first).apply()
        }
        val models = de.codinix.videoeditor.whisper.ModelManager.Model.values()
        sec.choice(getString(R.string.captions_model),
            models.mapIndexed { i, it -> resources.getStringArray(R.array.model_labels)[i] + if (modelManager.isAvailable(it)) "" else " · ${it.approxMb} MB" },
            prefs.getInt("captions_model", 1)) { pos ->
            if (prefs.getInt("captions_model", 1) != pos) { prefs.edit().putInt("captions_model", pos).apply(); prefetchCaptionModel() }
        }

        // Aufnahme
        sec.section(getString(R.string.settings_recording))
        val qualities = listOf<Quality?>(null) + QUALITY_ORDER
        sec.choice(getString(R.string.settings_default_quality),
            qualities.map { q -> if (q == null) getString(R.string.settings_default_quality_highest) else label(q) },
            qualities.indexOfFirst { q -> (q?.let { label(it) }) == prefs.getString("default_quality", null) }.coerceAtLeast(0)) { pos ->
            val q = qualities[pos]
            prefs.edit().putString("default_quality", q?.let { label(it) }).apply()
            if (segments.isEmpty() && activeRecording == null) { preferredQuality = q; bindCamera() }
        }
        sec.switch(getString(R.string.preview_sound), previewSoundOn) { on -> if (on != previewSoundOn) binding.previewSoundButton.performClick() }
        val zones = resources.getStringArray(R.array.safe_zones)
        sec.choice(getString(R.string.settings_safe_zone), zones.toList(), prefs.getInt("safe_zone", 0), sub = getString(R.string.safe_zone_hint)) { pos ->
            prefs.edit().putInt("safe_zone", pos).apply()
            binding.safeZone.platform = pos; binding.review.reviewSafeZone.platform = pos
        }

        // Mosaik
        sec.section(getString(R.string.settings_mosaic))
        sec.switch(getString(R.string.mosaic_gap_white), prefs.getBoolean("mosaic_gap_white", false)) { on ->
            prefs.edit().putBoolean("mosaic_gap_white", on).apply(); mosaic.gapWhite = on; publishMosaic()
        }
        sec.switch(getString(R.string.mosaic_rainbow), prefs.getBoolean("mosaic_rainbow", false)) { on ->
            prefs.edit().putBoolean("mosaic_rainbow", on).apply(); mosaic.rainbowGaps = on; publishMosaic()
        }

        // Export-Sicherungen
        sec.section(getString(R.string.settings_backups))
        val backupCounts = listOf(0, 1, 3, 5)
        sec.choice(getString(R.string.settings_backups), backupCounts.map { if (it == 0) getString(R.string.off) else it.toString() },
            backupCounts.indexOf(prefs.getInt("auto_backups", 3)).coerceAtLeast(0), sub = getString(R.string.backups_hint)) { pos ->
            prefs.edit().putInt("auto_backups", backupCounts[pos]).apply(); drafts.pruneAuto(backupCounts[pos])
        }

        // Gesten
        sec.section(getString(R.string.settings_gestures))
        sec.note(getString(R.string.gestures_text))

        // Hilfe
        sec.section(getString(R.string.settings_help))
        sec.button(getString(R.string.settings_show_tips)) {
            prefs.edit().putBoolean("tips_shown", false).putBoolean("tip_headphones_shown", false).putInt("file_browser_hint_count", 0).apply(); showFirstRunTips()
        }
        sec.button(getString(R.string.settings_crash)) {
            val rep = CrashLog.previous(this)
            if (rep == null) Toast.makeText(this, R.string.settings_no_crash, Toast.LENGTH_SHORT).show()
            else Sheet(this).setTitle(R.string.settings_crash)
                .setView(android.widget.TextView(this).apply { text = rep; textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true); setTextColor(0xFFDDDDE2.toInt()) })
                .setPositiveButton(R.string.copy) { _, _ ->
                    getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("crash", rep))
                    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
                }.setNegativeButton(R.string.ok, null).show()
        }
        sec.button(getString(R.string.licenses_title)) { showLicenses() }
        sec.button(getString(R.string.privacy_title)) {
            Sheet(this).setTitle(R.string.privacy_title).setMessage(getString(R.string.privacy_text)).setPositiveButton(R.string.ok, null).show()
        }
        sec.note(getString(R.string.settings_version, BuildConfig.VERSION_NAME))

        Sheet(this).setTitle(R.string.settings).setTall(true, selfScrolling = false).setSections(sec).show()
    }

    /** Open-Source-Lizenzen der verwendeten Bibliotheken. */
    private fun showLicenses() {
        val text = """
BabaCut uses the following open-source software:

whisper.cpp — Copyright (c) 2023-2024 The ggml authors. MIT License.
Whisper models (ggml) — OpenAI, MIT License.
AndroidX, CameraX, Media3 (ExoPlayer, Transformer, Effect) — The Android Open Source Project, Apache License 2.0.
Material Components for Android — Google LLC, Apache License 2.0.
Kotlin Standard Library — JetBrains s.r.o., Apache License 2.0.

MIT License: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.

Apache License 2.0: Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        """.trimIndent()
        Sheet(this).setTitle(R.string.licenses_title).setTall(true, selfScrolling = false)
            .setView(android.widget.TextView(this).apply { this.text = text; textSize = 12.5f; setTextColor(0xFFDDDDE2.toInt()); setTextIsSelectable(true); setLineSpacing(0f, 1.2f) })
            .setPositiveButton(R.string.ok, null).show()
    }

    /** Drei kurze Tipp-Karten beim ersten Öffnen, nacheinander. */
    private fun showFirstRunTips() {
        prefs.edit().putBoolean("tips_shown", true).apply()
        showTip(getString(R.string.tip_record), 4500, gesture = false)
        main.postDelayed({ showTip(getString(R.string.tip_toolbar), 4500, gesture = false) }, 5000)
        main.postDelayed({ showTip(getString(R.string.tip_overlays), 5500, gesture = true) }, 10_000)
        main.postDelayed({ showTip(getString(R.string.tip_file_browser), 6000, gesture = false) }, 16_000)
    }

    /** Einmaliger Hinweis beim ersten Video im Bild; Punkt am Kopfhörer-Knopf, solange Ton nur mit Kopfhörern hörbar wäre. */
    private fun onVideoPresenceChanged() {
        val hasVideo = anyLiveVideo()
        binding.previewSoundButton.setImageResource(if (hasVideo && !previewSoundOn) R.drawable.ic_headphones_dot else R.drawable.ic_headphones)
        if (hasVideo && !prefs.getBoolean("tip_headphones_shown", false)) {
            prefs.edit().putBoolean("tip_headphones_shown", true).apply()
            showTip(getString(R.string.tip_headphones), 7000, gesture = false)
        }
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
        lateinit var dlg: android.app.Dialog
        fun render() {
            row.removeAllViews()
            resources.getStringArray(R.array.filter_names).forEachIndexed { idx, name ->
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
        dlg = Sheet(this)
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
        onVideoPresenceChanged()
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
                text = getString(R.string.off); textSize = 15f; setTextColor(if (selected) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
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
        // Links neben dem Knopf; vertikal am Knopf zentriert, aber immer komplett im Bild
        val anchor = binding.mosaicButton
        col.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        val rootLoc = IntArray(2); binding.root.getLocationOnScreen(rootLoc)
        val screenH = binding.root.height
        val x = loc[0] - col.measuredWidth - (8 * dp).toInt()
        val anchorCenterY = loc[1] - rootLoc[1] + anchor.height / 2
        val minY = (24 * dp).toInt(); val maxY = screenH - col.measuredHeight - (24 * dp).toInt()
        val y = (anchorCenterY - col.measuredHeight / 2).coerceIn(minY, maxOf(minY, maxY)) + rootLoc[1]
        popup.showAtLocation(binding.root, android.view.Gravity.NO_GRAVITY, x, y)
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
        Sheet(this)
            .setTitle(R.string.mosaic_title)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                mosaic.gapWhite = gapWhite.isChecked; mosaic.rainbowGaps = rainbow.isChecked
                publishMosaic()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTileFillDialog(t: de.codinix.videoeditor.overlay.Mosaic.Tile) {
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(pad, pad, pad, pad) }
        lateinit var dlg: android.app.Dialog
        TextRenderer.COLORS.forEach { c ->
            val size = (36 * dp).toInt()
            row.addView(android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply { setMargins(pad / 3, 0, pad / 3, 0) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c)
                    setStroke((if (c == t.fillColor) 4 else 1) * dp.toInt().coerceAtLeast(1), 0xFFFFFFFF.toInt())
                }
                setOnClickListener { t.fillColor = c; publishMosaic(); dlg.dismiss() }
            })
        }
        dlg = Sheet(this)
            .setTitle(R.string.tile_fill)
            .setView(android.widget.HorizontalScrollView(this).apply { addView(row); isHorizontalScrollBarEnabled = false })
            .setNegativeButton(R.string.cancel, null)
            .create()
        dlg.show()
    }

    /** Kurzer, halbtransparenter Hinweis oben im Bild mit Gesten-Animation, verschwindet nach [ms]. */
    private fun showTip(text: String, ms: Long = 5000, gesture: Boolean = true) {
        val card = binding.tipCard
        binding.tipText.text = text
        binding.tipGesture.visibility = if (gesture) android.view.View.VISIBLE else android.view.View.GONE
        card.alpha = 0f; card.visibility = android.view.View.VISIBLE
        card.animate().alpha(1f).setDuration(250).start()
        main.removeCallbacks(hideTip)
        main.postDelayed(hideTip, ms)
    }
    private val hideTip = Runnable {
        binding.tipCard.animate().alpha(0f).setDuration(400).withEndAction { binding.tipCard.visibility = android.view.View.GONE }.start()
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
        onVideoPresenceChanged()
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
        val total = currentTotalMs()
        tileVideos().forEach { v ->
            tilePlayers[v.id]?.let { p ->
                if (playing && v.playing) resumeAt(p, v.sourcePositionAt(total)) else p.pause()
            }
        }
    }

    /** Player sicher weiterlaufen lassen: ggf. neu vorbereiten, an die Protokollstelle springen, starten. */
    private fun resumeAt(p: ExoPlayer, positionMs: Long) {
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        if (kotlin.math.abs(p.currentPosition - positionMs) > 400) p.seekTo(positionMs)
        p.play()
    }

    private fun syncTilePlayers() {
        val total = currentTotalMs()
        tileVideos().forEach { v -> tilePlayers[v.id]?.let { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.seekTo(v.sourcePositionAt(total)) } }
    }

    private fun resetMosaic() {
        tilePlayers.values.forEach { it.release() }; tilePlayers.clear()
        mosaic.tiles.forEach { t -> t.video?.let { compositor.releaseVideoLayer(it.id) } }
        mosaic = de.codinix.videoeditor.overlay.Mosaic(de.codinix.videoeditor.overlay.Mosaic.LAYOUT_NONE).apply {
            gapWhite = prefs.getBoolean("mosaic_gap_white", false); rainbowGaps = prefs.getBoolean("mosaic_rainbow", false)
        }
        binding.tileVideoButton.setOnClickListener {
            if (mosaic.selected >= 0) { hintFileBrowser(); pickTileVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
        }
        binding.tileVideoButton.setOnLongClickListener { if (mosaic.selected >= 0) openVideoDocument(VideoTarget.TILE); true }
        binding.addVideoButton.setOnLongClickListener { openVideoDocument(VideoTarget.OVERLAY); true }
        binding.greenscreenButton.setOnLongClickListener {
            if (!greenscreenActive && !mosaicActive && activeRecording == null) openVideoDocument(VideoTarget.BACKGROUND); true
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
        if (mosaicActive) { Toast.makeText(this, R.string.mosaic_first_off, Toast.LENGTH_SHORT).show(); return }
        val bg = overlayStore.videoOverlay()?.takeIf { it.isBackground }
        if (bg == null) {
            hintFileBrowser()
            pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } else {
            Sheet(this)
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
            // Kachel-Modus ist für Reaktionen gedacht: automatisch Frontkamera
            if (lensFacing != CameraSelector.LENS_FACING_FRONT && activeRecording == null) {
                lensFacing = CameraSelector.LENS_FACING_FRONT
                bindCamera()
            }
            showTip(getString(R.string.greenscreen_on), 6000, gesture = false)
        }
        if (!on) overlayStore.cameraOverlay()?.let { overlayStore.remove(it.id) }
        binding.gestureView.invalidate()
        updateOverlayButtons(overlayStore.selected())
    }

    private fun updateOverlayButtons(sel: Overlay?) {
        if (mosaicActive && mosaic.selected >= 0) return   // Kachel hat Vorrang, siehe updateTileButtons
        binding.removeOverlayButton.visibility = if (sel != null) android.view.View.VISIBLE else android.view.View.GONE
        binding.editTextButton.visibility = if (sel is TextOverlay) android.view.View.VISIBLE else android.view.View.GONE
        val cam = (sel as? de.codinix.videoeditor.overlay.CameraOverlay) ?: overlayStore.cameraOverlay()
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
        if (asBackground && mosaicActive) { Toast.makeText(this, R.string.mosaic_first_off, Toast.LENGTH_SHORT).show(); return }
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
                    onVideoPresenceChanged()
                    if (asBackground) {
                        overlayStore.selectedId = null
                        updateOverlayButtons(null)
                        applyGreenscreenState()
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
        Sheet(this)
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
        Sheet(this)
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
        Sheet(this)
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
    /** Tipp auf ein Video: nur dieses Video umschalten, Symbol in seiner Mitte einblenden. */
    private fun onVideoTapped(vo: VideoOverlay?, tileIdx: Int, background: Boolean) {
        val video: VideoOverlay = vo ?: (if (tileIdx >= 0) mosaic.tiles.getOrNull(tileIdx)?.video else null)
            ?: (if (background) overlayStore.videoOverlay()?.takeIf { it.isBackground } else null) ?: return
        val now = currentTotalMs()
        val playing = !video.playing
        video.addEvent(now, video.volume, playing)
        val player = if (vo != null || background) overlayPlayer else tilePlayers[video.id]
        if (activeRecording != null) { if (playing) player?.play() else player?.pause() }
        val rect = binding.gestureView.rectOf(if (background) null else vo, if (vo == null && !background) tileIdx else -1)
        binding.gestureView.showPlayIcon(rect, playing)
        persistSession()
        refreshUi()
    }

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

    private val captionLanguages by lazy { listOf(
        null to getString(R.string.captions_auto), "de" to "Deutsch", "en" to "English", "tr" to "Türkçe",
        "fr" to "Français", "es" to "Español", "it" to "Italiano", "ru" to "Русский", "ar" to "العربية", "pl" to "Polski"
    ) }

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
        val dp = resources.displayMetrics.density; val pad = (16 * dp).toInt()
        val sec = Sections(this)

        // Erkennung
        var langIdx = captionLanguages.indexOfFirst { it.first == prefs.getString("captions_lang", null) }.coerceAtLeast(0)
        var modelIdx = prefs.getInt("captions_model", 1)
        val models = de.codinix.videoeditor.whisper.ModelManager.Model.values()
        sec.section(getString(R.string.captions_title))
        sec.choice(getString(R.string.captions_language), captionLanguages.map { it.second }, langIdx) { langIdx = it }
        sec.choice(getString(R.string.captions_model),
            models.mapIndexed { i, it -> resources.getStringArray(R.array.model_labels)[i] + if (modelManager.isAvailable(it)) "" else " · ${it.approxMb} MB" },
            modelIdx) { modelIdx = it }
        sec.switch(getString(R.string.captions_always), prefs.getBoolean("captions_auto", false)) { on -> prefs.edit().putBoolean("captions_auto", on).apply() }

        // Stil
        sec.section(getString(R.string.captions_template))
        val cardW = (150 * dp).toInt(); val cardH = (84 * dp).toInt()
        val cardRow = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        fun renderCards() {
            cardRow.removeAllViews()
            resources.getStringArray(R.array.caption_templates).forEachIndexed { idx, name ->
                val img = android.widget.ImageView(this).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
                    setImageBitmap(de.codinix.videoeditor.whisper.CaptionStyle.preview(idx, captionSettings.accentColor, 720, 720, getString(R.string.caption_preview_words)))
                }
                val label = android.widget.TextView(this).apply { text = name; textSize = 11f; gravity = android.view.Gravity.CENTER; setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 0, 0, pad / 3) }
                val card = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(cardW, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(pad / 4, 0, pad / 4, 0) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 10 * dp; setColor(0xFF1C1D26.toInt())
                        setStroke((2.5f * dp).toInt(), if (idx == captionSettings.template) captionSettings.accentColor else 0x00000000)
                    }
                    addView(img, android.widget.LinearLayout.LayoutParams(cardW, cardH)); addView(label)
                    setOnClickListener {
                        captionSettings.template = idx
                        binding.review.captionView.settings = captionSettings
                        saveDefaultCaptionSettings(); persistSession(); renderCards()
                    }
                }
                cardRow.addView(card)
            }
        }
        renderCards()
        sec.custom(android.widget.HorizontalScrollView(this).apply { addView(cardRow); isHorizontalScrollBarEnabled = false; setPadding(0, pad / 2, 0, pad / 2) })
        val accentRow = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL; setPadding(0, pad / 2, 0, pad / 2) }
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
                    saveDefaultCaptionSettings(); persistSession(); renderCards()
                    for (i in 0 until accentRow.childCount) {
                        (accentRow.getChildAt(i).background as android.graphics.drawable.GradientDrawable)
                            .setStroke((if (de.codinix.videoeditor.whisper.CaptionStyle.ACCENT_COLORS[i] == c) 4 else 1) * dp.toInt().coerceAtLeast(1), 0xFFFFFFFF.toInt())
                    }
                }
            })
        }
        sec.custom(android.widget.HorizontalScrollView(this).apply { addView(accentRow); isHorizontalScrollBarEnabled = false })
        sec.switch(getString(R.string.captions_emojis), captionSettings.emojis) { on ->
            captionSettings.emojis = on; binding.review.captionView.settings = captionSettings; saveDefaultCaptionSettings(); persistSession()
        }
        sec.note(getString(R.string.captions_export_note))

        lateinit var dlg: android.app.Dialog
        if (captions.isNotEmpty()) {
            sec.section(null)
            sec.button(getString(R.string.captions_edit)) { dlg.dismiss(); showCaptionEditor(-1) }
            sec.button(getString(R.string.captions_remove), destructive = true) { captions.clear(); refreshCaptionUi(); persistSession(); dlg.dismiss() }
        }
        dlg = Sheet(this)
            .setTitle(R.string.captions)
            .setTall(true, selfScrolling = false)
            .setSections(sec)
            .setPositiveButton(if (captions.isEmpty()) R.string.captions_generate else R.string.captions_regenerate) { _, _ ->
                val lang = captionLanguages[langIdx].first
                prefs.edit().putString("captions_lang", lang).putInt("captions_model", modelIdx).apply()
                startTranscription(lang)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
                    text = getString(R.string.seg_label, segNo) + " · " + getString(R.string.start_label, t(c.startMs)); textSize = 12f; alpha = 0.85f
                    setOnClickListener { seekTo(c.startMs) }
                }
                val endLabel = android.widget.TextView(this).apply {
                    text = getString(R.string.end_label, t(c.endMs)); textSize = 12f; alpha = 0.85f
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
        val dlg = Sheet(this)
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
                val audioCheck = de.codinix.videoeditor.whisper.AudioPrep.lastDiagnostics
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
                    if (BuildConfig.DEBUG && audioCheck.isNotEmpty()) main.postDelayed({ Toast.makeText(this, "Ton-Kontrolle: $audioCheck", Toast.LENGTH_LONG).show() }, 3500)
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

    // ---------------------------------------------------------------- Ton in der Review

    /** Lautstärke [gain] für den Bereich [fromMs, toMs) in ein Protokoll schreiben; außerhalb bleibt alles. */
    private fun applyGainRange(events: List<VideoOverlay.Event>, fromMs: Long, toMs: Long, gain: Float): List<VideoOverlay.Event> {
        fun stateAt(t: Long) = events.lastOrNull { it.atMs <= t }
        val before = events.filter { it.atMs < fromMs }
        val inside = events.filter { it.atMs >= fromMs && it.atMs < toMs }.map { it.copy(gain = gain) }
        val after = events.filter { it.atMs >= toMs }
        val out = ArrayList<VideoOverlay.Event>(before)
        stateAt(fromMs)?.let { st -> if (inside.none { it.atMs == fromMs }) out.add(VideoOverlay.Event(fromMs, gain, st.playing)) }
        out.addAll(inside)
        stateAt(toMs)?.let { st -> if (after.none { it.atMs == toMs }) out.add(VideoOverlay.Event(toMs, st.gain, st.playing)) }
        out.addAll(after)
        return out.sortedBy { it.atMs }
    }

    private fun gainAt(events: List<VideoOverlay.Event>, t: Long, fallback: Float) = events.lastOrNull { it.atMs <= t }?.gain ?: fallback

    /** Ton-Übersicht der Review: Mikrofon und alle Overlay-Spuren, je Segment oder fürs ganze Video. */
    private fun showReviewSoundDialog() {
        val p = player ?: return
        if (segments.isEmpty()) return
        p.pause()
        val dp = resources.displayMetrics.density; val pad = (16 * dp).toInt()
        val segIdx = p.currentMediaItemIndex.coerceIn(0, segments.lastIndex)
        var segStart = 0L; for (i in 0 until segIdx) segStart += segments[i].durationMs
        val segEnd = segStart + segments[segIdx].durationMs
        val total = segments.sumOf { it.durationMs }
        var wholeVideo = false

        val sec = Sections(this)
        sec.section(getString(R.string.sound_scope_segment, segIdx + 1))
        sec.switch(getString(R.string.sound_scope_whole), false) { on -> wholeVideo = on }
        sec.section(getString(R.string.review_sound))

        fun rangeFrom() = if (wholeVideo) 0L else segStart
        fun rangeTo() = if (wholeVideo) total else segEnd
        var dirty = false
        fun row(label: String, initial: Int, onApply: (Float) -> Unit) {
            sec.slider(label, initial, max = 200, onChange = {}, onRelease = { v -> onApply(v / 100f); dirty = true })
        }

        // Mikrofon
        row(getString(R.string.tile_mic_volume), (segments[segIdx].micGain * 100).toInt()) { g ->
            if (wholeVideo) segments.forEach { it.micGain = g } else segments[segIdx].micGain = g
            p.volume = Loudness.gain(segments[p.currentMediaItemIndex.coerceIn(0, segments.lastIndex)].micGain).coerceIn(0f, 1f)
            persistSession()
        }
        // Aktuelles Overlay / Hintergrund
        overlayStore.videoOverlay()?.let { v ->
            val name = if (v.isBackground) getString(R.string.tile_bg_volume) else getString(R.string.sound_track_overlay)
            row(name, (gainAt(v.timeline(), segStart, v.volume) * 100).toInt()) { g ->
                val ev = applyGainRange(v.timeline(), rangeFrom(), rangeTo(), g)
                v.events.clear(); v.events.addAll(ev); if (wholeVideo) v.volume = g
            }
        }
        // Kachelvideos
        tileVideos().forEachIndexed { i, v ->
            row(getString(R.string.sound_track_tile, i + 1), (gainAt(v.timeline(), segStart, v.volume) * 100).toInt()) { g ->
                val ev = applyGainRange(v.timeline(), rangeFrom(), rangeTo(), g)
                v.events.clear(); v.events.addAll(ev); if (wholeVideo) v.volume = g
            }
        }
        // Historie (entfernte Videos)
        audioHistory.forEachIndexed { i, e ->
            if (e.startOffsetMs >= total) return@forEachIndexed
            val base = e.timeline.takeIf { it.isNotEmpty() }?.map { VideoOverlay.Event(it.fromMs, it.gain, it.playing, it.seekMs) }
                ?: listOf(VideoOverlay.Event(e.startOffsetMs, e.volume, true))
            row(getString(R.string.sound_track_removed, i + 1), (gainAt(base, segStart, e.volume) * 100).toInt()) { g ->
                val ev = applyGainRange(base, rangeFrom(), rangeTo(), g)
                val idx = audioHistory.indexOf(e)
                if (idx >= 0) audioHistory[idx] = e.copy(timeline = ev.map { OverlayAudioRenderer.Segment(it.atMs, it.gain, it.playing, it.seekMs) })
            }
        }
        sec.note(getString(R.string.tile_volume_hint))

        fun finish() {
            if (dirty && allAudioMixes().isNotEmpty()) {
                Toast.makeText(this, R.string.preparing_audio, Toast.LENGTH_SHORT).show()
                buildReviewOverlayPlayer()
            } else p.play()
            if (dirty) persistSession()
        }
        Sheet(this)
            .setTitle(R.string.review_sound)
            .setSections(sec)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    /** Text über das ganze Video (Review). existing == null: neu. */
    private fun showReviewTextDialog(existing: de.codinix.videoeditor.whisper.ReviewText?) {
        player?.pause()
        val dp = resources.displayMetrics.density; val pad = (16 * dp).toInt()
        val defColor = prefs.getInt("text_default_color", android.graphics.Color.WHITE)
        val defBg = if (prefs.contains("text_default_bg")) prefs.getInt("text_default_bg", 0) else null
        var textRgb = (existing?.colorArgb ?: defColor) or 0xFF000000.toInt()
        var textAlpha = existing?.let { android.graphics.Color.alpha(it.colorArgb) } ?: android.graphics.Color.alpha(defColor)
        var bgOn = if (existing != null) existing.bgColorArgb != null else defBg != null
        var bgRgb = (existing?.bgColorArgb ?: defBg ?: android.graphics.Color.BLACK) or 0xFF000000.toInt()
        var bgAlpha = existing?.bgColorArgb?.let { android.graphics.Color.alpha(it) } ?: defBg?.let { android.graphics.Color.alpha(it) } ?: 200
        fun textColor() = (textRgb and 0x00FFFFFF) or (textAlpha shl 24)
        fun bgColor(): Int? = if (bgOn) (bgRgb and 0x00FFFFFF) or (bgAlpha shl 24) else null
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.text_hint); setText(existing?.text ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2; maxLines = 5; gravity = android.view.Gravity.CENTER; textSize = 22f; setPadding(pad, pad, pad, pad)
        }
        val previewBg = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 12 * dp }
        fun refreshPreview() { input.setTextColor(textColor()); previewBg.setColor(bgColor() ?: 0x22888888); input.background = previewBg }
        fun hideKeyboard() { getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0) }
        fun label(res: Int) = android.widget.TextView(this).apply { text = getString(res); textSize = 13f; setPadding(0, pad, 0, pad / 4) }
        fun swatches(selected: () -> Int, onPick: (Int) -> Unit): android.view.View {
            val row = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
            val views = mutableListOf<Pair<Int, android.view.View>>()
            fun refresh() { views.forEach { (c, v) -> v.scaleX = if (c == (selected() or 0xFF000000.toInt())) 1.2f else 1f; v.scaleY = v.scaleX } }
            TextRenderer.COLORS.forEach { c ->
                val size = (34 * dp).toInt()
                val v = android.view.View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply { setMargins(pad / 3, pad / 4, pad / 3, pad / 4) }
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c); setStroke(3, 0xFF888888.toInt()) }
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
        val sec = Sections(this)
        sec.section(getString(R.string.text_dialog_title))
        sec.custom(input)
        sec.section(getString(R.string.text_color))
        sec.custom(swatches({ textRgb }) { textRgb = it })
        sec.slider(getString(R.string.text_opacity), textAlpha * 100 / 255, onChange = { textAlpha = it * 255 / 100; refreshPreview() })
        sec.section(getString(R.string.text_background))
        val bgCard = Sections(this)
        bgCard.section(null)
        bgCard.custom(swatches({ bgRgb }) { bgRgb = it })
        bgCard.slider(getString(R.string.bg_opacity), bgAlpha * 100 / 255, onChange = { bgAlpha = it * 255 / 100; refreshPreview() })
        bgCard.root.visibility = if (bgOn) android.view.View.VISIBLE else android.view.View.GONE
        sec.switch(getString(R.string.text_background), bgOn) { on -> bgOn = on; bgCard.root.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE; refreshPreview() }
        sec.root.addView(bgCard.root)
        val box = sec.root
        refreshPreview()
        val b = Sheet(this)
            .setTitle(R.string.text_dialog_title)
            .setSections(sec)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    if (existing == null) reviewTexts.add(de.codinix.videoeditor.whisper.ReviewText(System.nanoTime(), text, textColor(), bgColor(),
                        cx = prefs.getFloat("review_text_cx", 0.5f), cy = prefs.getFloat("review_text_cy", 0.5f),
                        widthFrac = prefs.getFloat("text_default_width", 0.6f), rotationDeg = prefs.getFloat("review_text_rot", 0f)))
                    else { existing.text = text; existing.colorArgb = textColor(); existing.bgColorArgb = bgColor(); existing.rerender() }
                    rememberTextStyle(textColor(), bgColor())
                    binding.review.captionView.invalidate(); persistSession()
                }
                if (inReview) player?.play()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> if (inReview) player?.play() }
            .setOnCancelListener { if (inReview) player?.play() }
        if (existing != null) b.setNeutralButton(R.string.delete) { _, _ ->
            reviewTexts.remove(existing); binding.review.captionView.invalidate(); persistSession(); if (inReview) player?.play()
        }
        val dlg = b.create()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dlg.show(); input.requestFocus()
    }

    /** Bild-/Text-Overlays, die nach Aufnahmebeginn hinzukamen: für den Zeitraum davor nachträglich auflegen. */
    private fun postOverlaySpecs(): List<de.codinix.videoeditor.whisper.PostOverlaySpec> = overlayStore.items.mapNotNull { o ->
        val bmp = when (o) { is ImageOverlay -> o.bitmap; is TextOverlay -> o.bitmap; else -> null } ?: return@mapNotNull null
        if (o.createdAtMs <= 0L) return@mapNotNull null
        de.codinix.videoeditor.whisper.PostOverlaySpec(bmp, o.cx, o.cy, o.widthFrac, o.rotationDeg, o.createdAtMs)
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
        // Mikrofon-Lautstärke je Segment (Anhebung über 100 % kann ein Player nicht)
        fun applyMicVolume() { p.volume = Loudness.gain(segments.getOrNull(p.currentMediaItemIndex)?.micGain ?: 1f).coerceIn(0f, 1f) }
        applyMicVolume()
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { applyMicVolume() }
        })
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
        val needsReencode = allAudioMixes().isNotEmpty() || captions.isNotEmpty() || reviewTexts.isNotEmpty() || segments.any { kotlin.math.abs(it.micGain - 1f) >= 0.01f }
        fun sizeText(bytes: Double) = if (bytes >= 1e9) "≈ %.1f GB".format(Locale.getDefault(), bytes / 1e9) else "≈ %.0f MB".format(Locale.getDefault(), bytes / 1e6)
        fun estimate(height: Int) = (Exporter.videoBitrateFor(height) + Exporter.AUDIO_BITRATE) / 8.0 * totalSec
        val originalSize = if (needsReencode) estimate(recordedHeight) else segments.sumOf { it.file.length() }.toDouble()
        val options = mutableListOf<Pair<String, Int?>>((getString(R.string.export_original) + "  " + sizeText(originalSize)) to null)
        listOf(2160 to "4K (2160p)", 1440 to "2K (1440p)", 1080 to "1080p", 720 to "720p", 480 to "480p")
            .filter { it.first < recordedHeight }
            .forEach { options.add((it.second + "  " + sizeText(estimate(it.first))) to it.first) }

        val pad = (20 * resources.displayMetrics.density).toInt()
        var selectedIdx = 0
        val sec = Sections(this)
        sec.section(getString(R.string.export_resolution))
        val listView = android.widget.FrameLayout(this)
        fun renderList() {
            listView.removeAllViews()
            listView.addView(Sections.radioList(this, options.map { it.first }, selectedIdx) { i -> selectedIdx = i; renderList() })
        }
        renderList()
        sec.custom(listView)
        sec.note(getString(R.string.captions_export_note).takeIf { captions.isNotEmpty() } ?: getString(R.string.export_size_note))
        Sheet(this)
            .setTitle(R.string.export_title)
            .setSections(sec)
            .setPositiveButton(R.string.save) { _, _ ->
                val idx = selectedIdx
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
        ex.postOverlays = reviewTexts.map { it.spec() }
        ex.micGains = segments.map { it.micGain }
        exporter = ex
        val audioMix = allAudioMixes()
        val progressRes = if (audioMix.isEmpty() && segments.all { kotlin.math.abs(it.micGain - 1f) < 0.01f })
            R.string.export_running else R.string.export_running_mix
        ex.export(segments.map { it.file }, targetHeight, object : Exporter.Listener {
            override fun onProgress(percent: Int) {
                dialog.setMessage(getString(progressRes, percent))
            }
            override fun onDone(uri: Uri) {
                dialog.dismiss()
                ex.release(); exporter = null
                val keep = prefs.getInt("auto_backups", 3)
                val backedUp = keep > 0 && saveProjectAsDraft(auto = true)
                if (backedUp) {
                    drafts.pruneAuto(keep)
                } else {
                    segments.forEach { it.file.delete() }
                    segments.clear()
                    audioHistory.forEach { it.file.delete() }
                    audioHistory.clear()
                    captions.clear()
                    reviewTexts.clear()
                    captionSettings = loadDefaultCaptionSettings()
                    clearOverlays()
                    resetMosaic()
                }
                setControlsEnabled(true)
                if (inReview) exitReview() else refreshUi()
                Sheet(this@MainActivity)
                    .setTitle(R.string.saved_title)
                    .setMessage(getString(R.string.saved_msg) + if (backedUp) "\n\n" + getString(R.string.backup_kept) else "")
                    .setPositiveButton(R.string.share) { _, _ -> shareVideo(uri) }
                    .setNegativeButton(R.string.ok, null)
                    .show()
            }
            override fun onError(message: String) {
                dialog.dismiss()
                ex.release(); exporter = null
                setControlsEnabled(true)
                if (inReview) { buildPlayer(); main.post(playbackTicker) }
                Sheet(this@MainActivity)
                    .setTitle(getString(R.string.error, ""))
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
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
        Sheet(this)
            .setTitle(R.string.discard_title)
            .setMessage(getString(R.string.discard_msg, segments.size))
            .setPositiveButton(R.string.discard) { _, _ ->
                activeRecording?.stop(); activeRecording = null
                segments.forEach { it.file.delete() }
                segments.clear()
                audioHistory.forEach { it.file.delete() }
                audioHistory.clear()
                captions.clear()
                reviewTexts.clear()
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
            segments.forEach { segs.put(org.json.JSONObject().put("path", it.file.absolutePath).put("durationMs", it.durationMs).put("micGain", it.micGain.toDouble())) }
            val ovs = org.json.JSONArray()
            overlayStore.items.forEach { o ->
                val j = org.json.JSONObject()
                    .put("cx", o.cx.toDouble()).put("cy", o.cy.toDouble())
                    .put("widthFrac", o.widthFrac.toDouble()).put("rotationDeg", o.rotationDeg.toDouble())
                    .put("createdAtMs", o.createdAtMs)
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
                .put("reviewTexts", de.codinix.videoeditor.whisper.ReviewText.listToJson(reviewTexts))
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
        Sheet(this)
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
        if (saveProjectAsDraft(auto = false)) {
            Toast.makeText(this, R.string.draft_saved, Toast.LENGTH_SHORT).show()
            if (inReview) exitReview() else refreshUi()
        }
    }

    /**
     * Projekt vollständig in einen Entwurf verschieben (Segmente, Overlays, Ton, Untertitel, Mosaik,
     * Review-Texte) und den Arbeitszustand leeren. [auto] = Export-Sicherung.
     */
    private fun saveProjectAsDraft(auto: Boolean): Boolean {
        try {
            drafts.save(
                segments.map { it.file to it.durationMs },
                overlayStore.items.toList(),
                lensFacing,
                preferredQuality?.let { label(it) },
                audioHistory.map { DraftStore.AudioTrack(it.file, it.startOffsetMs, it.endOffsetMs, it.volume, it.durationMs,
                    it.timeline.map { t -> VideoOverlay.Event(t.fromMs, t.gain, t.playing, t.seekMs) }) },
                captions.toList(), captionSettings,
                if (mosaicActive) mosaic else null,
                reviewTexts.toList(),
                segments.map { it.micGain },
                auto
            )
            captions.clear(); reviewTexts.clear()
            resetMosaic()
            segments.clear()
            audioHistory.clear()
            // Dateien der Video-Overlays wurden in den Entwurf verschoben – nur Player/Textur freigeben
            overlayStore.items.filterIsInstance<VideoOverlay>().forEach {
                overlayPlayer?.release(); overlayPlayer = null
                compositor.releaseVideoLayer(it.id)
            }
            overlayStore.clear()
            compositor.backgroundOverlayId = 0L
            binding.greenscreenButton.setBackgroundResource(R.drawable.bg_round_button)
            updateOverlayButtons(null)
            captionSettings = loadDefaultCaptionSettings()
            clearSession()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Entwurf speichern fehlgeschlagen", e)
            Toast.makeText(this, getString(R.string.error, e.message ?: "Entwurf"), Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun showDrafts() {
        if (activeRecording != null || segments.isNotEmpty()) return
        val all = drafts.list()
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.no_drafts, Toast.LENGTH_SHORT).show(); return
        }
        val manual = all.filter { !it.auto }
        val history = all.filter { it.auto }
        val fmtDate = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val dp = resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        var current: List<DraftStore.Info> = if (manual.isNotEmpty() || history.isEmpty()) manual else history
        lateinit var dlg: android.app.Dialog

        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = current.size
            override fun getItem(i: Int) = current[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, convert: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val info = current[i]
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
                val base = resources.getQuantityString(R.plurals.segments, info.segmentCount, info.segmentCount) + " · " + fmt(info.durationMs)
                (texts.getChildAt(1) as android.widget.TextView).text = if (info.auto) {
                    val daysLeft = (7 - (System.currentTimeMillis() - info.createdAt) / 86_400_000L).coerceAtLeast(0)
                    base + " · " + String.format(Locale.getDefault(), "%.1f GB", info.sizeBytes / 1e9) + " · " + getString(R.string.backup_days_left, daysLeft)
                } else base
                return row
            }
        }
        val listView = android.widget.ListView(this).apply {
            this.adapter = adapter
            divider = null
            setOnItemClickListener { _, _, which, _ -> dlg.dismiss(); askDraftAction(current[which]) }
        }
        val empty = android.widget.TextView(this).apply {
            text = getString(R.string.history_empty); textSize = 14f; alpha = 0.7f; gravity = android.view.Gravity.CENTER
            setPadding(pad, pad * 3, pad, pad * 3); visibility = android.view.View.GONE
        }
        val tabs = com.google.android.material.tabs.TabLayout(this).apply {
            addTab(newTab().setIcon(R.drawable.ic_save).setText(R.string.drafts))
            addTab(newTab().setIcon(R.drawable.ic_history).setText(R.string.history))
            tabGravity = com.google.android.material.tabs.TabLayout.GRAVITY_FILL
            setSelectedTabIndicatorColor(Sheet.ACCENT)
            setTabTextColors(0xFF9A9BA6.toInt(), 0xFFFFFFFF.toInt())
            tabIconTint = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()), intArrayOf(0xFFFFFFFF.toInt(), 0xFF9A9BA6.toInt()))
            setBackgroundColor(0x00000000)
            addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    current = if (tab.position == 0) manual else history
                    adapter.notifyDataSetChanged()
                    empty.visibility = if (current.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                    empty.text = getString(if (tab.position == 0) R.string.no_drafts else R.string.history_empty)
                    listView.visibility = if (current.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
                }
                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            })
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(tabs)
            addView(listView, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(empty)
        }
        dlg = Sheet(this)
            .setTitle(R.string.drafts)
            .setTall(true)
            .setView(box)
            .create()
        dlg.show()
        if (current === history) tabs.selectTab(tabs.getTabAt(1)) else { empty.visibility = if (manual.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE }
    }

    private fun askDraftAction(info: DraftStore.Info) {
        Sheet(this)
            .setTitle(R.string.drafts)
            .setMessage(getString(R.string.draft_item,
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(info.createdAt),
                info.segmentCount, fmt(info.durationMs)))
            .setPositiveButton(R.string.open) { _, _ -> loadDraft(info) }
            .setNeutralButton(R.string.delete) { _, _ -> drafts.delete(info) }
            .setNegativeButton(if (info.auto) R.string.backup_keep else R.string.cancel) { _, _ ->
                if (info.auto) { drafts.keep(info); Toast.makeText(this, R.string.backup_kept_toast, Toast.LENGTH_SHORT).show() }
            }
            .show()
    }

    private fun loadDraft(info: DraftStore.Info) {
        try {
            val loaded = drafts.load(info, segmentDir)
            segments.clear()
            loaded.segments.forEachIndexed { i, (f, d) -> segments.add(Segment(f, d, loaded.micGains.getOrNull(i) ?: 1f)) }
            clearOverlays()
            loaded.overlays.forEach { overlayStore.add(it) }
            captions.clear(); captions.addAll(loaded.captions)
            reviewTexts.clear(); reviewTexts.addAll(loaded.reviewTexts)
            captionSettings = loaded.captionSettings
            mosaic = loaded.mosaic ?: de.codinix.videoeditor.overlay.Mosaic(de.codinix.videoeditor.overlay.Mosaic.LAYOUT_NONE)
            binding.tileVideoButton.setOnClickListener {
            if (mosaic.selected >= 0) { hintFileBrowser(); pickTileVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
        }
        binding.tileVideoButton.setOnLongClickListener { if (mosaic.selected >= 0) openVideoDocument(VideoTarget.TILE); true }
        binding.addVideoButton.setOnLongClickListener { openVideoDocument(VideoTarget.OVERLAY); true }
        binding.greenscreenButton.setOnLongClickListener {
            if (!greenscreenActive && !mosaicActive && activeRecording == null) openVideoDocument(VideoTarget.BACKGROUND); true
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
        binding.deleteButton.setImageResource(R.drawable.ic_backspace)
        binding.deleteButton.contentDescription = getString(R.string.delete_last)
        binding.gestureView.showIdleMarkers = !recording && anyLiveVideo()
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", s / 60, s % 60)
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
        if (activeRecording != null) stoppedInBackground = true
        activeRecording?.stop()
        activeRecording = null
        player?.pause()
        reviewOverlayPlayer?.pause()
        overlayPlayer?.pause()
        tilePlayers.values.forEach { it.pause() }
    }

    private var stoppedInBackground = false

    override fun onStart() {
        super.onStart()
        if (stoppedInBackground) { stoppedInBackground = false; Toast.makeText(this, R.string.recording_paused_background, Toast.LENGTH_LONG).show() }
        if (!inReview) {
            overlayPlayer?.let { if (it.playbackState == Player.STATE_IDLE) it.prepare() }
            tilePlayers.values.forEach { if (it.playbackState == Player.STATE_IDLE) it.prepare() }
            overlayStore.videoOverlay()?.let { o -> overlayPlayer?.seekTo(o.sourcePositionAt(currentTotalMs())) }
            syncTilePlayers()
        }
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
        pro.release()
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
