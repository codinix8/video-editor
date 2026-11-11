package de.codinix.videoeditor

import de.codinix.videoeditor.overlay.VideoOverlay.Event
import de.codinix.videoeditor.whisper.Caption
import de.codinix.videoeditor.whisper.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Segments used below: durations 1000/2000/3000 ms → old spans [0,1000) [1000,3000) [3000,6000).
 */
class TimelineRemapTest {
    private val durations = listOf(1000L, 2000L, 3000L)

    // --- map / totalMs -------------------------------------------------------------------

    @Test fun identityOrder_keepsTimes() {
        val r = TimelineRemap(durations, listOf(0, 1, 2))
        assertEquals(6000L, r.totalMs)
        assertEquals(500L, r.map(500))
        assertEquals(2500L, r.map(2500))
        assertEquals(6000L, r.map(6000)) // exactly at the end of the last segment
        assertNull(r.map(6001))
    }

    @Test fun reorder_shiftsEachSegmentByConstant() {
        val r = TimelineRemap(durations, listOf(2, 0, 1))
        assertEquals(6000L, r.totalMs)
        assertEquals(500L, r.map(3500))  // segment 2 moves to the front
        assertEquals(3000L, r.map(0))    // segment 0 follows it
        assertEquals(4500L, r.map(1500)) // segment 1 comes last
        assertEquals(0L, r.map(3000))    // segment start is inclusive
    }

    @Test fun deletedSegment_mapsToNull() {
        val r = TimelineRemap(durations, listOf(0, 2))
        assertEquals(4000L, r.totalMs)
        assertNull(r.map(1500))
        assertEquals(1000L, r.map(3000))
        assertEquals(4000L, r.map(6000))
    }

    @Test fun emptyOrder_hasZeroLengthAndMapsNothing() {
        val r = TimelineRemap(durations, emptyList())
        assertEquals(0L, r.totalMs)
        assertNull(r.map(0))
    }

    // --- captions ------------------------------------------------------------------------

    @Test fun captions_inDeletedSegmentAreDropped_othersShiftedWithWords() {
        val r = TimelineRemap(durations, listOf(0, 2))
        val gone = Caption(1200, 1800, "gone", listOf(Word(1200, 1800, "gone")))
        val kept = Caption(3200, 3800, "a b", listOf(Word(3200, 3500, "a"), Word(3500, 3800, "b")))
        val out = r.captions(listOf(gone, kept))
        assertEquals(1, out.size)
        assertEquals(Caption(1200, 1800, "a b", listOf(Word(1200, 1500, "a"), Word(1500, 1800, "b"))), out[0])
    }

    @Test fun captions_endIsClampedToNewSegmentEnd() {
        // [1,0] with durations 1000/2000: segment 0 now lives at [2000,3000)
        val r = TimelineRemap(listOf(1000L, 2000L), listOf(1, 0))
        val c = Caption(200, 1500, "x y", listOf(Word(200, 600, "x"), Word(600, 1500, "y")))
        val out = r.captions(listOf(c)).single()
        assertEquals(2200L, out.startMs)
        assertEquals(2980L, out.endMs) // 20 ms before the segment end
        assertEquals(listOf(Word(2200, 2600, "x"), Word(2600, 2980, "y")), out.words)
    }

    @Test fun captions_keepMinimumDurationOf300ms() {
        val r = TimelineRemap(listOf(1000L, 2000L), listOf(0, 1))
        val out = r.captions(listOf(Caption(900, 950, "short", emptyList()))).single()
        assertEquals(900L, out.startMs)
        assertEquals(1200L, out.endMs)
    }

    @Test fun captions_areSortedByNewStart() {
        val r = TimelineRemap(listOf(1000L, 1000L), listOf(1, 0))
        val a = Caption(100, 400, "A", emptyList())
        val b = Caption(1100, 1400, "B", emptyList())
        val out = r.captions(listOf(a, b))
        assertEquals(listOf("B", "A"), out.map { it.text })
        assertEquals(listOf(100L, 1100L), out.map { it.startMs })
    }

    // --- sourcePosAt ---------------------------------------------------------------------

    @Test fun sourcePosAt_countsOnlyPlayingTime() {
        val ev = listOf(Event(0, 1f, true), Event(1000, 1f, false), Event(2000, 1f, true))
        assertEquals(1500L, TimelineRemap.sourcePosAt(ev, 0, 2500))
    }

    @Test fun sourcePosAt_wrapsAroundVideoDuration() {
        val ev = listOf(Event(0, 1f, true), Event(1000, 1f, false), Event(2000, 1f, true))
        assertEquals(300L, TimelineRemap.sourcePosAt(ev, 1200, 2500))
    }

    @Test fun sourcePosAt_followsSeekJumps() {
        val ev = listOf(Event(0, 1f, true), Event(1000, 1f, true, seekMs = 5000))
        assertEquals(5500L, TimelineRemap.sourcePosAt(ev, 0, 1500))
    }

    @Test fun sourcePosAt_beforeFirstEventIsZero() {
        assertEquals(0L, TimelineRemap.sourcePosAt(listOf(Event(500, 1f, true)), 0, 200))
    }

    // --- timeline (overlay audio log) ----------------------------------------------------

    @Test fun timeline_emptyStaysEmpty() {
        assertTrue(TimelineRemap(durations, listOf(1, 0)).timeline(emptyList(), 5000, null).isEmpty())
    }

    @Test fun timeline_deletedMiddleSegment_insertsSeekToSkipIt() {
        val r = TimelineRemap(listOf(1000L, 1000L, 1000L), listOf(0, 2))
        val out = r.timeline(listOf(Event(0, 1f, true)), 10_000, null)
        assertEquals(listOf(Event(0, 1f, true, 0), Event(1000, 1f, true, 2000)), out)
    }

    @Test fun timeline_reorder_withTrackEndInsideSegment() {
        val r = TimelineRemap(listOf(1000L, 1000L), listOf(1, 0))
        val out = r.timeline(listOf(Event(1200, 0.5f, true)), 5000, endMs = 1800)
        assertEquals(
            listOf(Event(200, 0.5f, true), Event(800, 0f, false), Event(1000, 0f, false)),
            out
        )
    }

    @Test fun timeline_dropsLeadingInactiveEntries() {
        val r = TimelineRemap(listOf(1000L, 1000L), listOf(0, 1))
        val out = r.timeline(listOf(Event(0, 0f, false), Event(1500, 1f, true)), 0, null)
        assertEquals(listOf(Event(1500, 1f, true)), out)
    }
}
