package com.naijasound.keyboard.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `counts are short and never round up`() {
        assertEquals("0", formatCount(0))
        assertEquals("950", formatCount(950))
        assertEquals("1k", formatCount(1_000))
        assertEquals("1.2k", formatCount(1_249))
        assertEquals("1.9k", formatCount(1_999))
        assertEquals("12.5k", formatCount(12_500))
        assertEquals("999k", formatCount(999_999))
        assertEquals("1.5M", formatCount(1_500_000))
    }

    @Test
    fun `sends label`() {
        assertEquals("1 send", sendsLabel(1))
        assertEquals("2 sends", sendsLabel(2))
        assertEquals("3.4k sends", sendsLabel(3_400))
    }
}
