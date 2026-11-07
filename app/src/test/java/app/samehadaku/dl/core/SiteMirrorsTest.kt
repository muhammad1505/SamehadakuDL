package app.samehadaku.dl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMirrorsTest {

    @Test
    fun `same path is offered on every other mirror`() {
        val candidates = SiteMirrors.candidates(
            "https://samehadaku.how/blue-lock-season-2-episode-1/",
        )
        assertTrue(candidates.none { it.startsWith("https://samehadaku.how/") })
        assertTrue(
            candidates.contains("https://samehadaku.care/blue-lock-season-2-episode-1/"),
        )
        assertTrue(
            candidates.contains("https://v2.samehadaku.how/blue-lock-season-2-episode-1/"),
        )
    }

    @Test
    fun `query string is kept`() {
        val candidates = SiteMirrors.candidates(
            "https://samehadaku.li/episode-links/blue-lock-1/?page=2",
        )
        assertTrue(
            candidates.contains(
                "https://samehadaku.how/episode-links/blue-lock-1/?page=2",
            ),
        )
    }

    @Test
    fun `own host is skipped even when it is a mirror`() {
        val candidates = SiteMirrors.candidates("https://samehadaku.care/anime/ao-ashi-s2/")
        assertEquals(
            false,
            candidates.any { it.startsWith("https://samehadaku.care/") },
        )
        assertTrue(candidates.contains("https://samehadaku.how/anime/ao-ashi-s2/"))
    }

    @Test
    fun `non site urls have no mirrors`() {
        assertEquals(emptyList<String>(), SiteMirrors.candidates("https://pixeldrain.com/u/abc"))
        assertEquals(emptyList<String>(), SiteMirrors.candidates("https://samehadaku.how/"))
        assertEquals(emptyList<String>(), SiteMirrors.candidates("bukan url"))
    }
}
