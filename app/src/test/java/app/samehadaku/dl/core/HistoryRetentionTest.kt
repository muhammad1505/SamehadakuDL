package app.samehadaku.dl.core

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRetentionTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 400L * day

    @Test
    fun `disabled keeps everything`() {
        assertFalse(HistoryRetention.isExpired(now - 300 * day, now, 0))
    }

    @Test
    fun `entry just inside the window stays`() {
        assertFalse(HistoryRetention.isExpired(now - 7 * day + 1, now, 7))
    }

    @Test
    fun `entry exactly at the window expires`() {
        assertTrue(HistoryRetention.isExpired(now - 7 * day, now, 7))
    }

    @Test
    fun `entry older than the window expires`() {
        assertTrue(HistoryRetention.isExpired(now - 30 * day, now, 7))
    }

    @Test
    fun `missing timestamp never expires`() {
        assertFalse(HistoryRetention.isExpired(0L, now, 3))
    }

    @Test
    fun `clock moving backwards keeps the entry`() {
        assertFalse(HistoryRetention.isExpired(now + day, now, 1))
    }

    @Test
    fun `negative retention is treated as disabled`() {
        assertFalse(HistoryRetention.isExpired(now - 100 * day, now, -7))
    }
}
