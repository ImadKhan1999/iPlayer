package com.imadlabs.iplayer

import com.imadlabs.iplayer.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {
    @Test fun duration() {
        assertEquals("0:00", Formatters.duration(0))
        assertEquals("0:42", Formatters.duration(42_999))
        assertEquals("12:05", Formatters.duration(725_000))
        assertEquals("1:02:03", Formatters.duration(3_723_000))
        assertEquals("-0:05", Formatters.duration(-5_000))
    }

    @Test fun speed() {
        assertEquals("1.0×", Formatters.speed(1f))
        assertEquals("0.25×", Formatters.speed(0.25f))
        assertEquals("2.5×", Formatters.speed(2.5f))
        assertEquals("1.75×", Formatters.speed(1.75f))
    }

    @Test fun delay() {
        assertEquals("0 ms", Formatters.delay(0))
        assertEquals("+250 ms", Formatters.delay(250))
        assertEquals("-100 ms", Formatters.delay(-100))
        assertEquals("-1.50 s", Formatters.delay(-1500))
    }

    @Test fun sizesAndRates() {
        assertEquals("512 B", Formatters.fileSize(512))
        assertEquals("1.5 MB", Formatters.fileSize(1_572_864))
        assertEquals("320 kbps", Formatters.bitrate(320_000))
        assertEquals("Unknown", Formatters.bitrate(0))
        assertEquals("44.1 kHz", Formatters.sampleRate(44_100))
        assertEquals("48 kHz", Formatters.sampleRate(48_000))
        assertEquals("23.976 fps", Formatters.frameRate(24000, 1001))
        assertEquals("25 fps", Formatters.frameRate(25, 1))
        assertNull(Formatters.frameRate(0, 1))
    }
}
