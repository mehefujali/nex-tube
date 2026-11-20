package com.nextube.app

import com.nextube.app.core.Recommender
import com.nextube.app.core.YtDlp
import com.nextube.app.data.DownloadTask
import com.nextube.app.data.MediaKind
import com.nextube.app.data.SearchItem
import com.nextube.app.util.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexTubeUnitTest {

    @Test
    fun formatHelpersWork() {
        assertEquals("—", Fmt.bytes(0))
        assertEquals("512 B", Fmt.bytes(512))
        assertEquals("1.5 MB", Fmt.bytes(1_572_864))
        assertEquals("", Fmt.duration(0))
        assertEquals("1:05", Fmt.duration(65))
        assertEquals("1:01:05", Fmt.duration(3665))
        assertEquals("1.2M views", Fmt.views(1_200_000))
        assertEquals("5K views", Fmt.views(5_000))
    }

    @Test
    fun urlAndPlatformDetectionWork() {
        assertEquals("https://youtu.be/abc123", Fmt.extractUrl("Check this https://youtu.be/abc123."))
        assertTrue(Fmt.isUrl("instagram.com/reel/xyz"))
        assertTrue(Fmt.isUrl("https://x.com/user/status/123"))
        assertTrue(Fmt.isPlaylistUrl("https://www.youtube.com/playlist?list=PL12345"))
        assertFalse(Fmt.isPlaylistUrl("https://www.youtube.com/watch?v=abc"))
        assertEquals("https://www.youtube.com/playlist?list=PL12345", Fmt.extractPlaylistUrl("https://www.youtube.com/watch?v=abc&list=PL12345"))
        assertNull(Fmt.extractPlaylistUrl("https://www.youtube.com/watch?v=abc&list=RDxyz"))
        assertEquals("Instagram", Fmt.platformOf("https://www.instagram.com/reel/abc/").name)
        assertEquals("Facebook", Fmt.platformOf("https://fb.watch/abc/").name)
        assertEquals("YouTube", Fmt.platformOf("https://youtu.be/abc").name)
    }

    @Test
    fun progressParserWorks() {
        val p = YtDlp.parseProgress("NXP|5242880|10485760|NA|1048576.0|5|137")
        assertNotNull(p)
        assertEquals("137", p!!.formatId)
        assertEquals(5242880L, p.downloaded)
        assertEquals(10485760L, p.total)
        assertEquals(5L, p.eta)
        assertNull(YtDlp.parseProgress("[download] random line"))
    }

    @Test
    fun recommenderBuildsQueryAndFiltersDownloaded() {
        val history = listOf(
            DownloadTask(
                id = "1", url = "https://www.youtube.com/watch?v=v1", title = "Arijit Singh Kesariya Official Video",
                channel = "Sony Music India", thumbnail = "", kind = MediaKind.AUDIO, optionLabel = "MP3", args = emptyList(),
            ),
            DownloadTask(
                id = "2", url = "https://www.youtube.com/watch?v=v2", title = "Arijit Singh Apna Bana Le",
                channel = "Sony Music India", thumbnail = "", kind = MediaKind.AUDIO, optionLabel = "MP3", args = emptyList(),
            ),
        )
        val rec = Recommender.buildQuery(history, seed = 0)
        assertNotNull(rec)
        assertTrue(rec!!.query.contains("Sony Music India"))

        val searchResults = listOf(
            SearchItem("v1", "Arijit Singh Kesariya Official Video", "Sony Music India", 200, 1000, "https://www.youtube.com/watch?v=v1"),
            SearchItem("v3", "Fresh Song", "Sony Music India", 210, 5000, "https://www.youtube.com/watch?v=v3"),
        )
        val fresh = Recommender.filterFresh(searchResults, history)
        assertEquals(1, fresh.size)
        assertEquals("v3", fresh.first().id)
    }
}
