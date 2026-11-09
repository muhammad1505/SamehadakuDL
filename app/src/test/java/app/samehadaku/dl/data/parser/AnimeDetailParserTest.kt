package app.samehadaku.dl.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeDetailParserTest {

    @Test
    fun `parses description rating and genres from detail metadata`() {
        val document = Jsoup.parse(
            """
            <html>
              <head><meta property="og:description" content="Kisah petualangan panjang seorang pemuda dan teman-temannya di lautan."></head>
              <body><div class="rating">Rating: 8.7</div><div class="genres"><a>Aksi</a><a>Petualangan</a></div></body>
            </html>
            """.trimIndent(),
        )

        val detail = AnimeDetailParser.parse(document)

        assertEquals("8.7", detail?.rating)
        assertTrue(detail?.description?.contains("Kisah petualangan") == true)
        assertEquals(listOf("Aksi", "Petualangan"), detail?.genres)
    }

    @Test
    fun `prefers the long visible synopsis over short seo description`() {
        val document = Jsoup.parse(
            """
            <head><meta property="og:description" content="Nonton One Piece terbaru di Samehadaku."></head>
            <body>
              <div class="desc">Gol D. Roger dikenal sebagai Raja Bajak Laut, Orang terkuat dan paling terkenal yang pernah mengarungi Grand Line. Penangkapan dan Eksekusi Roger oleh Pemerintahan Dunia telah membawa perubahan di seluruh dunia. Inilah awal dari era bajak laut dan mimpi Monkey D Luffy.</div>
            </body>
            """.trimIndent(),
        )

        val detail = AnimeDetailParser.parse(document)

        assertTrue(detail?.description?.contains("Gol D. Roger") == true)
        assertTrue(detail?.description?.contains("Monkey D Luffy") == true)
    }
}
