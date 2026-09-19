package de.codinix.videoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.codinix.videoeditor.overlay.ImageOverlay
import de.codinix.videoeditor.overlay.TextOverlay
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

    /** Tonspur eines bereits entfernten Video-Overlays. */
    data class AudioTrack(val file: File, val startOffsetMs: Long, val endOffsetMs: Long, val volume: Float, val durationMs: Long,
                          val events: List<VideoOverlay.Event> = emptyList())

    private fun eventsToJson(events: List<VideoOverlay.Event>): JSONArray {
        val a = JSONArray()
        events.forEach { a.put(JSONObject().put("atMs", it.atMs).put("gain", it.gain.toDouble()).put("playing", it.playing)) }
        return a
    }
    private fun eventsFromJson(a: JSONArray?): List<VideoOverlay.Event> {
        if (a == null) return emptyList()
        val out = ArrayList<VideoOverlay.Event>()
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            out.add(VideoOverlay.Event(e.getLong("atMs"), e.getDouble("gain").toFloat(), e.optBoolean("playing", true)))
        }
        return out
    }

    class Loaded(
        val segments: List<Pair<File, Long>>,
        val overlays: List<Overlay>,
        val lensFacing: Int,
        val qualityLabel: String?,
        val audioTracks: List<AudioTrack> = emptyList()
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
        qualityLabel: String?,
        audioTracks: List<AudioTrack> = emptyList()
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
                is de.codinix.videoeditor.overlay.CameraOverlay -> {
                    j.put("type", "camera").put("shape", o.shape).put("border", o.border)
                }
                is TextOverlay -> {
                    j.put("type", "text").put("text", o.text).put("color", o.colorArgb)
                        .put("background", o.background).put("bgColor", o.bgColorArgb ?: JSONObject.NULL)
                }
                is VideoOverlay -> {
                    val dest = File(dir, "overlay_$i.mp4")
                    if (!o.file.renameTo(dest)) { o.file.copyTo(dest, overwrite = true); o.file.delete() }
                    j.put("type", "video").put("file", dest.name)
                        .put("volume", o.volume.toDouble())
                        .put("startOffsetMs", o.startOffsetMs)
                        .put("isBackground", o.isBackground)
                        .put("events", eventsToJson(o.events))
                }
            }
            ovArr.put(j)
        }

        val trArr = JSONArray()
        audioTracks.forEachIndexed { i, t ->
            val dest = File(dir, "audio_$i.mp4")
            if (!t.file.renameTo(dest)) { t.file.copyTo(dest, overwrite = true); t.file.delete() }
            trArr.put(JSONObject().put("file", dest.name)
                .put("startOffsetMs", t.startOffsetMs).put("endOffsetMs", t.endOffsetMs)
                .put("volume", t.volume.toDouble()).put("durationMs", t.durationMs)
                .put("events", eventsToJson(t.events)))
        }

        val meta = JSONObject()
            .put("createdAt", id.toLong())
            .put("lensFacing", lensFacing)
            .put("quality", qualityLabel ?: JSONObject.NULL)
            .put("segments", segArr)
            .put("overlays", ovArr)
            .put("audioTracks", trArr)
        File(dir, "meta.json").writeText(meta.toString())

        val dur = segments.sumOf { it.second }
        return Info(id, dir, id.toLong(), segments.size, dur)
    }

    /**
     * Stellt aus einer Sitzungsdatei (absolute Pfade, geschrieben während der Arbeit) einen
     * Entwurf her – nach einem Absturz. Segment- und Overlay-Dateien werden verschoben.
     */
    fun recoverFromSession(session: JSONObject): Info? {
        val segs = session.optJSONArray("segments") ?: return null
        if (segs.length() == 0) return null
        val id = System.currentTimeMillis().toString()
        val dir = File(root, id).apply { mkdirs() }
        val segArr = JSONArray()
        for (i in 0 until segs.length()) {
            val o = segs.getJSONObject(i)
            val src = File(o.getString("path"))
            if (!src.exists()) continue
            val dest = File(dir, "seg_$i.mp4")
            if (!src.renameTo(dest)) { src.copyTo(dest, overwrite = true); src.delete() }
            segArr.put(JSONObject().put("file", dest.name).put("durationMs", o.getLong("durationMs")))
        }
        if (segArr.length() == 0) { dir.deleteRecursively(); return null }
        val ovArr = JSONArray()
        val ovs = session.optJSONArray("overlays") ?: JSONArray()
        for (i in 0 until ovs.length()) {
            val o = ovs.getJSONObject(i)
            if (o.optString("type") == "text" || o.optString("type") == "camera") { ovArr.put(o); continue }
            val src = File(o.getString("path"))
            if (!src.exists()) continue
            val ext = if (o.getString("type") == "video") "mp4" else "png"
            val dest = File(dir, "overlay_$i.$ext")
            if (!src.renameTo(dest)) { src.copyTo(dest, overwrite = true); src.delete() }
            val j = JSONObject(o.toString())
            j.remove("path"); j.put("file", dest.name)
            ovArr.put(j)
        }
        val trArr = JSONArray()
        val trs = session.optJSONArray("audioTracks") ?: JSONArray()
        for (i in 0 until trs.length()) {
            val t = trs.getJSONObject(i)
            val src = File(t.getString("path"))
            if (!src.exists()) continue
            val dest = File(dir, "audio_$i.mp4")
            if (!src.renameTo(dest)) { src.copyTo(dest, overwrite = true); src.delete() }
            val j = JSONObject(t.toString()); j.remove("path"); j.put("file", dest.name)
            trArr.put(j)
        }
        val meta = JSONObject()
            .put("createdAt", id.toLong())
            .put("lensFacing", session.optInt("lensFacing", 1))
            .put("quality", session.opt("quality") ?: JSONObject.NULL)
            .put("segments", segArr)
            .put("overlays", ovArr)
            .put("audioTracks", trArr)
        File(dir, "meta.json").writeText(meta.toString())
        var dur = 0L
        for (i in 0 until segArr.length()) dur += segArr.getJSONObject(i).getLong("durationMs")
        return Info(id, dir, id.toLong(), segArr.length(), dur)
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
            val cx = o.getDouble("cx").toFloat(); val cy = o.getDouble("cy").toFloat()
            val w = o.getDouble("widthFrac").toFloat(); val rot = o.getDouble("rotationDeg").toFloat()
            if (o.optString("type", "image") == "camera") {
                overlays.add(de.codinix.videoeditor.overlay.CameraOverlay(Overlay.newId(), cx, cy, w, rot,
                    o.optInt("shape", 0), o.optBoolean("border", true)))
                continue
            }
            if (o.optString("type", "image") == "text") {
                val bg: Int? = if (o.has("bgColor") && !o.isNull("bgColor")) o.getInt("bgColor")
                    else if (o.optBoolean("background", false)) 0xC8000000.toInt() else null
                overlays.add(TextOverlay(Overlay.newId(), o.getString("text"), o.getInt("color"), bg, cx, cy, w, rot))
                continue
            }
            val src = File(info.dir, o.getString("file"))
            if (o.optString("type", "image") == "video") {
                val dest = File(targetDir.parentFile ?: targetDir, "overlay_video_${System.currentTimeMillis()}_$i.mp4")
                if (!src.renameTo(dest)) src.copyTo(dest, overwrite = true)
                val vo = VideoOverlay(Overlay.newId(), dest, cx, cy, w, rot,
                    volume = if (o.has("volume")) o.getDouble("volume").toFloat() else (if (o.optBoolean("soundOn", true)) 1f else 0f),
                    startOffsetMs = o.optLong("startOffsetMs", 0L))
                vo.isBackground = o.optBoolean("isBackground", false)
                vo.events.addAll(eventsFromJson(o.optJSONArray("events")))
                overlays.add(vo)
            } else {
                val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: continue
                overlays.add(ImageOverlay(Overlay.newId(), bmp, cx, cy, w, rot))
            }
        }
        val quality = if (j.isNull("quality")) null else j.getString("quality")
        val tracks = ArrayList<AudioTrack>()
        val trs = j.optJSONArray("audioTracks") ?: JSONArray()
        for (i in 0 until trs.length()) {
            val t = trs.getJSONObject(i)
            val src = File(info.dir, t.getString("file"))
            if (!src.exists()) continue
            val dest = File(targetDir.parentFile ?: targetDir, "overlay_videos/hist_${System.currentTimeMillis()}_$i.mp4")
            dest.parentFile?.mkdirs()
            if (!src.renameTo(dest)) src.copyTo(dest, overwrite = true)
            tracks.add(AudioTrack(dest, t.getLong("startOffsetMs"), t.getLong("endOffsetMs"),
                t.getDouble("volume").toFloat(), t.getLong("durationMs"), eventsFromJson(t.optJSONArray("events"))))
        }
        val loaded = Loaded(segments, overlays, j.getInt("lensFacing"), quality, tracks)
        delete(info)
        return loaded
    }

    fun delete(info: Info) {
        info.dir.deleteRecursively()
    }
}
