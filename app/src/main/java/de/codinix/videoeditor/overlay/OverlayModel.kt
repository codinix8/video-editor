package de.codinix.videoeditor.overlay

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicLong

/**
 * Ein Bild-Overlay im Frame. Alle Koordinaten sind auf den Ausgabe-Frame normiert:
 *  - cx, cy: Mittelpunkt, 0..1 der Frame-Breite bzw. -Höhe (0,0 = oben links)
 *  - widthFrac: Breite als Anteil der Frame-Breite
 *  - rotationDeg: Drehung im Uhrzeigersinn
 *
 * Auf diese Weise sitzt das Overlay in Vorschau und Aufnahme an derselben Stelle,
 * auch wenn beide Ausgaben unterschiedliche Pixelgrößen haben.
 */
class ImageOverlay(
    val id: Long,
    val bitmap: Bitmap,
    var cx: Float = 0.5f,
    var cy: Float = 0.5f,
    var widthFrac: Float = 0.4f,
    var rotationDeg: Float = 0f
) {
    /** Höhe/Breite des Bildes. */
    val aspect: Float get() = bitmap.height.toFloat() / bitmap.width.toFloat()

    fun snapshot() = OverlaySnapshot(id, bitmap, cx, cy, widthFrac, rotationDeg, aspect)

    companion object {
        private val nextId = AtomicLong(1)
        fun newId() = nextId.getAndIncrement()
    }
}

/** Unveränderliche Kopie für den Render-Thread. */
data class OverlaySnapshot(
    val id: Long,
    val bitmap: Bitmap,
    val cx: Float,
    val cy: Float,
    val widthFrac: Float,
    val rotationDeg: Float,
    val aspect: Float
)

/**
 * Hält die Overlay-Liste (UI-Thread) und stellt dem GL-Thread eine konsistente
 * Momentaufnahme bereit. Jede Änderung ruft [publish] auf.
 */
class OverlayStore {
    val items = mutableListOf<ImageOverlay>()

    @Volatile
    var snapshot: List<OverlaySnapshot> = emptyList()
        private set

    var selectedId: Long? = null

    fun add(overlay: ImageOverlay) {
        items.add(overlay)
        selectedId = overlay.id
        publish()
    }

    fun remove(id: Long) {
        items.removeAll { it.id == id }
        if (selectedId == id) selectedId = null
        publish()
    }

    fun clear() {
        items.clear()
        selectedId = null
        publish()
    }

    fun selected(): ImageOverlay? = items.firstOrNull { it.id == selectedId }

    fun bringToFront(id: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0 && idx != items.lastIndex) {
            items.add(items.removeAt(idx))
            publish()
        }
    }

    fun publish() {
        snapshot = items.map { it.snapshot() }
    }
}
