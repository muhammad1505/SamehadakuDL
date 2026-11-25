package app.samehadaku.dl.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboPlanTest {

    private val megabyte = 1024L * 1024

    @Test
    fun `segments cover the file exactly once`() {
        val total = 100 * megabyte
        val segments = planSegments(total, 4)

        assertEquals(4, segments.size)
        assertEquals(0L, segments.first().start)
        assertEquals(total - 1, segments.last().endInclusive)
        assertEquals(total, segments.sumOf { it.length })
        segments.zipWithNext().forEach { (current, next) ->
            assertEquals(current.endInclusive + 1, next.start)
        }
    }

    @Test
    fun `small files collapse into one connection`() {
        assertEquals(1, planSegments(3 * megabyte, 4).size)
    }

    @Test
    fun `too many connections are clamped`() {
        assertEquals(1, planSegments(100 * megabyte, 0).size)
        assertEquals(
            MAX_TURBO_CONNECTIONS,
            planSegments(100 * megabyte, 99).size,
        )
    }

    @Test
    fun `unknown size has no plan`() {
        assertTrue(planSegments(0L, 4).isEmpty())
        assertTrue(planSegments(-5L, 4).isEmpty())
    }

    @Test
    fun `chunk sizes stay close to each other`() {
        val segments = planSegments(40 * megabyte, 4)
        val lengths = segments.map { it.length }
        assertTrue(lengths.all { length -> length >= 4 * megabyte })
        assertTrue(lengths.max() - lengths.min() <= 1)
    }

    @Test
    fun `file just above the turbo threshold still splits`() {
        val segments = planSegments(MIN_TURBO_TOTAL_BYTES, TURBO_CONNECTIONS)
        assertEquals(2, segments.size)
        assertEquals(MIN_TURBO_TOTAL_BYTES, segments.sumOf { it.length })
    }
}
