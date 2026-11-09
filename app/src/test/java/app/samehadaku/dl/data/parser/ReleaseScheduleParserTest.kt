package app.samehadaku.dl.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseScheduleParserTest {

    @Test
    fun `parses weekly rows and delay announcement`() {
        val document = Jsoup.parse(
            """
            <html><head><title>Jadwal anime delay libur</title></head><body>
              <h2>Senin</h2>
              <div class="schedule-item"><a href="/anime/blue-lock/">Blue Lock</a> 18:30 Ep 12</div>
              <div class="notice">Jadwal anime delay/libur minggu ini.</div>
            </body></html>
            """.trimIndent(),
            "https://samehadaku.how/jadwal-rilis/",
        )

        val schedule = ReleaseScheduleParser.parseSchedule(document, document.baseUri())
        val announcements = ReleaseScheduleParser.parseAnnouncements(document)

        assertEquals(1, schedule.size)
        assertEquals("Senin", schedule.first().day)
        assertEquals("Blue Lock", schedule.first().title)
        assertEquals("18:30", schedule.first().time)
        assertEquals("Ep 12", schedule.first().episode)
        assertTrue(announcements.any { it.contains("delay", ignoreCase = true) })
    }
}
