package de.codinix.videoeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Volume slider (0..2 = 0..200 %) → amplitude factor, perceptual below 100 %, linear above. */
class LoudnessTest {
    private val eps = 1e-4f

    @Test fun zeroAndNearZero_areSilent() {
        assertEquals(0f, Loudness.gain(0f), 0f)
        assertEquals(0f, Loudness.gain(0.0005f), 0f)
        assertEquals(0f, Loudness.gain(-1f), 0f)
    }

    @Test fun hundredPercent_isUnity() = assertEquals(1f, Loudness.gain(1f), eps)

    @Test fun halfSlider_isAboutPointThreeOne() =
        // 0.5^(1/0.6) ≈ 0.315: "half as loud" needs much less than half the amplitude
        assertEquals(0.315f, Loudness.gain(0.5f), 0.001f)

    @Test fun aboveHundredPercent_isLinear() = assertEquals(1.5f, Loudness.gain(1.5f), eps)

    @Test fun cappedAtTwoHundredPercent() = assertEquals(2f, Loudness.gain(3f), eps)

    @Test fun gainIsMonotonic() {
        var prev = -1f
        var s = 0f
        while (s <= 2f) {
            val g = Loudness.gain(s)
            assertTrue("gain($s)=$g < $prev", g >= prev)
            prev = g
            s += 0.01f
        }
    }
}
