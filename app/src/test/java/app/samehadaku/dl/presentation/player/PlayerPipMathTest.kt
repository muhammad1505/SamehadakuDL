package app.samehadaku.dl.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPipMathTest {

    @Test
    fun `normal widescreen ratio is kept`() {
        assertEquals(16f / 9f, clampPipAspectRatio(16f / 9f), 0.0001f)
    }

    @Test
    fun `vertical video is clamped to the minimum`() {
        assertEquals(MIN_PIP_ASPECT_RATIO, clampPipAspectRatio(9f / 16f), 0.0001f)
        assertEquals(MIN_PIP_ASPECT_RATIO, clampPipAspectRatio(0.1f), 0.0001f)
    }

    @Test
    fun `ultrawide video is clamped to the maximum`() {
        assertEquals(MAX_PIP_ASPECT_RATIO, clampPipAspectRatio(4f), 0.0001f)
    }

    @Test
    fun `missing or broken video size falls back to widescreen`() {
        assertEquals(16f / 9f, clampPipAspectRatio(Float.NaN), 0.0001f)
        assertEquals(16f / 9f, clampPipAspectRatio(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(16f / 9f, clampPipAspectRatio(0f), 0.0001f)
        assertEquals(16f / 9f, clampPipAspectRatio(-1f), 0.0001f)
    }
}
