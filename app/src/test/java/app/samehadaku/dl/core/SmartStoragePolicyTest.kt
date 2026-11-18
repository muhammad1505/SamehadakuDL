package app.samehadaku.dl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartStoragePolicyTest {

    @Test
    fun `grace days are normalized to an offered option`() {
        assertEquals(1, SmartStoragePolicy.normalizeGraceDays(0))
        assertEquals(1, SmartStoragePolicy.normalizeGraceDays(1))
        assertEquals(3, SmartStoragePolicy.normalizeGraceDays(3))
        assertEquals(7, SmartStoragePolicy.normalizeGraceDays(7))
        assertEquals(14, SmartStoragePolicy.normalizeGraceDays(14))
        assertEquals(7, SmartStoragePolicy.normalizeGraceDays(8))
    }

    @Test
    fun `a watched file becomes due exactly after the grace period`() {
        val day = 24L * 60L * 60L * 1000L
        val watchedAt = 1_000_000L

        assertFalse(SmartStoragePolicy.isDue(watchedAt, watchedAt, 3))
        assertFalse(SmartStoragePolicy.isDue(watchedAt, watchedAt + 3 * day - 1, 3))
        assertTrue(SmartStoragePolicy.isDue(watchedAt, watchedAt + 3 * day, 3))
        assertTrue(SmartStoragePolicy.isDue(watchedAt, watchedAt + 4 * day, 3))
    }

    @Test
    fun `future or missing markers are never due`() {
        assertFalse(SmartStoragePolicy.isDue(0L, System.currentTimeMillis(), 3))
        assertFalse(SmartStoragePolicy.isDue(2_000L, 1_000L, 3))
    }

    @Test
    fun `manual cleanup bypasses grace only for valid markers`() {
        val day = 24L * 60L * 60L * 1000L
        val watchedAt = 1_000_000L

        assertFalse(
            SmartStoragePolicy.shouldDelete(
                watchedAt,
                watchedAt + day,
                3,
                ignoreGrace = false,
            ),
        )
        assertTrue(
            SmartStoragePolicy.shouldDelete(
                watchedAt,
                watchedAt + day,
                3,
                ignoreGrace = true,
            ),
        )
        assertFalse(
            SmartStoragePolicy.shouldDelete(
                0L,
                System.currentTimeMillis(),
                3,
                ignoreGrace = true,
            ),
        )
        assertFalse(
            SmartStoragePolicy.shouldDelete(
                watchedAt,
                watchedAt - 1,
                3,
                ignoreGrace = true,
            ),
        )
    }

    @Test
    fun `accumulated watching counts as watched from eighty percent`() {
        val duration = 24L * 60L * 1000L

        assertFalse(SmartStoragePolicy.countsAsWatched(19 * 60_000L, duration))
        assertTrue(SmartStoragePolicy.countsAsWatched(20 * 60_000L, duration))
        assertTrue(SmartStoragePolicy.countsAsWatched(duration, duration))
    }

    @Test
    fun `watching from two sittings adds up`() {
        val duration = 24L * 60L * 1000L
        val firstSitting = 10 * 60_000L
        val secondSitting = 10 * 60_000L

        assertFalse(SmartStoragePolicy.countsAsWatched(firstSitting, duration))
        assertTrue(
            SmartStoragePolicy.countsAsWatched(firstSitting + secondSitting, duration),
        )
    }

    @Test
    fun `unknown duration or empty watching is not watched`() {
        assertFalse(SmartStoragePolicy.countsAsWatched(10L, 0L))
        assertFalse(SmartStoragePolicy.countsAsWatched(0L, 100L))
        assertFalse(SmartStoragePolicy.countsAsWatched(-1L, 100L))
    }

    @Test
    fun `a single sitting longer than three times the duration is discarded`() {
        val duration = 24L * 60L * 1000L

        assertEquals(0L, SmartStoragePolicy.usableSessionMs(73 * 60_000L, duration))
        assertEquals(72 * 60_000L, SmartStoragePolicy.usableSessionMs(72 * 60_000L, duration))
        assertEquals(20 * 60_000L, SmartStoragePolicy.usableSessionMs(20 * 60_000L, duration))
        assertEquals(0L, SmartStoragePolicy.usableSessionMs(20 * 60_000L, 0L))
        assertEquals(0L, SmartStoragePolicy.usableSessionMs(-5L, duration))
    }

    @Test
    fun `deletion requires a recorded positive size`() {
        assertFalse(SmartStoragePolicy.hasUsableSize(0L))
        assertFalse(SmartStoragePolicy.hasUsableSize(-1L))
        assertTrue(SmartStoragePolicy.hasUsableSize(1L))
    }

    @Test
    fun `folder keys are stable and sanitized`() {
        assertEquals(
            SmartStoragePolicy.key("one-piece", "01.mkv"),
            SmartStoragePolicy.key("one-piece", "01.mkv"),
        )
        assertEquals(
            SmartStoragePolicy.key("one:piece", "01.mkv"),
            SmartStoragePolicy.key("one_piece", "01.mkv"),
        )
    }

    @Test
    fun `generated download names are recognized for legacy adoption`() {
        assertTrue(
            SmartStoragePolicy.looksLikeAppDownloadName(
                "one-piece-episode-1096_MKV_1080p.mkv",
            ),
        )
        assertTrue(SmartStoragePolicy.looksLikeAppDownloadName("episode_MP4_720p.MP4"))
        assertTrue(SmartStoragePolicy.looksLikeAppDownloadName("movie_X265_HD.mkv"))
        assertTrue(SmartStoragePolicy.looksLikeAppDownloadName("special_MKV_480p.webm"))
    }

    @Test
    fun `arbitrary user videos are not treated as app downloads`() {
        assertFalse(SmartStoragePolicy.looksLikeAppDownloadName("video liburan.mp4"))
        assertFalse(SmartStoragePolicy.looksLikeAppDownloadName("episode_AVI_720p.avi"))
        assertFalse(SmartStoragePolicy.looksLikeAppDownloadName("episode_MKV_4k.mkv"))
        assertFalse(SmartStoragePolicy.looksLikeAppDownloadName("episode_MKV_1080p.part"))
        assertFalse(SmartStoragePolicy.looksLikeAppDownloadName("episode_MKV_1080p.rar"))
    }
}
