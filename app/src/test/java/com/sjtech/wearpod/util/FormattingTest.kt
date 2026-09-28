package com.sjtech.wearpod.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun formatsDurationsUnderAnHour() {
        assertEquals("0:00", formatDurationShort(0))
        assertEquals("0:59", formatDurationShort(59))
        assertEquals("1:01", formatDurationShort(61))
        assertEquals("59:59", formatDurationShort(3_599))
    }

    @Test
    fun formatsDurationsOfAnHourOrMore() {
        assertEquals("1:00:00", formatDurationShort(3_600))
        assertEquals("1:01:01", formatDurationShort(3_661))
        assertEquals("10:00:05", formatDurationShort(36_005))
    }

    @Test
    fun missingDurationShowsPlaceholder() {
        assertEquals("--", formatDurationShort(null))
    }
}
