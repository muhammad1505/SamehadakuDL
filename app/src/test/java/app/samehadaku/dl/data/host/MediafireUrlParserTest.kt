package app.samehadaku.dl.data.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediafireUrlParserTest {

    @Test
    fun `double encoded link produces decoded candidate`() {
        val url = "https://www.mediafire.com/file/abc%255Bxyz%255D/file.rar"
        val candidates = MediafireUrlParser.buildCandidates(url)

        assertEquals(2, candidates.size)
        assertEquals(url, candidates[0])
        assertEquals(
            "https://www.mediafire.com/file/abc%5Bxyz%5D/file.rar",
            candidates[1],
        )
    }

    @Test
    fun `normal link yields a single candidate`() {
        val url = "https://www.mediafire.com/file/abc/file.mkv"
        assertEquals(listOf(url), MediafireUrlParser.buildCandidates(url))
    }

    @Test
    fun `pixeldrain direct url strips query and slash`() {
        assertEquals(
            "https://pixeldrain.com/api/file/abc123?download",
            PixeldrainUrlParser.toDirectUrl("https://pixeldrain.com/u/abc123/?foo=bar"),
        )
        assertTrue(
            PixeldrainUrlParser.toDirectUrl("https://www.mediafire.com/file/x") == null,
        )
    }
}
