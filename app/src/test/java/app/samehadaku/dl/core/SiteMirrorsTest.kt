package app.samehadaku.dl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMirrorsTest {

    @Test
    fun `canonical episode is offered on live mirrors`() {
        val candidates = SiteMirrors.candidates(
            "https://samehadaku.how/blue-lock-season-2-episode-1/",
        )
        assertTrue(candidates.none { it.startsWith("https://samehadaku.how/") })
        assertTrue(
            candidates.contains("https://v2.samehadaku.how/blue-lock-season-2-episode-1/"),
        )
        assertTrue(
            candidates.contains(
                "https://samehadaku.li/blue-lock-season-2-episode-1-subtitle-indonesia/",
            ),
        )
    }

    @Test
    fun `wordpress episode is translated back to the canonical path`() {
        val candidates = SiteMirrors.candidates(
            "https://samehadaku.li/shuiro-no-kamen-episode-5-subtitle-indonesia/",
        )
        assertTrue(
            candidates.contains("https://v2.samehadaku.how/shuiro-no-kamen-episode-5/"),
        )
        assertTrue(candidates.contains("https://samehadaku.how/shuiro-no-kamen-episode-5/"))
    }

    @Test
    fun `query string is kept`() {
        val candidates = SiteMirrors.candidates(
            "https://samehadaku.li/episode-links/blue-lock-1/?page=2",
        )
        assertTrue(
            candidates.contains(
                "https://v2.samehadaku.how/episode-links/blue-lock-1/?page=2",
            ),
        )
    }

    @Test
    fun `old parked host is rescued but never offered`() {
        val candidates = SiteMirrors.candidates("https://samehadaku.care/anime/ao-ashi-s2/")
        assertFalse(candidates.any { it.startsWith("https://samehadaku.care/") })
        assertTrue(candidates.contains("https://v2.samehadaku.how/anime/ao-ashi-s2/"))
    }

    @Test
    fun `only live hosts are advertised`() {
        val hosts = SiteMirrors.knownHosts()
        assertTrue("v2.samehadaku.how" in hosts)
        assertTrue("samehadaku.li" in hosts)
        assertFalse("samehadaku.care" in hosts)
        assertFalse("samehadaku.email" in hosts)
        assertFalse("samehadaku.vip" in hosts)
        assertFalse("samehadaku.mba" in hosts)
    }

    @Test
    fun `episode reference is recovered from both url shapes`() {
        assertEquals(
            SiteMirrors.EpisodeRef("ao-ashi-s2", 5),
            SiteMirrors.episodeRef("https://v2.samehadaku.how/ao-ashi-s2-episode-5/"),
        )
        assertEquals(
            SiteMirrors.EpisodeRef("ao-ashi-season-2", 5),
            SiteMirrors.episodeRef(
                "https://samehadaku.li/ao-ashi-season-2-episode-5-subtitle-indonesia/",
            ),
        )
    }

    @Test
    fun `non site urls have no mirrors`() {
        assertEquals(emptyList<String>(), SiteMirrors.candidates("https://pixeldrain.com/u/abc"))
        assertEquals(emptyList<String>(), SiteMirrors.candidates("https://samehadaku.how/"))
        assertEquals(emptyList<String>(), SiteMirrors.candidates("bukan url"))
    }
}
