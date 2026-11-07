package app.samehadaku.dl.data.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GofileUrlParserTest {

    @Test
    fun `extracts content id from download path`() {
        assertEquals(
            "zZHJAyLy",
            GofileUrlParser.contentId("https://gofile.io/d/zZHJAyLy"),
        )
    }

    @Test
    fun `extracts content id from query links`() {
        assertEquals(
            "abc123",
            GofileUrlParser.contentId("https://gofile.io/?c=abc123"),
        )
    }

    @Test
    fun `rejects unsupported links`() {
        assertNull(GofileUrlParser.contentId("https://example.com/d/abc"))
    }

    @Test
    fun `website token follows gofile formula`() {
        assertEquals(
            "a2b09bed5aeadcdbce7c11d2f972116b656b33fc51dfa920f0f644bf3e44c258",
            GofileWebsiteToken.generate(
                accountToken = "test-token",
                salt = "12af056dacea0b",
                epochSeconds = 12345L * 14400L,
            ),
        )
    }

    @Test
    fun `extracts rotated salt from obfuscated script`() {
        val source = """
            var arr=['\x57\x36\x69\x65\x57\x35\x7a\x53\x43\x53\x6b\x63\x79\x76\x66\x58\x57\x37\x4e\x63\x47\x30\x44\x75\x57\x4f\x69'];
            decoder(0x163,'\x35\x74\x69\x46');
        """.trimIndent()

        assertEquals(
            listOf("12af056dacea0b"),
            GofileSaltExtractor.extract(source),
        )
    }
}
