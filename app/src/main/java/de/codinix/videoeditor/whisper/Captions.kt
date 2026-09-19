package de.codinix.videoeditor.whisper

import org.json.JSONArray
import org.json.JSONObject

data class Word(val startMs: Long, val endMs: Long, val text: String)

/** Ein Untertitel-Block: was zwischen startMs und endMs eingeblendet wird. */
data class Caption(val startMs: Long, val endMs: Long, val text: String, val words: List<Word>) {
    fun toJson(): JSONObject = JSONObject()
        .put("s", startMs).put("e", endMs).put("t", text)
        .put("w", JSONArray().also { a -> words.forEach { a.put(JSONObject().put("s", it.startMs).put("e", it.endMs).put("t", it.text)) } })

    companion object {
        fun fromJson(o: JSONObject): Caption {
            val words = ArrayList<Word>()
            val a = o.optJSONArray("w") ?: JSONArray()
            for (i in 0 until a.length()) {
                val w = a.getJSONObject(i)
                words.add(Word(w.getLong("s"), w.getLong("e"), w.getString("t")))
            }
            return Caption(o.getLong("s"), o.getLong("e"), o.getString("t"), words)
        }
        fun listToJson(list: List<Caption>): JSONArray = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
        fun listFromJson(a: JSONArray?): MutableList<Caption> {
            val out = ArrayList<Caption>()
            if (a == null) return out
            for (i in 0 until a.length()) out.add(fromJson(a.getJSONObject(i)))
            return out
        }

        /**
         * Whisper-Sätze in Untertitel-Blöcke schneiden: höchstens [maxWords] Wörter bzw.
         * [maxMs] Millisekunden pro Block, damit die Einblendungen lesbar kurz bleiben.
         */
        fun chunk(words: List<Word>, maxWords: Int = 6, maxMs: Long = 3500, maxChars: Int = 38): List<Caption> {
            val out = ArrayList<Caption>()
            var cur = ArrayList<Word>()
            fun flush() {
                if (cur.isEmpty()) return
                out.add(Caption(cur.first().startMs, cur.last().endMs, cur.joinToString(" ") { it.text }, cur.toList()))
                cur = ArrayList()
            }
            for (w in words) {
                val nextChars = cur.sumOf { it.text.length + 1 } + w.text.length
                if (cur.isNotEmpty() && (cur.size >= maxWords || w.endMs - cur.first().startMs > maxMs || nextChars > maxChars)) flush()
                cur.add(w)
            }
            flush()
            // Lücken schließen: Block bleibt bis kurz vor dem nächsten stehen (max. 1,5 s)
            return out.mapIndexed { i, c ->
                val nextStart = out.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
                val end = minOf(maxOf(c.endMs, c.startMs + 600), nextStart - 40, c.endMs + 1500)
                c.copy(endMs = maxOf(end, c.startMs + 300))
            }
        }
    }
}
