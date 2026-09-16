package de.codinix.videoeditor

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import de.codinix.videoeditor.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Schritt 0: Kamera-Vorschau, ein Aufnahmeknopf, Video landet in der Galerie.
 * Zweck ist ausschließlich, die Kette GitHub-Build -> APK -> Handy -> Kamera -> Galerie
 * zu verifizieren. Alles Weitere baut darauf auf.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val requiredPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = requiredPermissions.all { results[it] == true }
            if (allGranted) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
                binding.statusText.text = getString(R.string.permission_needed)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.recordButton.setOnClickListener { toggleRecording() }

        if (hasAllPermissions()) {
            startCamera()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val cameraProvider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            // Höchste vom Gerät unterstützte Qualität, mit Rückfall auf niedrigere Stufen.
            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, videoCapture)
                binding.statusText.text = getString(R.string.ready)
            } catch (e: Exception) {
                Log.e(TAG, "Kamera konnte nicht gebunden werden", e)
                binding.statusText.text = getString(R.string.error, e.message ?: "bind failed")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleRecording() {
        val capture = videoCapture ?: return

        activeRecording?.let {
            it.stop()
            activeRecording = null
            return
        }

        val name = "VideoEditor_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoEditor")
            }
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val pending = capture.output.prepareRecording(this, outputOptions)
        val micGranted = PermissionChecker.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PermissionChecker.PERMISSION_GRANTED
        if (micGranted) pending.withAudioEnabled()

        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    binding.recordButton.isSelected = true
                    binding.recordButton.contentDescription = getString(R.string.stop)
                    binding.statusText.text = getString(R.string.recording, "0 s")
                }
                is VideoRecordEvent.Status -> {
                    val seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000
                    binding.statusText.text = getString(R.string.recording, "$seconds s")
                }
                is VideoRecordEvent.Finalize -> {
                    binding.recordButton.isSelected = false
                    binding.recordButton.contentDescription = getString(R.string.record)
                    if (!event.hasError()) {
                        binding.statusText.text = getString(R.string.saved)
                        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
                    } else {
                        activeRecording?.close()
                        activeRecording = null
                        val msg = "Code ${event.error}"
                        Log.e(TAG, "Aufnahme fehlgeschlagen: $msg", event.cause)
                        binding.statusText.text = getString(R.string.error, msg)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "VideoEditor"
    }
}
