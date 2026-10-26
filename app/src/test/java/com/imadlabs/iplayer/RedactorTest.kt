package com.imadlabs.iplayer

import com.imadlabs.iplayer.util.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    @Test fun removesUrlCredentials() {
        val out = Redactor.redact("Opening smb://alice:s3cret@nas.local/Movies/a.mkv")
        assertEquals("Opening smb://***@nas.local/Movies/a.mkv", out)
    }

    @Test fun removesTokens() {
        val out = Redactor.redact("https://cdn.example.com/v.m3u8?token=abc123&quality=hd&sig=zzz")
        assertFalse(out.contains("abc123"))
        assertFalse(out.contains("zzz"))
        assertEquals("https://cdn.example.com/v.m3u8?token=***&quality=hd&sig=***", out)
    }

    @Test fun removesHeadersAndVlcOptions() {
        assertFalse(Redactor.redact("Authorization: Bearer eyJhbGci").contains("eyJ"))
        assertEquals(":smb-pwd=*** :smb-user=***", Redactor.redact(":smb-pwd=hunter2 :smb-user=bob"))
    }

    @Test fun leavesOrdinaryTextAlone() {
        val text = "Playing /storage/emulated/0/Movies/Film.mkv at 1.5x"
        assertEquals(text, Redactor.redact(text))
    }
}
