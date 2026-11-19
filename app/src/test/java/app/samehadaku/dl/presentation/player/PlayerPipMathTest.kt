package app.samehadaku.dl.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPipMathTest {

    @Test
    fun `normal widescreen ratio is kept`() {
        assertEquals(16f / 9f, clampPipAspectRatio(16f / 9f), 0.0001f)
    }

    @Test
    fun `portrait video inside the supported range is kept`() {
        // 9:16 is taller than widescreen but still above Android's 0.42 floor,
        // so PiP must accept it instead of shrinking it to the minimum.
        assertEquals(9f / 16f, clampPipAspectRatio(9f / 16f), 0.0001f)
    }

    @Test
    fun `extremely tall video is clamped to the minimum`() {
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
