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
              <li>1080p <a href="https://gofile.io/d/gofile123">Gofile</a></li>
              <li>1080p <a href="https://acefile.co/f/987654/file.mkv">Acefile</a></li>
              <li>1080p <a href="https://filedon.co/view/f3kUTC3QTM">Filedon</a></li>
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
    fun `redesigned sections parse links without li wrappers`() {
        val html = """
            <html><body>
              <h3>Link Download MKV</h3>
              <div class="quality-row">
                <h4>360p</h4>
                <div><a href="/d/movie-gofile">Gofile</a></div>
              </div>
              <h3>Link Download MP4</h3>
              <div class="quality-row">
                <h4>720p</h4>
                <div><a href="https://www.mediafire.com/file/movie-mp4/file.mp4">Mediafire</a></div>
              </div>
            </body></html>
        """.trimIndent()

        val links = DownloadLinkParser.parse(
            html,
            "https://v2.samehadaku.how/the-first-slam-dunk-movie/",
        )

        assertNotNull(links)
        assertEquals(
            "https://v2.samehadaku.how/d/movie-gofile",
            links!!.url(VideoFormat.MKV, "360p", Host.GOFILE),
        )
        assertEquals(
            "https://www.mediafire.com/file/movie-mp4/file.mp4",
            links.url(VideoFormat.MP4, "720p", Host.MEDIAFIRE),
        )
    }

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
            "https://gofile.io/d/gofile123",
            links.url(VideoFormat.MKV, "1080p", Host.GOFILE),
        )
        assertEquals(
            "https://acefile.co/f/987654/file.mkv",
            links.url(VideoFormat.MKV, "1080p", Host.ACEFILE),
        )
        assertEquals(
            "https://filedon.co/view/f3kUTC3QTM",
            links.url(VideoFormat.MKV, "1080p", Host.FILEDON),
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
    fun `keeps filedon links outside the legacy list`() {
        val html = """
            <html><body>
              <h3>MKV 720p</h3>
              <ul>
                <li>720p <a href="https://gofile.io/d/abc">Gofile</a></li>
              </ul>
              <div class="mirror-extra">
                <h4>720p</h4>
                <a href="https://filedon.co/view/extraSh4re">Filedon</a>
              </div>
            </body></html>
        """.trimIndent()

        val links = DownloadLinkParser.parse(html, pageUrl)
        assertNotNull(links)
        assertEquals(
            "https://gofile.io/d/abc",
            links!!.url(VideoFormat.MKV, "720p", Host.GOFILE),
        )
        assertEquals(
            "https://filedon.co/view/extraSh4re",
            links.url(VideoFormat.MKV, "720p", Host.FILEDON),
        )
    }

    @Test
    fun `parses the live episode layout with span wrapped hosts`() {
        val html = """
            <html><body>
              <div class="download-eps" id="downloadb">
                <p><b>MKV</b></p>
                <ul>
                  <li>
                    <strong>1080p</strong>
                    <div class="download-eps-links">
                      <span><a href="https://gofile.io/d/a">Gofile</a></span>
                      <span><a href="https://filedon.co/view/f3kUTC3QTM">Filedon</a></span>
                      <span><a href="https://acefile.co/f/1/t.mkv">Acefile</a></span>
                      <span><a href="https://pixeldrain.com/u/p">Pixeldrain</a></span>
                      <span><a href="https://www.mediafire.com/file/m/t.mkv">Mediafire (RAR)</a></span>
                    </div>
                  </li>
                </ul>
              </div>
            </body></html>
        """.trimIndent()

        val links = DownloadLinkParser.parse(html, pageUrl)
        assertNotNull(links)
        assertEquals(5, links!!.hosts(VideoFormat.MKV, "1080p").size)
        assertEquals(
            "https://filedon.co/view/f3kUTC3QTM",
            links.url(VideoFormat.MKV, "1080p", Host.FILEDON),
        )
    }

    @Test
    fun `keeps struck hosts visible as unavailable`() {
        val html = """
            <html><body>
              <div class="download-eps" id="downloadb">
                <p><b>MKV</b></p>
                <ul>
                  <li>
                    <strong>360p</strong>
                    <div class="download-eps-links">
                      <span><a href="https://gofile.io/d/abc">Gofile</a></span>
                      <span><strike>Filedon</strike></span>
                      <span><a href="https://acefile.co/f/1/file.mkv">Acefile</a></span>
                      <span><a href="https://pixeldrain.com/u/xyz">Pixeldrain</a></span>
                      <span><a href="https://www.mediafire.com/file/a/b.mkv">Mediafire (RAR)</a></span>
                    </div>
                  </li>
                </ul>
              </div>
            </body></html>
        """.trimIndent()

        val links = DownloadLinkParser.parse(html, pageUrl)
        assertNotNull(links)
        assertEquals(4, links!!.hosts(VideoFormat.MKV, "360p").size)
        assertEquals(listOf(Host.FILEDON), links.unavailableHosts(VideoFormat.MKV, "360p"))
        assertNull(links.url(VideoFormat.MKV, "360p", Host.FILEDON))
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
    fun `host detection accepts all supported hosts`() {
        assertEquals(
            Host.GOFILE,
            Host.fromLink("https://gofile.io/d/abc", "Gofile"),
        )
        assertEquals(
            Host.ACEFILE,
            Host.fromLink("https://acefile.co/f/123/file", "Acefile"),
        )
        assertEquals(
            Host.PIXELDRAIN,
            Host.fromLink("https://pixeldrain.com/u/abc", "Pixeldrain"),
        )
        assertEquals(
            Host.MEDIAFIRE,
            Host.fromLink("https://www.mediafire.com/file/abc", "Mediafire"),
        )
        assertEquals(
            Host.FILEDON,
            Host.fromLink("https://filedon.co/view/f3kUTC3QTM", "Filedon"),
        )
        assertNull(Host.fromLink("https://example.com/file", "Other"))
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
