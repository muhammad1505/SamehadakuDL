package app.samehadaku.dl.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultParserTest {

    private val pageUrl = "https://v2.samehadaku.how/?s=blue+lock"

    @Test
    fun `parses current animpost cards with image titles and type`() {
        val html = """
            <html><body>
              <main>
                <article class="animpost" id="post-101">
                  <a href="https://v2.samehadaku.how/anime/blue-lock-season-2/">
                    <img title="Blue Lock Season 2" src="/img/blue.jpg">
                    <div class="ttls">Blue Lock Season 2</div>
                    <div class="type">TV</div>
                  </a>
                </article>
                <article class="animpost" id="post-102">
                  <a href="/anime/blue-lock/">
                    <img title="Blue Lock" src="/img/blue1.jpg">
                    <div class="type">TV</div>
                  </a>
                </article>
              </main>
            </body></html>
        """.trimIndent()

        val results = SearchResultParser.parse(html, pageUrl)

        assertEquals(2, results.size)
        assertEquals("Blue Lock Season 2", results[0].title)
        assertEquals(
            "https://v2.samehadaku.how/anime/blue-lock-season-2/",
            results[0].url,
        )
        assertEquals("blue-lock-season-2", results[0].slug)
        assertEquals("TV", results[0].type)
        assertEquals("Blue Lock", results[1].title)
    }

    @Test
    fun `parses legacy bsx cards and ignores non anime links`() {
        val html = """
            <html><body>
              <div class="listupd">
                <div class="bs">
                  <div class="bsx">
                    <a href="/anime/one-piece/" title="One Piece">
                      <h2>One Piece</h2>
                      <span class="epx">Episode 1100</span>
                    </a>
                  </div>
                </div>
                <div class="bs">
                  <div class="bsx">
                    <a href="/anime/blue-lock/" title="Nonton Anime Blue Lock">
                      <h2>Blue Lock</h2>
                    </a>
                  </div>
                </div>
              </div>
              <a href="https://v2.samehadaku.how/anime/">Semua anime</a>
              <a href="https://v2.samehadaku.how/blue-lock-episode-1/">Episode 1</a>
            </body></html>
        """.trimIndent()

        val results = SearchResultParser.parse(html, pageUrl)

        assertEquals(2, results.size)
        assertEquals(listOf("one-piece", "blue-lock"), results.map { it.slug })
        assertEquals("One Piece", results[0].title)
        assertEquals("Episode 1100", results[0].episodeLabel)
        assertEquals("Blue Lock", results[1].title)
    }

    @Test
    fun `falls back to plain anime anchors and deduplicates`() {
        val html = """
            <html><body>
              <div class="random-layout">
                <a href="https://v2.samehadaku.how/anime/blue-lock/">Blue Lock</a>
                <a href="https://v2.samehadaku.how/anime/blue-lock/#comments">Blue Lock duplikat</a>
                <a href="https://v2.samehadaku.how/anime/blue-lock-season-2/">Blue Lock Season 2</a>
                <a href="https://other.example/anime/boruto/">Boruto</a>
              </div>
            </body></html>
        """.trimIndent()

        val results = SearchResultParser.parse(html, pageUrl)

        assertEquals(2, results.size)
        assertEquals(listOf("blue-lock", "blue-lock-season-2"), results.map { it.slug })
        assertTrue(results.none { "boruto" in it.slug })
    }

    @Test
    fun `not found page yields an empty list`() {
        val html = """
            <html><body>
              <main><div class="notfound">Tidak ada hasil untuk pencarian ini.</div></main>
            </body></html>
        """.trimIndent()

        assertTrue(SearchResultParser.parse(html, pageUrl).isEmpty())
    }

    @Test
    fun `query ranking removes unrelated cards and puts exact title first`() {
        val html = """
            <div class="listupd">
              <div class="bsx"><a href="/anime/one-piece-film/" title="One Piece Film"><h2>One Piece Film</h2></a></div>
              <div class="bsx"><a href="/anime/blue-lock/" title="Blue Lock"><h2>Blue Lock</h2></a></div>
              <div class="bsx"><a href="/anime/one-piece/" title="One Piece"><h2>One Piece</h2></a></div>
            </div>
        """.trimIndent()

        val results = SearchResultParser.parse(html, pageUrl, "one piece")

        assertEquals(listOf("one-piece", "one-piece-film"), results.map { it.slug })
    }
}
