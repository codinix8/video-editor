package de.codinix.videoeditor.whisper

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Kotlin-Seite der Whisper-Anbindung. Ein Engine-Objekt hält ein geladenes Modell.
 */
class WhisperEngine private constructor(private val handle: Long) {

    interface Progress { fun onProgress(percent: Int) }

    class Result(val language: String, val words: List<Word>, val rawSegments: List<Caption>)

    /**
     * @param pcm16k Mono, 16 kHz, Werte -1..1
     * @param language ISO-Code ("de", "en") oder null für automatische Erkennung
     */
    fun transcribe(pcm16k: FloatArray, language: String?, progress: Progress?): Result {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val json = nativeTranscribe(handle, pcm16k, language, threads, progress)
        val root = JSONObject(json)
        if (root.has("error")) throw IllegalStateException(root.getString("error"))
        val segs = root.getJSONArray("segments")
        val words = ArrayList<Word>()
        val raw = ArrayList<Caption>()
        var dropped = 0
        for (i in 0 until segs.length()) {
            val s = segs.getJSONObject(i)
            val t0 = s.getLong("t0"); val t1 = s.getLong("t1")
            val text = s.getString("text").trim()
            // Halluzinationen auf Stille aussortieren: Modell unsicher, Audio praktisch stumm oder bekannte Floskel
            val nsp = s.optDouble("nsp", 0.0)
            val rms = rmsDb(pcm16k, t0, t1)
            if (nsp > 0.6 || rms < SILENCE_DB || isHallucination(text)) { dropped++; continue }
            val ws = s.getJSONArray("words")
            val segWords = ArrayList<Word>()
            for (k in 0 until ws.length()) {
                val w = ws.getJSONObject(k)
                val text = w.getString("w").trim()
                if (text.isEmpty()) continue
                segWords.add(Word(w.getLong("t0"), w.getLong("t1"), text))
            }
            words.addAll(segWords)
            raw.add(Caption(t0, t1, text, segWords))
        }
        if (dropped > 0) Log.i(TAG, "$dropped Segment(e) als Stille/Halluzination verworfen")
        return Result(root.optString("language", "?"), words, raw)
    }

    /** Pegel (dBFS) des Audios im Bereich [t0,t1] ms; -100 bei leerem Bereich. */
    private fun rmsDb(pcm: FloatArray, t0: Long, t1: Long): Double {
        val a = (t0 * 16).toInt().coerceIn(0, pcm.size); val b = (t1 * 16).toInt().coerceIn(a, pcm.size)
        if (b - a < 160) return -100.0
        var sum = 0.0
        for (i in a until b) sum += pcm[i].toDouble() * pcm[i]
        val rms = Math.sqrt(sum / (b - a))
        return if (rms <= 1e-9) -100.0 else 20 * Math.log10(rms)
    }

    private fun isHallucination(text: String): Boolean {
        val t = text.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), "").trim()
        if (t.isEmpty()) return true
        return HALLUCINATIONS.any { t == it || t.startsWith(it) }
    }

    fun close() { nativeFree(handle) }

    private external fun nativeFree(handle: Long)
    private external fun nativeTranscribe(handle: Long, samples: FloatArray, language: String?, threads: Int, progress: Progress?): String

    companion object {
        private const val TAG = "WhisperEngine"
        /** Unter diesem Pegel gilt ein Abschnitt als stumm (dBFS, 16-kHz-Mono nach Normalisierung). */
        private const val SILENCE_DB = -50.0
        /** Typische Whisper-Erfindungen auf Stille/Rauschen (mehrsprachig, klein geschrieben, ohne Satzzeichen). */
        private val HALLUCINATIONS = listOf(
            "thank you for watching", "thanks for watching", "see you next time", "see you in the next video",
            "please subscribe", "subtitles by", "amara org", "thank you", "bye bye", "the end",
            "danke fürs zuschauen", "vielen dank fürs zuschauen", "bis zum nächsten mal", "untertitel im auftrag des zdf",
            "untertitelung des zdf", "untertitel von", "copyright", "www", "música", "music", "musik",
            "gracias por ver", "merci davoir regardé", "sous titres", "abonnez vous", "teşekkürler", "izlediğiniz için teşekkürler",
            "ممنون که تماشا کردید", "شكرا للمشاهدة"
        )
        init { System.loadLibrary("whisperjni") }

        @JvmStatic private external fun nativeInit(path: String): Long

        fun load(model: File): WhisperEngine {
            val h = nativeInit(model.absolutePath)
            if (h == 0L) throw IllegalStateException("Modell konnte nicht geladen werden")
            Log.i(TAG, "Modell geladen: ${model.name}")
            return WhisperEngine(h)
        }
    }
}
