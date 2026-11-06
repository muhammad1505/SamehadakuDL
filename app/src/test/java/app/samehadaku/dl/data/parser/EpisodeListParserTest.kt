package app.samehadaku.dl.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeListParserTest {

    @Test
    fun `inconsistent diamond no ace slugs yield exactly four sorted episodes`() {
        val html = """
            <html><body>
              <div class="lstepsiode">
                <ul>
                  <li><a href="/diamond-no-ace-act-ii-season-2-cour-2-episode-4/">Episode 4</a></li>
                  <li><a href="/diamond-no-ace-act-ii-season-2-cour-2/">Episode 1</a></li>
                  <li><a href="/diamond-no-ace-act-ii-season-2-cour-2-2/">Episode 2</a></li>
                  <li><a href="/diamond-no-ace-act-ii-season-2-cour-2-episode-3/">Episode 3</a></li>
                </ul>
              </div>
              <div class="sidebar">
                <a href="/one-piece-episode-1100/">One Piece 1100</a>
                <a href="/diamond-no-ace-act-ii-season-2-cour-1-episode-12/">Other cour</a>
              </div>
            </body></html>
        """.trimIndent()

        val pageUrl = "https://v2.samehadaku.how/anime/diamond-no-ace-act-ii-season-2-cour-2/"
        val episodes = EpisodeListParser.parse(html, pageUrl)

        assertEquals(4, episodes.size)
        assertEquals(listOf(1, 2, 3, 4), episodes.map { it.number })
        assertEquals(
            listOf(
                "https://v2.samehadaku.how/diamond-no-ace-act-ii-season-2-cour-2/",
                "https://v2.samehadaku.how/diamond-no-ace-act-ii-season-2-cour-2-2/",
                "https://v2.samehadaku.how/diamond-no-ace-act-ii-season-2-cour-2-episode-3/",
                "https://v2.samehadaku.how/diamond-no-ace-act-ii-season-2-cour-2-episode-4/",
            ),
            episodes.map { it.url },
        )
        assertTrue(episodes.none { "one-piece" in it.url })
        assertTrue(episodes.none { "cour-1" in it.url })
    }

    @Test
    fun `normal list supports relative hrefs fragments and query strings`() {
        val html = """
            <html><body>
              <div class="lstepsiode">
                <a href="../../ao-ashi-s2-episode-1/#comments">Episode 1</a>
                <a href="/ao-ashi-s2-episode-2/?utm=1">Episode 2</a>
                <a href="/ao-ashi-s2-episode-2/#fragment">Episode 2 duplicate</a>
              </div>
            </body></html>
        """.trimIndent()

        val pageUrl = "https://samehadaku.how/anime/ao-ashi-s2/"
        val episodes = EpisodeListParser.parse(html, pageUrl)

        assertEquals(2, episodes.size)
        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals(
            "https://samehadaku.how/ao-ashi-s2-episode-1/",
            episodes[0].url,
        )
        assertEquals(
            "https://samehadaku.how/ao-ashi-s2-episode-2/",
            episodes[1].url,
        )
    }

    @Test
    fun `fallback groups anchors by most similar prefix`() {
        val html = """
            <html><body>
              <div class="unrelated">
                <a href="/kingdom-5-episode-1/">Kingdom</a>
                <a href="/kingdom-5-episode-2/">Kingdom 2</a>
              </div>
              <div class="another">
                <a href="/blue-lock-season-2-episode-1/">Blue Lock</a>
                <a href="/blue-lock-season-2-episode-2/">Blue Lock 2</a>
                <a href="/blue-lock-season-2-episode-3/">Blue Lock 3</a>
              </div>
            </body></html>
        """.trimIndent()

        val episodes = EpisodeListParser.parse(
            html,
            "https://samehadaku.how/anime/blue-lock-season-2/",
        )

        assertEquals(3, episodes.size)
        assertTrue(episodes.all { "blue-lock-season-2" in it.url })
    }
}
