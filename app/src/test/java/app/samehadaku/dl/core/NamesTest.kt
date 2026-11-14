package app.samehadaku.dl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {

    @Test
    fun `season spelling differences describe the same anime`() {
        assertTrue(Names.sameAnime("ao-ashi-s2", "ao-ashi-season-2"))
        assertTrue(Names.sameAnime("Blue Lock Season 2", "blue-lock-s2"))
        assertTrue(
            Names.titleMatchesSlug(
                "Ao Ashi Season 2 Subtitle Indonesia",
                "ao-ashi-s2",
            ),
        )
    }

    @Test
    fun `different series never match just because one contains the other`() {
        assertFalse(Names.sameAnime("one-piece", "one-piece-film-red"))
        assertFalse(Names.sameAnime("jujutsu-kaisen", "jujutsu-kaisen-s2"))
        assertFalse(Names.sameAnime("blue-lock", "blue-lock-season-2"))
    }

    @Test
    fun `normalization removes page title noise`() {
        assertEquals(
            "ao ashi s2",
            Names.normalizedAnimeKey("Nonton Anime Ao Ashi Season 2 Sub Indo"),
        )
    }
}
