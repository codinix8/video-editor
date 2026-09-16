package de.codinix.videoeditor.gl

import android.util.Log
import androidx.camera.core.CameraEffect
import androidx.core.util.Consumer

/** Hängt den [CompositorProcessor] in Vorschau und Video-Aufnahme ein. */
class CompositorEffect(processor: CompositorProcessor) : CameraEffect(
    PREVIEW or VIDEO_CAPTURE,
    processor.executor,
    processor,
    Consumer<Throwable> { Log.e("CompositorEffect", "Effekt-Fehler", it) }
)
