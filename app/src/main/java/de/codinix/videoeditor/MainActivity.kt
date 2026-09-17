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

    private val audioManager by lazy { getSystemService(android.media.AudioManager::class.java) }
    private var headphonesConnected = false
    /** Mikrofon-Verstärkung für den Export (1.0 = unverändert). */
    private var micGain = 1f
    private val audioDeviceCallback = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) { updateHeadphones(true) }
        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) { updateHeadphones(false) }
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
        cacheDir.listFiles()?.filter { it.name.startsWith("overlay_video_") || it.name.startsWith("export_") }
            ?.forEach { it.delete() }

        compositor = CompositorProcessor(overlayStore)
        compositorEffect = CompositorEffect(compositor)
        compositor.onFrameAspectChanged = { aspect -> main.post { binding.gestureView.frameAspect = aspect } }

        binding.gestureView.store = overlayStore
        binding.gestureView.onSelectionChanged = { sel -> updateOverlayButtons(sel) }
        binding.addVideoButton.setOnClickListener {
            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        binding.soundButton.setOnClickListener {
            (overlayStore.selected() as? VideoOverlay)?.let { showVolumeDialog(it) }
        }
        binding.micButton.setOnClickListener { showMicDialog() }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, main)
        updateHeadphones(false)
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
                    overlayPlayer?.play()
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
        overlayPlayer?.pause()
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
            syncOverlayPlayer()
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
            val bmp = loadBitmap(uri, 1280)
            val overlay = ImageOverlay(Overlay.newId(), bmp, cx = 0.5f, cy = 0.5f, widthFrac = 0.45f)
            overlayStore.add(overlay)
            updateOverlayButtons(overlay)
            binding.gestureView.invalidate()
            Toast.makeText(this, R.string.overlay_hint, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Overlay laden fehlgeschlagen", e)
            Toast.makeText(this, getString(R.string.error, e.message ?: "Bild"), Toast.LENGTH_LONG).show()
        }
    }

    private fun updateOverlayButtons(sel: Overlay?) {
        binding.removeOverlayButton.visibility = if (sel != null) android.view.View.VISIBLE else android.view.View.GONE
        val video = sel as? VideoOverlay
        binding.soundButton.visibility = if (video != null) android.view.View.VISIBLE else android.view.View.GONE
        video?.let {
            binding.soundButton.setImageResource(if (it.soundOn) R.drawable.ic_overlay_volume else R.drawable.ic_overlay_volume_off)
            binding.soundButton.contentDescription = getString(if (it.soundOn) R.string.sound_on else R.string.sound_off)
        }
    }

    private fun removeOverlay(id: Long) {
        val o = overlayStore.items.firstOrNull { it.id == id }
        overlayStore.remove(id)
        if (o is VideoOverlay) {
            overlayPlayer?.release(); overlayPlayer = null
            compositor.releaseVideoLayer(o.id)
            o.file.delete()
        }
    }

    private fun clearOverlays() {
        overlayStore.items.map { it.id }.forEach { removeOverlay(it) }
        overlayStore.clear()
        updateOverlayButtons(null)
    }

    private fun addVideoOverlay(uri: Uri) {
        if (overlayStore.videoOverlay() != null) {
            Toast.makeText(this, R.string.only_one_video, Toast.LENGTH_LONG).show(); return
        }
        Toast.makeText(this, R.string.video_copying, Toast.LENGTH_SHORT).show()
        val dest = File(cacheDir, "overlay_video_${System.currentTimeMillis()}.mp4")
        bgExecutor.execute {
            try {
                contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalStateException("Video konnte nicht gelesen werden")
                main.post {
                    val total = segments.sumOf { it.durationMs }
                    val overlay = VideoOverlay(Overlay.newId(), dest, startOffsetMs = total)
                    overlayStore.add(overlay)
                    attachVideoOverlay(overlay)
                    updateOverlayButtons(overlay)
                    binding.gestureView.invalidate()
                    Toast.makeText(this, R.string.overlay_hint, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video-Overlay fehlgeschlagen", e)
                dest.delete()
                main.post { Toast.makeText(this, getString(R.string.error, e.message ?: "Video"), Toast.LENGTH_LONG).show() }
            }
        }
    }

    /** Player anlegen und in die GL-Textur des Overlays rendern lassen. */
    private fun attachVideoOverlay(overlay: VideoOverlay) {
        overlayPlayer?.release()
        val p = ExoPlayer.Builder(this).build()
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

    /** Vorschau-Lautstärke: nur über Kopfhörer, sonst würde das Mikrofon den Lautsprecher aufnehmen. */
    private fun previewVolume(o: VideoOverlay): Float =
        if (headphonesConnected) o.volume.coerceIn(0f, 1f) else 0f

    private fun updateHeadphones(announce: Boolean) {
        val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        val types = setOf(
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            26 /* TYPE_BLE_HEADSET */, 30 /* TYPE_BLE_BROADCAST */
        )
        val now = devices.any { it.type in types }
        val changed = now != headphonesConnected
        headphonesConnected = now
        overlayStore.videoOverlay()?.let { o -> overlayPlayer?.volume = previewVolume(o) }
        if (changed && now && announce) Toast.makeText(this, R.string.headphones_on, Toast.LENGTH_SHORT).show()
    }

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
                    label.text = getString(R.string.volume_percent, value)
                    overlayPlayer?.volume = previewVolume(o)
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
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /** Position des Overlay-Videos an die Gesamtlänge der Aufnahme angleichen. */
    private fun syncOverlayPlayer() {
        val o = overlayStore.videoOverlay() ?: return
        val p = overlayPlayer ?: return
        val total = segments.sumOf { it.durationMs }
        if (total < o.startOffsetMs) o.startOffsetMs = total
        var pos = total - o.startOffsetMs
        if (o.durationMs > 0) pos %= o.durationMs
        p.seekTo(pos)
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

        val pad = (20 * resources.displayMetrics.density).toInt()
        val radios = android.widget.RadioGroup(this)
        options.forEachIndexed { i, (name, _) ->
            radios.addView(android.widget.RadioButton(this).apply { id = 1000 + i; text = name; isChecked = i == 0 })
        }
        val micLabel = android.widget.TextView(this).apply {
            text = getString(R.string.mic_volume) + ": " + getString(R.string.volume_percent, (micGain * 100).toInt())
            setPadding(0, pad, 0, 0)
        }
        val micSeek = android.widget.SeekBar(this).apply {
            max = 200; progress = (micGain * 100).toInt()
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar, v: Int, fromUser: Boolean) {
                    micLabel.text = getString(R.string.mic_volume) + ": " + getString(R.string.volume_percent, v)
                    micGain = v / 100f
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar) {}
            })
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(radios); addView(micLabel); addView(micSeek)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_title)
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.save) { _, _ ->
                val idx = (radios.checkedRadioButtonId - 1000).coerceIn(0, options.lastIndex)
                runExport(options[idx].second, micSeek.progress / 100f)
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

        val ex = Exporter(this)
        exporter = ex
        val audioMix = overlayStore.videoOverlay()
            ?.takeIf { it.soundOn && it.durationMs > 0 }
            ?.let { listOf(Exporter.AudioMix(it.file, it.startOffsetMs, it.durationMs, it.volume)) }
            ?: emptyList()
        val progressRes = if (audioMix.isEmpty() && kotlin.math.abs(micGain - 1f) < 0.01f)
            R.string.export_running else R.string.export_running_mix
        ex.export(segments.map { it.file }, targetHeight, object : Exporter.Listener {
            override fun onProgress(percent: Int) {
                dialog.setMessage(getString(progressRes, percent))
            }
            override fun onDone(uri: Uri) {
                dialog.dismiss()
                ex.release(); exporter = null
                segments.forEach { it.file.delete() }
                segments.clear()
                clearOverlays()
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
                player?.play()
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
                clearOverlays()
                refreshUi()
            }
            .setNeutralButton(R.string.save_draft) { _, _ -> saveDraft() }
            .setNegativeButton(R.string.cancel, null)
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
                preferredQuality?.let { label(it) }
            )
            segments.clear()
            // Dateien der Video-Overlays wurden in den Entwurf verschoben – nur Player/Textur freigeben
            overlayStore.items.filterIsInstance<VideoOverlay>().forEach {
                overlayPlayer?.release(); overlayPlayer = null
                compositor.releaseVideoLayer(it.id)
            }
            overlayStore.clear()
            updateOverlayButtons(null)
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
        val fmtDate = java.text.SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
        val labels = list.map {
            getString(R.string.draft_item, fmtDate.format(it.createdAt), it.segmentCount, fmt(it.durationMs))
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drafts)
            .setItems(labels) { _, which -> askDraftAction(list[which]) }
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
            overlayStore.selectedId = null
            overlayStore.videoOverlay()?.let { attachVideoOverlay(it) }
            updateOverlayButtons(null)

            // Kameraeinstellungen wiederherstellen, damit neue Segmente zu den alten passen
            lensFacing = loaded.lensFacing
            preferredQuality = QUALITY_ORDER.firstOrNull { label(it) == loaded.qualityLabel }
            bindCamera()

            binding.gestureView.invalidate()
            refreshUi()
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

        binding.draftsButton.visibility =
            if (segments.isEmpty() && !recording) android.view.View.VISIBLE else android.view.View.GONE

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
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        overlayPlayer?.release(); overlayPlayer = null
        compositor.release()
        bgExecutor.shutdown()
    }

    companion object {
        private const val TAG = "VideoEditor"
        private val QUALITY_ORDER = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
    }
}
