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
        for (i in 0 until segs.length()) {
            val s = segs.getJSONObject(i)
            val ws = s.getJSONArray("words")
            val segWords = ArrayList<Word>()
            for (k in 0 until ws.length()) {
                val w = ws.getJSONObject(k)
                val text = w.getString("w").trim()
                if (text.isEmpty()) continue
                segWords.add(Word(w.getLong("t0"), w.getLong("t1"), text))
            }
            words.addAll(segWords)
            raw.add(Caption(s.getLong("t0"), s.getLong("t1"), s.getString("text").trim(), segWords))
        }
        return Result(root.optString("language", "?"), words, raw)
    }

    fun close() { nativeFree(handle) }

    private external fun nativeFree(handle: Long)
    private external fun nativeTranscribe(handle: Long, samples: FloatArray, language: String?, threads: Int, progress: Progress?): String

    companion object {
        private const val TAG = "WhisperEngine"
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
