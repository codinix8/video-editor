package de.codinix.videoeditor.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Caption.chunk / chunkSentences: how recognised words are cut into on-screen caption blocks. */
class CaptionChunkTest {
    /** n consecutive words of 300 ms each: w0 = 0..300, w1 = 300..600, ... */
    private fun words(n: Int, prefix: String = "w") = (0 until n).map { Word(it * 300L, (it + 1) * 300L, "$prefix$it") }

    @Test fun empty_givesNoCaptions() {
        assertTrue(Caption.chunk(emptyList()).isEmpty())
        assertTrue(Caption.chunkSentences(listOf(emptyList(), emptyList())).isEmpty())
    }

    @Test fun maxWords_splitsAfterSixWords() {
        val c = Caption.chunk(words(8))
        assertEquals(2, c.size)
        assertEquals("w0 w1 w2 w3 w4 w5", c[0].text)
        assertEquals("w6 w7", c[1].text)
        assertEquals(6, c[0].words.size)
        assertEquals(0L, c[0].startMs)
        assertEquals(1800L, c[1].startMs)
    }

    @Test fun blockEnd_stopsJustBeforeNextBlock() {
        val c = Caption.chunk(words(8))
        // first block would end at 1800, but is cut 40 ms before the next block starts
        assertEquals(1760L, c[0].endMs)
        assertEquals(2400L, c[1].endMs)
    }

    @Test fun sentencePunctuation_endsBlock() {
        val c = Caption.chunk(listOf(Word(0, 300, "Hi."), Word(300, 600, "there")))
        assertEquals(listOf("Hi.", "there"), c.map { it.text })
        // every block stays visible for at least 300 ms
        assertTrue(c.all { it.endMs - it.startMs >= 300 })
    }

    @Test fun maxChars_splitsLongWords() {
        val long = listOf(Word(0, 300, "a".repeat(20)), Word(300, 600, "b".repeat(20)))
        assertEquals(2, Caption.chunk(long, maxChars = 38).size)
        assertEquals(1, Caption.chunk(long, maxChars = 60).size)
    }

    @Test fun maxMs_splitsLongBlocks() {
        val slow = listOf(Word(0, 2000, "one"), Word(2000, 4000, "two"))
        assertEquals(2, Caption.chunk(slow, maxMs = 3500).size)
    }

    @Test fun eachSentence_startsNewBlock() {
        val c = Caption.chunkSentences(listOf(words(2, "a"), listOf(Word(600, 900, "b0"))))
        assertEquals(listOf("a0 a1", "b0"), c.map { it.text })
    }

    @Test fun firstWord_isClampedToSentenceStart() {
        val c = Caption.chunkSentences(listOf(listOf(Word(900, 1300, "late"))), sentenceStarts = listOf(1000L))
        assertEquals(1000L, c[0].startMs)
    }

    @Test fun segmentBoundary_splitsAndSnapsWord() {
        // word starts 200 ms before the cut at 1000 and ends after it: it moves onto the cut
        val c = Caption.chunk(listOf(Word(0, 500, "before"), Word(800, 1100, "after")), boundariesMs = listOf(1000L))
        assertEquals(listOf("before", "after"), c.map { it.text })
        assertEquals(1000L, c[1].startMs)
        // block before the cut ends 20 ms before it at the latest
        assertTrue(c[0].endMs <= 980L)
    }
}
