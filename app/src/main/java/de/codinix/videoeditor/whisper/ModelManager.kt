package de.codinix.videoeditor.whisper

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Lädt und verwaltet Whisper-Modelle im privaten App-Speicher. */
class ModelManager(context: Context) {
    private val dir = File(context.filesDir, "models").apply { mkdirs() }

    enum class Model(val fileName: String, val label: String, val approxMb: Int) {
        TINY("ggml-tiny.bin", "Tiny (schnell)", 75),
        BASE("ggml-base.bin", "Base (genauer)", 142),
        SMALL("ggml-small.bin", "Small (am genauesten, langsam)", 466);

        val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"
    }

    fun file(model: Model) = File(dir, model.fileName)
    fun isAvailable(model: Model) = file(model).let { it.exists() && it.length() > 1_000_000 }

    /** Blockierend; auf Hintergrund-Thread aufrufen. */
    fun download(model: Model, onProgress: (Int) -> Unit) {
        val target = file(model)
        val tmp = File(dir, model.fileName + ".part")
        val conn = URL(model.url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20_000; conn.readTimeout = 60_000
        conn.connect()
        if (conn.responseCode !in 200..299) throw IllegalStateException("Download fehlgeschlagen: HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; onProgress(p) }
                    }
                }
            }
        }
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
    }
}
