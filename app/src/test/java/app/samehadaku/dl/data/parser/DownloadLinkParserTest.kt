package app.samehadaku.dl.data.parser

import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadLinkParserTest {

    private val pageUrl = "https://v2.samehadaku.how/blue-lock-season-2-episode-1/"

    private val fixture = """
        <html><body>
          <div class="links">
            <h3>MKV 1080p</h3>
            <ul>
              <li>1080p <a href="https://pixeldrain.com/u/abc123">Pixeldrain</a></li>
              <li>1080p <a href="https://www.mediafire.com/file/xyz789/file.mkv">Mediafire</a></li>
            </ul>
          </div>
          <div class="links">
            <h3>MP4 720p</h3>
            <ul>
              <li>720p <a href="https://pixeldrain.com/u/mp4file">Pixeldrain</a></li>
            </ul>
          </div>
          <div class="links">
            <h3>X265 480p</h3>
            <ul>
              <li>480p <a href="https://www.mediafire.com/file/x265file/file.mkv">Mediafire</a></li>
            </ul>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses formats resolutions and hosts`() {
        val links = DownloadLinkParser.parse(fixture, pageUrl)
        assertNotNull(links)

        assertEquals(
            "https://pixeldrain.com/u/abc123",
            links!!.url(VideoFormat.MKV, "1080p", Host.PIXELDRAIN),
        )
        assertEquals(
            "https://www.mediafire.com/file/xyz789/file.mkv",
            links.url(VideoFormat.MKV, "1080p", Host.MEDIAFIRE),
        )
        assertEquals(
            "https://pixeldrain.com/u/mp4file",
            links.url(VideoFormat.MP4, "720p", Host.PIXELDRAIN),
        )
        assertEquals(
            "https://www.mediafire.com/file/x265file/file.mkv",
            links.url(VideoFormat.X265, "480p", Host.MEDIAFIRE),
        )
        assertEquals(
            listOf(VideoFormat.MKV, VideoFormat.MP4, VideoFormat.X265),
            links.formats(),
        )
    }

    @Test
    fun `fullhd and hd fallbacks map to 1080p and 720p`() {
        val html = """
            <html><body>
              <h3>MKV</h3>
              <ul>
                <li>FULLHD <a href="https://pixeldrain.com/u/full">Pixeldrain</a></li>
              </ul>
              <h3>MP4</h3>
              <ul>
                <li>HD <a href="https://pixeldrain.com/u/hd">Pixeldrain</a></li>
              </ul>
            </body></html>
        """.trimIndent()

        val links = DownloadLinkParser.parse(html, pageUrl)
        assertNotNull(links)
        assertEquals(
            "https://pixeldrain.com/u/full",
            links!!.url(VideoFormat.MKV, "1080p", Host.PIXELDRAIN),
        )
        assertEquals(
            "https://pixeldrain.com/u/hd",
            links.url(VideoFormat.MP4, "720p", Host.PIXELDRAIN),
        )
    }

    @Test
    fun `returns null when no supported host is present`() {
        val html = """
            <html><body>
              <ul><li>1080p <a href="https://example.com/file">Other</a></li></ul>
            </body></html>
        """.trimIndent()

        assertNull(DownloadLinkParser.parse(html, pageUrl))
    }

    @Test
    fun `meta refresh target is resolved against site root`() {
        val html = """
            <html><head>
              <meta http-equiv="refresh" content="0; url='/episode-links/blue-lock-1/'">
            </head><body></body></html>
        """.trimIndent()

        val target = DownloadLinkParser.findMetaRefreshUrl(html, pageUrl)
        assertEquals(
            "https://v2.samehadaku.how/episode-links/blue-lock-1/",
            target,
        )
    }
}
