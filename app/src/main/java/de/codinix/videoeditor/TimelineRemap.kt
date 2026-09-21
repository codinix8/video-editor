package de.codinix.videoeditor

import de.codinix.videoeditor.overlay.VideoOverlay
import de.codinix.videoeditor.whisper.Caption

/**
 * Rechnet zeitgebundene Daten um, wenn Segmente umgeordnet oder gelöscht werden.
 *
 * [oldDurations] = Dauern der Segmente in alter Reihenfolge, [newOrder] = alte Indizes in
 * neuer Reihenfolge (fehlende Indizes = gelöscht). Alles außerhalb der behaltenen Segmente
 * fällt weg; innerhalb eines Segments verschiebt sich die Zeit um eine Konstante.
 */
class TimelineRemap(oldDurations: List<Long>, private val newOrder: List<Int>) {

    private class Span(val oldStart: Long, val oldEnd: Long, val newStart: Long)
    private val spans: List<Span>

    init {
        val oldStarts = LongArray(oldDurations.size)
        var acc = 0L
        oldDurations.forEachIndexed { i, d -> oldStarts[i] = acc; acc += d }
        var newAcc = 0L
        spans = newOrder.map { i ->
            val sp = Span(oldStarts[i], oldStarts[i] + oldDurations[i], newAcc)
            newAcc += oldDurations[i]
            sp
        }
    }

    val totalMs: Long get() = spans.lastOrNull()?.let { it.newStart + (it.oldEnd - it.oldStart) } ?: 0L

    /** Neue Zeit für eine alte Zeit oder null, wenn sie in einem gelöschten Segment lag. */
    fun map(oldMs: Long): Long? {
        for (sp in spans) if (oldMs >= sp.oldStart && oldMs < sp.oldEnd) return sp.newStart + (oldMs - sp.oldStart)
        // Genau am Ende der letzten Aufnahme
        spans.lastOrNull()?.let { if (oldMs == it.oldEnd) return it.newStart + (it.oldEnd - it.oldStart) }
        return null
    }

    fun captions(list: List<Caption>): List<Caption> = list.mapNotNull { c ->
        val sp = spans.firstOrNull { c.startMs >= it.oldStart && c.startMs < it.oldEnd } ?: return@mapNotNull null
        val d = sp.newStart - sp.oldStart
        // Block bleibt in seinem Segment: Ende höchstens am neuen Segmentende
        val segEndNew = sp.newStart + (sp.oldEnd - sp.oldStart)
        val newEnd = minOf(c.endMs + d, segEndNew - 20).coerceAtLeast(c.startMs + d + 300)
        c.copy(startMs = c.startMs + d, endMs = newEnd,
            words = c.words.map { it.copy(startMs = it.startMs + d, endMs = minOf(it.endMs + d, newEnd)) })
    }.sortedBy { it.startMs }

    /**
     * Ton-Protokoll umrechnen. Pro neuem Segment: Zustand am alten Segmentanfang (Lautstärke,
     * läuft/pausiert, Quellposition) als Startereignis mit Sprung, dann die Ereignisse des
     * Segments verschoben. [endMs] = altes Ende der Spur (Overlay entfernt) oder null.
     */
    fun timeline(events: List<VideoOverlay.Event>, durationMs: Long, endMs: Long?): List<VideoOverlay.Event> {
        if (events.isEmpty()) return events
        val out = ArrayList<VideoOverlay.Event>()
        for (sp in spans) {
            val len = sp.oldEnd - sp.oldStart
            // Zustand am alten Segmentanfang
            val before = events.filter { it.atMs <= sp.oldStart }
            val active = before.isNotEmpty() && (endMs == null || sp.oldStart < endMs)
            if (active) {
                val st = before.last()
                out.add(VideoOverlay.Event(sp.newStart, st.gain, st.playing, sourcePosAt(events, durationMs, sp.oldStart)))
            } else if (out.isNotEmpty()) {
                out.add(VideoOverlay.Event(sp.newStart, 0f, false))
            }
            // Ereignisse innerhalb des Segments
            events.filter { it.atMs > sp.oldStart && it.atMs < sp.oldEnd }.forEach { e ->
                if (endMs == null || e.atMs < endMs)
                    out.add(VideoOverlay.Event(sp.newStart + (e.atMs - sp.oldStart), e.gain, e.playing, e.seekMs))
            }
            // Spurende innerhalb des Segments
            if (endMs != null && endMs > sp.oldStart && endMs < sp.oldEnd) {
                out.add(VideoOverlay.Event(sp.newStart + (endMs - sp.oldStart), 0f, false))
            }
            if (len <= 0) continue
        }
        // Führende „inaktiv“-Einträge entfernen
        while (out.isNotEmpty() && !out.first().playing && out.first().gain == 0f) out.removeAt(0)
        return out
    }

    companion object {
        /** Quellposition zur Zeit [atMs] laut Protokoll (mit Sprüngen). */
        fun sourcePosAt(events: List<VideoOverlay.Event>, durationMs: Long, atMs: Long): Long {
            var pos = 0L
            for (i in events.indices) {
                val from = events[i].atMs
                if (from >= atMs) break
                events[i].seekMs?.let { pos = it }
                val to = if (i + 1 < events.size) minOf(events[i + 1].atMs, atMs) else atMs
                if (events[i].playing && to > from) pos += to - from
            }
            return if (durationMs > 0) pos % durationMs else pos
        }
    }
}
