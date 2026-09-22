package de.codinix.videoeditor.gl

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Farbfilter für das Kamerabild. Die GLSL-Funktion und die Kotlin-Vorschau rechnen dasselbe,
 * damit die Auswahlkarten dem Ergebnis entsprechen.
 */
object ColorFilters {
    val NAMES = listOf("Original", "Warm", "Kalt", "Schwarzweiß", "Vintage", "Kräftig", "Matt", "Kino", "Pastell")

    /** GLSL: vec3 grade(vec3 c, int f) – wird in die Kamera-Shader eingesetzt. */
    const val GLSL = """
        vec3 grade(vec3 c, int f) {
            if (f == 0) return c;
            float l = dot(c, vec3(0.299, 0.587, 0.114));
            if (f == 1) { c = c * vec3(1.08, 1.02, 0.92); c = mix(vec3(l), c, 1.1); return clamp(c, 0.0, 1.0); }
            if (f == 2) { c = c * vec3(0.92, 1.0, 1.10); return clamp(c, 0.0, 1.0); }
            if (f == 3) { return vec3(clamp((l - 0.5) * 1.1 + 0.5, 0.0, 1.0)); }
            if (f == 4) { vec3 sep = vec3(l) * vec3(1.15, 1.0, 0.8); c = mix(c, sep, 0.65); c = c * 0.9 + 0.06; return clamp(c, 0.0, 1.0); }
            if (f == 5) { c = mix(vec3(l), c, 1.35); c = (c - 0.5) * 1.15 + 0.5; return clamp(c, 0.0, 1.0); }
            if (f == 6) { c = mix(vec3(l), c, 0.85); c = c * 0.85 + 0.09; return clamp(c, 0.0, 1.0); }
            if (f == 7) { c = (c - 0.5) * 1.2 + 0.5; c = c * vec3(0.95, 1.0, 1.08); c.rgb = mix(c.rgb, c.rgb * vec3(1.05, 0.98, 0.9), smoothstep(0.5, 1.0, l)); return clamp(c, 0.0, 1.0); }
            if (f == 8) { c = mix(vec3(l), c, 0.75); c = c * 0.92 + 0.08; return clamp(c, 0.0, 1.0); }
            return c;
        }
    """

    private fun clamp(v: Float) = v.coerceIn(0f, 1f)
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Kotlin-Fassung derselben Kurven (für Vorschaukarten). */
    fun grade(r0: Float, g0: Float, b0: Float, f: Int): FloatArray {
        var r = r0; var g = g0; var b = b0
        val l = 0.299f * r + 0.587f * g + 0.114f * b
        when (f) {
            1 -> { r *= 1.08f; g *= 1.02f; b *= 0.92f; r = mix(l, r, 1.1f); g = mix(l, g, 1.1f); b = mix(l, b, 1.1f) }
            2 -> { r *= 0.92f; b *= 1.10f }
            3 -> { val v = (l - 0.5f) * 1.1f + 0.5f; r = v; g = v; b = v }
            4 -> { r = mix(r, l * 1.15f, 0.65f) * 0.9f + 0.06f; g = mix(g, l, 0.65f) * 0.9f + 0.06f; b = mix(b, l * 0.8f, 0.65f) * 0.9f + 0.06f }
            5 -> { r = (mix(l, r, 1.35f) - 0.5f) * 1.15f + 0.5f; g = (mix(l, g, 1.35f) - 0.5f) * 1.15f + 0.5f; b = (mix(l, b, 1.35f) - 0.5f) * 1.15f + 0.5f }
            6 -> { r = mix(l, r, 0.85f) * 0.85f + 0.09f; g = mix(l, g, 0.85f) * 0.85f + 0.09f; b = mix(l, b, 0.85f) * 0.85f + 0.09f }
            7 -> {
                r = (r - 0.5f) * 1.2f + 0.5f; g = (g - 0.5f) * 1.2f + 0.5f; b = (b - 0.5f) * 1.2f + 0.5f
                r *= 0.95f; b *= 1.08f
                val t = ((l - 0.5f) / 0.5f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                r = mix(r, r * 1.05f, t); g = mix(g, g * 0.98f, t); b = mix(b, b * 0.9f, t)
            }
            8 -> { r = mix(l, r, 0.75f) * 0.92f + 0.08f; g = mix(l, g, 0.75f) * 0.92f + 0.08f; b = mix(l, b, 0.75f) * 0.92f + 0.08f }
        }
        return floatArrayOf(clamp(r), clamp(g), clamp(b))
    }

    /** Vorschau: Testbild (Himmel, Haut, Grün, Grau) durch den Filter gerechnet. */
    fun preview(f: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val fx = x.toFloat() / w; val fy = y.toFloat() / h
            // Oben Himmelsblau→Orange, unten Grün→Hautton, mit Helligkeitsverlauf
            val r0 = mix(mix(0.35f, 0.95f, fx), mix(0.25f, 0.85f, fx), fy)
            val g0 = mix(mix(0.6f, 0.6f, fx), mix(0.55f, 0.6f, fx), fy)
            val b0 = mix(mix(0.9f, 0.3f, fx), mix(0.25f, 0.5f, fx), fy)
            val c = grade(r0, g0, b0, f)
            px[y * w + x] = Color.rgb((c[0] * 255).toInt(), (c[1] * 255).toInt(), (c[2] * 255).toInt())
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }
}
