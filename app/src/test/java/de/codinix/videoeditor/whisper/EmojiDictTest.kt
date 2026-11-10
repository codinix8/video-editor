package de.codinix.videoeditor.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Word → emoji lookup used for caption emojis (offline, deterministic). */
class EmojiDictTest {
    @Test fun exactWord() = assertEquals("🇮🇷", EmojiDict.forWord("iran"))

    @Test fun caseAndPunctuation_areIgnored() = assertEquals("🇮🇷", EmojiDict.forWord("  Iran!"))

    @Test fun umlauts_areNormalised() = assertEquals("🇦🇹", EmojiDict.forWord("Österreich"))

    @Test fun stem_matchesInflectedWord() = assertEquals("🇩🇪", EmojiDict.forWord("Deutschlands"))

    @Test fun shortKeys_matchOnlyExactly() = assertEquals("🇺🇸", EmojiDict.forWord("USA"))

    @Test fun unknownOrEmpty_isNull() {
        assertNull(EmojiDict.forWord(""))
        assertNull(EmojiDict.forWord("..."))
        assertNull(EmojiDict.forWord("qqxzv"))
    }
}
