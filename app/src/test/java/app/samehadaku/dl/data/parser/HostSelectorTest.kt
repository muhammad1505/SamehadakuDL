package app.samehadaku.dl.data.parser

import app.samehadaku.dl.domain.model.DownloadLinks
import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostSelectorTest {

    private val links = DownloadLinks(
        linkedMapOf(
            VideoFormat.MKV to linkedMapOf(
                "1080p" to linkedMapOf(
                    Host.PIXELDRAIN to "https://pixeldrain.com/u/p1080",
                    Host.MEDIAFIRE to "https://www.mediafire.com/file/m1080",
                ),
            ),
            VideoFormat.MP4 to linkedMapOf(
                "720p" to linkedMapOf(
                    Host.PIXELDRAIN to "https://pixeldrain.com/u/p720",
                ),
            ),
        ),
    )

    @Test
    fun `preferred host is first then fallback hosts`() {
        val ordered = HostSelector.findTargets(links, "mkv", "1080p", "mediafire")
        assertEquals(2, ordered.size)
        assertEquals(Host.MEDIAFIRE, ordered[0].host)
        assertEquals(Host.PIXELDRAIN, ordered[1].host)
    }

    @Test
    fun `preferred pixeldrain puts pixeldrain first`() {
        val ordered = HostSelector.findTargets(links, "MKV", "1080p", "Pixeldrain")
        assertEquals(Host.PIXELDRAIN, ordered[0].host)
        assertEquals(Host.MEDIAFIRE, ordered[1].host)
    }

    @Test
    fun `no matching quality returns empty list`() {
        assertTrue(HostSelector.findTargets(links, "x265", "1080p", "pixeldrain").isEmpty())
        assertTrue(HostSelector.findTargets(links, "mkv", "360p", "pixeldrain").isEmpty())
    }

    @Test
    fun `same resolution on another format is used when the chosen format is missing`() {
        val selection = HostSelector.findBestTargets(
            links,
            VideoFormat.X265,
            "720p",
            Host.PIXELDRAIN,
        )!!
        assertTrue(selection.adjusted)
        assertEquals(VideoFormat.MP4, selection.format)
        assertEquals("720p", selection.resolution)
        assertEquals(Host.PIXELDRAIN, selection.targets.first().host)
    }

    @Test
    fun `format fallback follows the configured format order`() {
        val mixed = DownloadLinks(
            linkedMapOf(
                VideoFormat.MKV to linkedMapOf(
                    "480p" to linkedMapOf(
                        Host.MEDIAFIRE to "https://www.mediafire.com/file/m480",
                    ),
                ),
                VideoFormat.MP4 to linkedMapOf(
                    "480p" to linkedMapOf(
                        Host.PIXELDRAIN to "https://pixeldrain.com/u/p480",
                    ),
                ),
            ),
        )
        val selection = HostSelector.findBestTargets(
            mixed,
            VideoFormat.X265,
            "480p",
            Host.PIXELDRAIN,
        )!!
        assertTrue(selection.adjusted)
        assertEquals(VideoFormat.MKV, selection.format)
        assertEquals(Host.MEDIAFIRE, selection.targets.first().host)
    }

    @Test
    fun `exact format on a fallback host wins before changing format`() {
        val mixed = DownloadLinks(
            linkedMapOf(
                VideoFormat.X265 to linkedMapOf(
                    "480p" to linkedMapOf(
                        Host.GOFILE to "https://gofile.io/d/x265-480",
                    ),
                ),
                VideoFormat.MKV to linkedMapOf(
                    "480p" to linkedMapOf(
                        Host.ACEFILE to "https://acefile.co/f/mkv-480",
                    ),
                ),
            ),
        )
        val selection = HostSelector.findBestTargets(
            mixed,
            VideoFormat.X265,
            "480p",
            Host.ACEFILE,
        )!!
        assertEquals(false, selection.adjusted)
        assertEquals(VideoFormat.X265, selection.format)
        assertEquals(Host.GOFILE, selection.targets.single().host)
    }

    @Test
    fun `resolution never changes during format fallback`() {
        val mixed = DownloadLinks(
            linkedMapOf(
                VideoFormat.X265 to linkedMapOf(
                    "720p" to linkedMapOf(
                        Host.ACEFILE to "https://acefile.co/f/x265-720",
                    ),
                ),
                VideoFormat.MKV to linkedMapOf(
                    "480p" to linkedMapOf(
                        Host.GOFILE to "https://gofile.io/d/mkv-480",
                    ),
                ),
            ),
        )
        val selection = HostSelector.findBestTargets(
            mixed,
            VideoFormat.X265,
            "480p",
            Host.ACEFILE,
        )!!
        assertEquals(VideoFormat.MKV, selection.format)
        assertEquals("480p", selection.resolution)
        assertNull(
            HostSelector.findBestTargets(
                mixed,
                VideoFormat.X265,
                "360p",
                Host.ACEFILE,
            ),
        )
    }

    @Test
    fun `exact quality is not reported as adjusted`() {
        val selection = HostSelector.findBestTargets(
            links,
            VideoFormat.MKV,
            "1080p",
            Host.MEDIAFIRE,
        )!!
        assertEquals(false, selection.adjusted)
        assertEquals(Host.MEDIAFIRE, selection.targets.first().host)
    }

    @Test
    fun `no links at all returns null`() {
        assertNull(
            HostSelector.findBestTargets(
                DownloadLinks.EMPTY,
                VideoFormat.MKV,
                "480p",
                Host.PIXELDRAIN,
            ),
        )
    }

    @Test
    fun `nearest fallback prefers closest resolution`() {
        val nearest = HostSelector.findNearestTargets(
            links,
            VideoFormat.MKV,
            "900p",
            Host.MEDIAFIRE,
        )!!
        assertEquals("1080p", nearest.resolution)
        assertEquals(Host.MEDIAFIRE, nearest.targets.first().host)
    }
}
