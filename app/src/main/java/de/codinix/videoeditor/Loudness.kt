package de.codinix.videoeditor

import kotlin.math.pow

/**
 * Umrechnung Regler-Prozent (0..2 = 0..200 %) → Amplitudenfaktor.
 * Das Gehör empfindet Lautstärke etwa als Amplitude^0,6 (Stevens). Ein Regler, der
 * „halb so laut“ meint, muss die Amplitude daher auf ~0,31 setzen, nicht auf 0,5.
 * Über 100 % geht es linear weiter, um Übersteuern in Grenzen zu halten.
 */
object Loudness {
    fun gain(setting: Float): Float = when {
        setting <= 0.001f -> 0f
        setting < 1f -> setting.toDouble().pow(1.0 / 0.6).toFloat()
        else -> setting.coerceAtMost(2f)
    }
}
