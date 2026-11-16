package app.samehadaku.dl.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeFilesTest {

    @Test
    fun `videos and archives count as episodes`() {
        assertTrue(EpisodeFiles.isEpisode("one-piece-episode-1096_MKV_1080p.mkv"))
        assertTrue(EpisodeFiles.isEpisode("episode_MP4_720p.MP4"))
        assertTrue(EpisodeFiles.isEpisode("batch-01-12.zip"))
    }

    @Test
    fun `only videos are tracked by smart storage`() {
        assertTrue(EpisodeFiles.isVideo("one-piece-episode-1096_MKV_1080p.mkv"))
        assertTrue(EpisodeFiles.isVideo("episode_MP4_720p.MP4"))
        assertFalse(EpisodeFiles.isVideo("batch-01-12.zip"))
        assertFalse(EpisodeFiles.isVideo("catatan.txt"))
        assertFalse(EpisodeFiles.isVideo("episode.mkv.part"))
    }

    @Test
    fun `unrelated files are ignored`() {
        assertFalse(EpisodeFiles.isEpisode("asset"))
        assertFalse(EpisodeFiles.isEpisode("screenshot.png"))
        assertFalse(EpisodeFiles.isEpisode("catatan.txt"))
        assertFalse(EpisodeFiles.isEpisode(".nomedia"))
        assertFalse(EpisodeFiles.isEpisode("episode.mkv.part"))
        assertFalse(EpisodeFiles.isEpisode(""))
    }
}
