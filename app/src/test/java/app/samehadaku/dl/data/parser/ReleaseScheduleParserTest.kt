package app.samehadaku.dl.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseScheduleParserTest {

    @Test
    fun `parses current samehadaku daily cards with cover score and clock`() {
        val document = Jsoup.parse(
            """
            <div class="schedule">
              <div x-show="activeDay === 'monday'" class="result-schedule">
                <div class="animepost">
                  <a href="/anime/one-piece/" title="One Piece">
                    <img class="anmsa" src="/wp-content/uploads/one-piece.jpg" title="One Piece">
                    <div class="type TV">TV</div>
                    <div class="score">★ 8.72</div>
                  </a>
                  <div class="data"><div class="title"><h2>One Piece</h2></div></div>
                  <div class="data_tw"><a>00:00</a></div>
                </div>
              </div>
            </div>
            """.trimIndent(),
            "https://v2.samehadaku.how/jadwal/",
        )

        val item = ReleaseScheduleParser.parseSchedule(document, document.baseUri()).single()

        assertEquals("Senin", item.day)
        assertEquals("One Piece", item.title)
        assertEquals("00:00", item.time)
        assertEquals("8.72", item.rating)
        assertEquals(
            "https://v2.samehadaku.how/wp-content/uploads/one-piece.jpg",
            item.coverUrl,
        )
    }

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
