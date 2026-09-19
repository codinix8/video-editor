package de.codinix.videoeditor.overlay

import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Gemeinsame Geometrie aller Overlays. Koordinaten sind auf den sichtbaren Frame normiert:
 *  - cx, cy: Mittelpunkt, 0..1 der Frame-Breite bzw. -Höhe (0,0 = oben links)
 *  - widthFrac: Breite als Anteil der Frame-Breite
 *  - rotationDeg: Drehung im Uhrzeigersinn
 * So sitzt ein Overlay in Vorschau und Aufnahme an derselben Stelle, auch wenn beide
 * Ausgaben unterschiedliche Pixelgrößen haben.
 */
sealed class Overlay {
    abstract val id: Long
    abstract var cx: Float
    abstract var cy: Float
    abstract var widthFrac: Float
    abstract var rotationDeg: Float
    /** Höhe/Breite des Inhalts. */
    abstract val aspect: Float

    abstract fun snapshot(): OverlaySnapshot

    companion object {
        private val nextId = AtomicLong(1)
        fun newId() = nextId.getAndIncrement()
    }
}

class ImageOverlay(
    override val id: Long,
    val bitmap: Bitmap,
    override var cx: Float = 0.5f,
    override var cy: Float = 0.5f,
    override var widthFrac: Float = 0.4f,
    override var rotationDeg: Float = 0f
) : Overlay() {
    override val aspect: Float get() = bitmap.height.toFloat() / bitmap.width.toFloat()
    override fun snapshot() = OverlaySnapshot(id, cx, cy, widthFrac, rotationDeg, aspect, bitmap = bitmap)
}

/**
 * Ein Video als Bild-im-Bild. Die Wiedergabe läuft nur während der Aufnahme
 * (siehe MainActivity), damit das Video im Ergebnis durchgehend ist.
 *  - startOffsetMs: Position in der Gesamtaufnahme, an der das Video eingefügt wurde
 *  - volume: Lautstärke des Overlay-Tons beim Export, 0 = stumm, 1 = original, bis 2 = doppelt
 */
class VideoOverlay(
    override val id: Long,
    val file: File,
    override var cx: Float = 0.5f,
    override var cy: Float = 0.5f,
    override var widthFrac: Float = 0.5f,
    override var rotationDeg: Float = 0f,
    var volume: Float = 1f,
    var startOffsetMs: Long = 0L
) : Overlay() {
    /** Wird gesetzt, sobald der Player die Videogröße kennt. */
    var videoAspect: Float = 16f / 9f
    var durationMs: Long = 0L
    override val aspect: Float get() = videoAspect

    /** Ein Ereignis im Protokoll: ab [atMs] (Aufnahmezeit) gilt [gain]; läuft das Video? */
    data class Event(val atMs: Long, val gain: Float, val playing: Boolean)

    /**
     * Protokoll der Aufnahme: Einfügen, Lautstärkeänderungen, Pause/Weiter.
     * Leer = klassisch (ab startOffsetMs durchgehend mit volume).
     */
    val events = mutableListOf<Event>()

    /** Gilt derzeit als laufend? (letztes Ereignis) */
    val playing: Boolean get() = events.lastOrNull()?.playing ?: true

    fun addEvent(atMs: Long, gain: Float = volume, playing: Boolean = this.playing) {
        // Ereignis zur selben Zeit ersetzt das vorige
        if (events.isNotEmpty() && events.last().atMs >= atMs) events.removeAt(events.lastIndex)
        events.add(Event(atMs, gain, playing))
    }

    /** Zeitleiste für den Renderer (Segmente ab Einfügezeitpunkt). */
    fun timeline(): List<Event> =
        if (events.isEmpty()) listOf(Event(startOffsetMs, volume, true)) else events.toList()

    /** Ereignisse hinter [totalMs] verwerfen (nach Segment-Löschen). */
    fun trimEvents(totalMs: Long) {
        events.removeAll { it.atMs > totalMs }
    }

    /** Ist irgendwo im Protokoll Ton vorhanden? */
    val soundOn: Boolean get() = timeline().any { it.playing && it.gain > 0.005f }

    /**
     * Position im Quellvideo zur Aufnahmezeit [atMs]: Summe der Laufzeiten aller „playing“-
     * Abschnitte bis dahin, in der Schleife.
     */
    fun sourcePositionAt(atMs: Long): Long {
        val tl = timeline()
        var pos = 0L
        for (i in tl.indices) {
            val from = tl[i].atMs
            if (from >= atMs) break
            val to = if (i + 1 < tl.size) minOf(tl[i + 1].atMs, atMs) else atMs
            if (tl[i].playing && to > from) pos += to - from
        }
        return if (durationMs > 0) pos % durationMs else pos
    }
    override fun snapshot() = OverlaySnapshot(id, cx, cy, widthFrac, rotationDeg, aspect, isVideo = true)
}

/** Unveränderliche Kopie für den Render-Thread. */
data class OverlaySnapshot(
    val id: Long,
    val cx: Float,
    val cy: Float,
    val widthFrac: Float,
    val rotationDeg: Float,
    val aspect: Float,
    val bitmap: Bitmap? = null,
    val isVideo: Boolean = false
)

/**
 * Hält die Overlay-Liste (UI-Thread) und stellt dem GL-Thread eine konsistente
 * Momentaufnahme bereit. Jede Änderung ruft [publish] auf.
 */
class OverlayStore {
    val items = mutableListOf<Overlay>()

    @Volatile
    var snapshot: List<OverlaySnapshot> = emptyList()
        private set

    var selectedId: Long? = null

    fun add(overlay: Overlay) {
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

    fun selected(): Overlay? = items.firstOrNull { it.id == selectedId }

    fun videoOverlay(): VideoOverlay? = items.filterIsInstance<VideoOverlay>().firstOrNull()

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
