package com.sjtech.wearpod.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastFeedParserTest {
    private val parser = PodcastFeedParser()

    @Test
    fun parses_itunes_durations() {
        assertEquals(3723, parser.parseDurationToSeconds("1:02:03"))
        assertEquals(754, parser.parseDurationToSeconds("12:34"))
        assertEquals(45, parser.parseDurationToSeconds("45"))
    }

    @Test
    fun returns_null_for_invalid_durations() {
        assertNull(parser.parseDurationToSeconds(""))
        assertNull(parser.parseDurationToSeconds("abc"))
        assertNull(parser.parseDurationToSeconds("1:2:3:4"))
    }
}
