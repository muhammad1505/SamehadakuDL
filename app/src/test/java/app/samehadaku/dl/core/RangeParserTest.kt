package app.samehadaku.dl.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeParserTest {

    @Test
    fun `empty text selects all episodes`() {
        assertEquals(listOf(1, 2, 3, 4, 5), RangeParser.parse("", 5))
        assertEquals(listOf(1, 2, 3, 4, 5), RangeParser.parse("   ", 5))
    }

    @Test
    fun `single number`() {
        assertEquals(listOf(3), RangeParser.parse("3", 10))
    }

    @Test
    fun `simple range`() {
        assertEquals(listOf(2, 3, 4, 5), RangeParser.parse("2-5", 10))
    }

    @Test
    fun `mixed list and ranges`() {
        assertEquals(listOf(1, 4, 7, 8, 9), RangeParser.parse("1,4,7-9", 12))
    }

    @Test
    fun `out of range values are clamped away`() {
        assertEquals(listOf(8, 9, 10), RangeParser.parse("0,8-20", 10))
        assertEquals(emptyList<Int>(), RangeParser.parse("99", 10))
    }

    @Test
    fun `garbage is ignored`() {
        assertEquals(emptyList<Int>(), RangeParser.parse("abc", 10))
        assertEquals(emptyList<Int>(), RangeParser.parse("--", 10))
        assertEquals(emptyList<Int>(), RangeParser.parse("5-2", 10))
    }
}
