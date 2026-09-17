package de.codinix.videoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.codinix.videoeditor.overlay.ImageOverlay
import de.codinix.videoeditor.overlay.Overlay
import de.codinix.videoeditor.overlay.VideoOverlay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Entwürfe: eine Aufnahme-Sitzung (Segmente + Overlays + Kameraeinstellungen) wird in
 * den privaten App-Speicher verschoben und kann später wieder geöffnet werden.
 * Dateien werden verschoben, nicht kopiert – 4K-Segmente sind groß.
 */
class DraftStore(context: Context) {

    private val root = File(context.filesDir, "drafts").apply { mkdirs() }

    data class Info(
        val id: String,
        val dir: File,
        val createdAt: Long,
        val segmentCount: Int,
        val durationMs: Long
    )

    class Loaded(
        val segments: List<Pair<File, Long>>,
        val overlays: List<Overlay>,
        val lensFacing: Int,
        val qualityLabel: String?
    )

    fun list(): List<Info> = root.listFiles()?.mapNotNull { dir ->
        val meta = File(dir, "meta.json")
        if (!meta.exists()) return@mapNotNull null
        try {
            val j = JSONObject(meta.readText())
            val segs = j.getJSONArray("segments")
            var dur = 0L
            for (i in 0 until segs.length()) dur += segs.getJSONObject(i).getLong("durationMs")
            Info(dir.name, dir, j.getLong("createdAt"), segs.length(), dur)
        } catch (e: Exception) { null }
    }?.sortedByDescending { it.createdAt } ?: emptyList()

    fun save(
        segments: List<Pair<File, Long>>,
        overlays: List<Overlay>,
        lensFacing: Int,
        qualityLabel: String?
    ): Info {
        val id = System.currentTimeMillis().toString()
        val dir = File(root, id).apply { mkdirs() }

        val segArr = JSONArray()
        segments.forEachIndexed { i, (file, dur) ->
            val dest = File(dir, "seg_$i.mp4")
            if (!file.renameTo(dest)) { file.copyTo(dest, overwrite = true); file.delete() }
            segArr.put(JSONObject().put("file", dest.name).put("durationMs", dur))
        }

        val ovArr = JSONArray()
        overlays.forEachIndexed { i, o ->
            val j = JSONObject()
                .put("cx", o.cx.toDouble()).put("cy", o.cy.toDouble())
                .put("widthFrac", o.widthFrac.toDouble())
                .put("rotationDeg", o.rotationDeg.toDouble())
            when (o) {
                is ImageOverlay -> {
                    val png = File(dir, "overlay_$i.png")
                    png.outputStream().use { o.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    j.put("type", "image").put("file", png.name)
                }
                is VideoOverlay -> {
                    val dest = File(dir, "overlay_$i.mp4")
                    if (!o.file.renameTo(dest)) { o.file.copyTo(dest, overwrite = true); o.file.delete() }
                    j.put("type", "video").put("file", dest.name)
                        .put("volume", o.volume.toDouble())
                        .put("startOffsetMs", o.startOffsetMs)
                }
            }
            ovArr.put(j)
        }

        val meta = JSONObject()
            .put("createdAt", id.toLong())
            .put("lensFacing", lensFacing)
            .put("quality", qualityLabel ?: JSONObject.NULL)
            .put("segments", segArr)
            .put("overlays", ovArr)
        File(dir, "meta.json").writeText(meta.toString())

        val dur = segments.sumOf { it.second }
        return Info(id, dir, id.toLong(), segments.size, dur)
    }

    /** Verschiebt die Segmente nach [targetDir] und löscht den Entwurf anschließend. */
    fun load(info: Info, targetDir: File): Loaded {
        val j = JSONObject(File(info.dir, "meta.json").readText())
        val segs = j.getJSONArray("segments")
        val segments = ArrayList<Pair<File, Long>>()
        for (i in 0 until segs.length()) {
            val o = segs.getJSONObject(i)
            val src = File(info.dir, o.getString("file"))
            val dest = File(targetDir, "seg_${System.currentTimeMillis()}_$i.mp4")
            if (!src.renameTo(dest)) { src.copyTo(dest, overwrite = true) }
            segments.add(dest to o.getLong("durationMs"))
        }
        val ovs = j.getJSONArray("overlays")
        val overlays = ArrayList<Overlay>()
        for (i in 0 until ovs.length()) {
            val o = ovs.getJSONObject(i)
            val src = File(info.dir, o.getString("file"))
            val cx = o.getDouble("cx").toFloat(); val cy = o.getDouble("cy").toFloat()
            val w = o.getDouble("widthFrac").toFloat(); val rot = o.getDouble("rotationDeg").toFloat()
            if (o.optString("type", "image") == "video") {
                val dest = File(targetDir.parentFile ?: targetDir, "overlay_video_${System.currentTimeMillis()}_$i.mp4")
                if (!src.renameTo(dest)) src.copyTo(dest, overwrite = true)
                overlays.add(VideoOverlay(Overlay.newId(), dest, cx, cy, w, rot,
                    volume = if (o.has("volume")) o.getDouble("volume").toFloat() else (if (o.optBoolean("soundOn", true)) 1f else 0f),
                    startOffsetMs = o.optLong("startOffsetMs", 0L)))
            } else {
                val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: continue
                overlays.add(ImageOverlay(Overlay.newId(), bmp, cx, cy, w, rot))
            }
        }
        val quality = if (j.isNull("quality")) null else j.getString("quality")
        val loaded = Loaded(segments, overlays, j.getInt("lensFacing"), quality)
        delete(info)
        return loaded
    }

    fun delete(info: Info) {
        info.dir.deleteRecursively()
    }
}
