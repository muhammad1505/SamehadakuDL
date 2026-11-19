package app.samehadaku.dl.data.autocheck

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCheckPolicyTest {

    private val intervalMinutes = 60
    private val intervalMillis = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())

    @Test
    fun `never checked is due`() {
        assertTrue(isAutoCheckDue(lastCheckAt = 0L, now = 1_000L, intervalMinutes = intervalMinutes))
    }

    @Test
    fun `just before the interval is not due`() {
        val now = 10 * intervalMillis
        assertFalse(
            isAutoCheckDue(
                lastCheckAt = now - intervalMillis + 1,
                now = now,
                intervalMinutes = intervalMinutes,
            ),
        )
    }

    @Test
    fun `exact interval is due`() {
        val now = 10 * intervalMillis
        assertTrue(
            isAutoCheckDue(
                lastCheckAt = now - intervalMillis,
                now = now,
                intervalMinutes = intervalMinutes,
            ),
        )
    }

    @Test
    fun `clock moving backwards does not retrigger`() {
        assertFalse(
            isAutoCheckDue(
                lastCheckAt = 5_000L,
                now = 1_000L,
                intervalMinutes = intervalMinutes,
            ),
        )
    }

    @Test
    fun `interval below WorkManager minimum is clamped`() {
        val now = 10 * TimeUnit.MINUTES.toMillis(MIN_AUTO_CHECK_MINUTES.toLong())
        val minimum = TimeUnit.MINUTES.toMillis(MIN_AUTO_CHECK_MINUTES.toLong())
        assertFalse(
            isAutoCheckDue(
                lastCheckAt = now - minimum + 1,
                now = now,
                intervalMinutes = 1,
            ),
        )
        assertTrue(
            isAutoCheckDue(
                lastCheckAt = now - minimum,
                now = now,
                intervalMinutes = 1,
            ),
        )
    }
}
