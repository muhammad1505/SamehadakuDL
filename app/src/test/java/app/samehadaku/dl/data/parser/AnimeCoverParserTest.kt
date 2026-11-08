package app.samehadaku.dl.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeCoverParserTest {

    @Test
    fun `prefers social cover and resolves relative poster url`() {
        val html = """
            <html><head>
              <meta property="og:image" content="/images/the-first-slam-dunk.jpg">
            </head><body>
              <div class="thumb"><img src="/images/fallback.jpg"></div>
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://v2.samehadaku.how/images/the-first-slam-dunk.jpg",
            AnimeCoverParser.parse(html, "https://v2.samehadaku.how/anime/the-first-slam-dunk/"),
        )
    }

    @Test
    fun `reads lazy loaded poster when social metadata is absent`() {
        val html = """
            <html><body>
              <div class="poster"><img data-src="https://cdn.example/poster.webp"></div>
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://cdn.example/poster.webp",
            AnimeCoverParser.parse(html, "https://samehadaku.how/anime/example/"),
        )
    }
}
