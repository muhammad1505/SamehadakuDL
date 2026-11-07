package app.samehadaku.dl.data.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AcefileUrlParserTest {

    @Test
    fun `extracts file id from page url`() {
        assertEquals(
            "112436301",
            AcefileUrlParser.fileId(
                "https://acefile.co/f/112436301/tokyo-revengers-s4-episode-6",
            ),
        )
    }

    @Test
    fun `extracts file id without slug`() {
        assertEquals("123", AcefileUrlParser.fileId("https://acefile.co/f/123"))
    }

    @Test
    fun `rejects unsupported links`() {
        assertNull(AcefileUrlParser.fileId("https://example.com/f/123"))
    }

    @Test
    fun `builds mirror endpoint`() {
        assertEquals(
            "https://acefile.co/service/mirror_download/123",
            AcefileUrlParser.mirrorUrl("123"),
        )
    }
}
