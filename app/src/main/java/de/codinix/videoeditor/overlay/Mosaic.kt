package de.codinix.videoeditor.overlay

import android.graphics.Bitmap
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mosaik: der Frame wird in feste Kacheln geteilt. Jede Kachel zeigt die Kamera oder ein
 * Bild (Videos folgen), mit Zoom und Verschiebung des Inhalts; Bereiche außerhalb des
 * Inhalts bekommen die Füllfarbe. Trennlinien in Füllfarbe oder als Signatur-Regenbogen.
 * Koordinaten der Kacheln sind Anteile des sichtbaren Frames (0..1, oben links).
 */
class Mosaic(var layout: Int) {

    class Tile(
        var kind: Int = KIND_CAMERA,
        var bitmap: Bitmap? = null,
        /** Verschiebung des Inhalts, -1..1 innerhalb des erlaubten Bereichs. */
        var offX: Float = 0f,
        var offY: Float = 0f,
        /** 1 = formatfüllend, < 1 kleiner mit Füllfarbe, > 1 hineingezoomt. */
        var zoom: Float = 1f,
        var fillWhite: Boolean = false
    ) {
        fun snapshot() = TileSnapshot(kind, bitmap, offX, offY, zoom, fillWhite)
    }

    val tiles: MutableList<Tile> = MutableList(tileCount(layout)) { Tile() }
    var gapWhite = false
    var rainbowGaps = false
    var selected: Int = -1

    fun setLayout(newLayout: Int) {
        val old = tiles.toList()
        layout = newLayout
        tiles.clear()
        for (i in 0 until tileCount(newLayout)) tiles.add(old.getOrNull(i) ?: Tile())
        if (selected >= tiles.size) selected = -1
    }

    fun snapshot() = MosaicSnapshot(layout, tiles.map { it.snapshot() }, gapWhite, rainbowGaps)

    fun toJson(imageFiles: List<String?>): JSONObject {
        val arr = JSONArray()
        tiles.forEachIndexed { i, t ->
            arr.put(JSONObject().put("kind", t.kind).put("offX", t.offX.toDouble()).put("offY", t.offY.toDouble())
                .put("zoom", t.zoom.toDouble()).put("fillWhite", t.fillWhite).put("file", imageFiles.getOrNull(i) ?: JSONObject.NULL))
        }
        return JSONObject().put("layout", layout).put("gapWhite", gapWhite).put("rainbow", rainbowGaps).put("tiles", arr)
    }

    companion object {
        const val LAYOUT_NONE = 0
        const val LAYOUT_2_ROWS = 1
        const val LAYOUT_2_COLS = 2
        const val LAYOUT_3_ROWS = 3
        const val LAYOUT_3_COLS = 4
        const val LAYOUT_2X2 = 5
        val LAYOUT_NAMES = listOf("Aus", "2 übereinander", "2 nebeneinander", "3 übereinander", "3 nebeneinander", "2 × 2")

        const val KIND_CAMERA = 0
        const val KIND_IMAGE = 1
        const val KIND_VIDEO = 2

        fun tileCount(layout: Int) = when (layout) {
            LAYOUT_2_ROWS, LAYOUT_2_COLS -> 2
            LAYOUT_3_ROWS, LAYOUT_3_COLS -> 3
            LAYOUT_2X2 -> 4
            else -> 0
        }

        /** Kachelrechtecke ohne Zwischenraum, Anteile des Frames. */
        fun rects(layout: Int): List<RectF> = when (layout) {
            LAYOUT_2_ROWS -> listOf(RectF(0f, 0f, 1f, .5f), RectF(0f, .5f, 1f, 1f))
            LAYOUT_2_COLS -> listOf(RectF(0f, 0f, .5f, 1f), RectF(.5f, 0f, 1f, 1f))
            LAYOUT_3_ROWS -> listOf(RectF(0f, 0f, 1f, 1f / 3), RectF(0f, 1f / 3, 1f, 2f / 3), RectF(0f, 2f / 3, 1f, 1f))
            LAYOUT_3_COLS -> listOf(RectF(0f, 0f, 1f / 3, 1f), RectF(1f / 3, 0f, 2f / 3, 1f), RectF(2f / 3, 0f, 1f, 1f))
            LAYOUT_2X2 -> listOf(RectF(0f, 0f, .5f, .5f), RectF(.5f, 0f, 1f, .5f), RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f))
            else -> emptyList()
        }

        fun fromJson(o: JSONObject, loadBitmap: (String) -> Bitmap?): Mosaic {
            val m = Mosaic(o.optInt("layout", LAYOUT_NONE))
            m.gapWhite = o.optBoolean("gapWhite", false)
            m.rainbowGaps = o.optBoolean("rainbow", false)
            val arr = o.optJSONArray("tiles") ?: JSONArray()
            for (i in 0 until minOf(arr.length(), m.tiles.size)) {
                val t = arr.getJSONObject(i)
                val tile = m.tiles[i]
                tile.kind = t.optInt("kind", KIND_CAMERA)
                tile.offX = t.optDouble("offX", 0.0).toFloat(); tile.offY = t.optDouble("offY", 0.0).toFloat()
                tile.zoom = t.optDouble("zoom", 1.0).toFloat(); tile.fillWhite = t.optBoolean("fillWhite", false)
                if (tile.kind == KIND_IMAGE) {
                    val f = if (t.isNull("file")) null else t.optString("file")
                    tile.bitmap = f?.let(loadBitmap)
                    if (tile.bitmap == null) tile.kind = KIND_CAMERA
                }
            }
            return m
        }
    }
}

data class TileSnapshot(val kind: Int, val bitmap: Bitmap?, val offX: Float, val offY: Float, val zoom: Float, val fillWhite: Boolean)
data class MosaicSnapshot(val layout: Int, val tiles: List<TileSnapshot>, val gapWhite: Boolean, val rainbowGaps: Boolean)
