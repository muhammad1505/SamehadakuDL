package app.samehadaku.dl.data.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FiledonUrlParserTest {

    @Test
    fun `extracts share id from samehadaku link`() {
        assertEquals(
            "f3kUTC3QTM",
            FiledonUrlParser.shareId("https://filedon.co/view/f3kUTC3QTM"),
        )
    }

    @Test
    fun `accepts other filedon domains and legacy file path`() {
        assertEquals(
            "AbC123",
            FiledonUrlParser.shareId("https://filedon.net/file/AbC123"),
        )
    }

    @Test
    fun `rejects unsupported links`() {
        assertNull(FiledonUrlParser.shareId("https://example.com/view/f3kUTC3QTM"))
    }

    @Test
    fun `builds page and download endpoints`() {
        assertEquals(
            "https://filedon.co/view/f3kUTC3QTM",
            FiledonUrlParser.pageUrl("f3kUTC3QTM"),
        )
        assertEquals(
            "https://filedon.co/download/f3kUTC3QTM",
            FiledonUrlParser.downloadUrl("f3kUTC3QTM"),
        )
    }
}
