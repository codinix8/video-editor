package de.codinix.videoeditor

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guard for the rule "every UI string in all 7 strings.xml".
 *
 * Reads the seven `res/values…/strings.xml` files as plain text (no Android classes: the android.jar stub
 * throws in JVM tests) and checks that
 *  1. every key of the English default `values/strings.xml` exists in de/tr/es/fr/fa/ar, and
 *  2. no non-German file (default EN and tr/es/fr/fa/ar) contains German error words that were localised in 1.1.5
 *     (scope A + D). The word list is deliberately small and explicit; extend it when a new German leak is fixed.
 */
class StringsConsistencyTest {

    private val locales = listOf("de", "tr", "es", "fr", "fa", "ar")

    /** German-only words from the 1.1.5 error/crash-label localisation. Matched as whole words, case-sensitive. */
    private val germanWords = listOf(
        "Fehler", "fehlgeschlagen", "Bild", "Umrechnung", "Umwandlung", "Spracherkennung",
        "Entwurf", "Kachelvideo", "Overlay-Video", "Zeit", "Gerät", "Keine Segmente",
    )

    private val resDir: File = listOf("src/main/res", "app/src/main/res").map(::File)
        .firstOrNull { File(it, "values/strings.xml").isFile }
        ?: error("res dir not found from ${File(".").absolutePath}")

    /** Entry (`string:name`, `plurals:name`, `string-array:name`) -> raw inner text. Skips translatable="false". */
    private fun entries(dir: String): Map<String, String> {
        val text = File(resDir, "$dir/strings.xml").readText()
        val entry = Regex("""<(string|plurals|string-array)\s+name="([^"]+)"([^>]*)>([\s\S]*?)</\1>""")
        return entry.findAll(text)
            .filterNot { it.groupValues[3].contains("""translatable="false"""") }
            .associate { "${it.groupValues[1]}:${it.groupValues[2]}" to it.groupValues[4] }
    }

    private val wordPatterns = germanWords.associateWith { Regex("""(?<!\p{L})${Regex.escape(it)}(?!\p{L})""") }

    private fun germanHits(value: String) = wordPatterns.filterValues { it.containsMatchIn(value) }.keys

    @Test fun everyDefaultKey_existsInAllLocales() {
        val default = entries("values")
        assertTrue("parsed too few keys from values/strings.xml: ${default.size}", default.size > 100)
        val gaps = locales.flatMap { loc ->
            val keys = entries("values-$loc").keys
            (default.keys - keys).map { "values-$loc: missing $it" }
        }
        if (gaps.isNotEmpty()) fail("Missing translations (add them in all 7 strings.xml):\n" + gaps.joinToString("\n"))
    }

    @Test fun nonGermanLocales_containNoGermanErrorWords() {
        val leaks = (listOf("values") + locales.filter { it != "de" }.map { "values-$it" }).flatMap { dir ->
            entries(dir).flatMap { (key, value) ->
                germanHits(value).map { w -> "$dir: $key contains German \"$w\"" }
            }
        }
        if (leaks.isNotEmpty()) fail("German text in a non-German locale:\n" + leaks.joinToString("\n"))
    }

    @Test fun wordCheck_catchesALeak() {
        val value = "Export fehlgeschlagen: Bild"
        val hits = germanHits(value)
        assertTrue(hits.containsAll(listOf("fehlgeschlagen", "Bild")))
        assertTrue("whole words only", germanHits("Bildschirm Zeitraum").isEmpty())
    }
}
