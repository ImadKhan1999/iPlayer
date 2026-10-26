package com.imadlabs.iplayer

import com.imadlabs.iplayer.data.media.EpisodeParser
import com.imadlabs.iplayer.data.media.LrcParser
import com.imadlabs.iplayer.data.media.Lyrics
import com.imadlabs.iplayer.data.media.MediaFormats
import com.imadlabs.iplayer.data.media.SubtitleMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaParsingTest {
    @Test fun episodePatterns() {
        assertEquals(EpisodeParser.Episode("The Show", 1, 2), EpisodeParser.parse("The.Show.S01E02.1080p.mkv"))
        assertEquals(EpisodeParser.Episode("Show Name", 3, 14), EpisodeParser.parse("Show Name - 3x14 - Title.mp4"))
        assertEquals(EpisodeParser.Episode("Show", 2, 5), EpisodeParser.parse("Show Season 2 Episode 5.avi"))
        assertEquals(EpisodeParser.Episode("Lost", 2, 5), EpisodeParser.parse("E05 - Orientation.mkv", "/storage/Videos/Lost/Season 2"))
        assertNull(EpisodeParser.parse("Holiday 2024.mp4"))
        assertEquals(2, EpisodeParser.seasonNumber("/x/Season 02"))
        assertEquals(3, EpisodeParser.seasonNumber("S3"))
    }

    @Test fun subtitleMatching() {
        val candidates = listOf("Movie.srt", "Movie.fr.srt", "Movie.en.forced.ass", "Other.srt", "Movie.nfo", "Movie.lrc")
        val m = SubtitleMatcher.match("Movie.mkv", candidates, preferredLanguage = "en")
        assertEquals(listOf("Movie.srt", "Movie.en.forced.ass", "Movie.fr.srt"), m.map { it.fileName })
        assertEquals("en", m[1].language)
        assertTrue(SubtitleMatcher.languageMatches("eng", "en"))
        assertTrue(SubtitleMatcher.languageMatches("English", "en"))
        assertFalse(SubtitleMatcher.languageMatches("fre", "en"))
    }

    @Test fun formats() {
        assertTrue(MediaFormats.isVideo("a.MKV"))
        assertTrue(MediaFormats.isAudio("b.flac"))
        assertTrue(MediaFormats.isSubtitle("c.ass"))
        assertFalse(MediaFormats.isSubtitle("d.lrc"))
        assertFalse(MediaFormats.isPlayable("e.pdf"))
    }

    @Test fun lrcSynced() {
        val lrc = """
            [ti:Song]
            [offset:500]
            [00:01.00]First
            [00:05.50][00:20.00]Chorus
            [00:10.123]Second
        """.trimIndent()
        val lyrics = LrcParser.parse(lrc) as Lyrics.Synced
        assertEquals(listOf(500L, 5000L, 9623L, 19500L), lyrics.lines.map { it.timeMs })
        assertEquals("Chorus", lyrics.lines[1].text)
        assertEquals(-1, LrcParser.activeIndex(lyrics.lines, 100))
        assertEquals(1, LrcParser.activeIndex(lyrics.lines, 9000))
        assertEquals(3, LrcParser.activeIndex(lyrics.lines, 60_000))
    }

    @Test fun lrcPlainAndEmpty() {
        assertEquals(Lyrics.Plain("Just words\nMore words"), LrcParser.parse("[ar:Someone]\nJust words\nMore words"))
        assertNull(LrcParser.parse("   "))
    }
}
