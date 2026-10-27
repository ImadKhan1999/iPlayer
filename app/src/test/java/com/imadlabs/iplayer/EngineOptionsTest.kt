package com.imadlabs.iplayer

import com.imadlabs.iplayer.data.settings.AppSettings
import com.imadlabs.iplayer.data.settings.ReplayGainMode
import com.imadlabs.iplayer.data.settings.StereoMode
import com.imadlabs.iplayer.playback.EngineOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineOptionsTest {
    @Test fun defaultsHaveNoOptionalFilters() {
        val o = EngineOptions.build(AppSettings())
        assertTrue("--audio-time-stretch" in o)
        assertFalse(o.any { it.startsWith("--audio-filter") || it.startsWith("--audio-replay-gain-mode") || it.startsWith("--stereo-mode") })
    }

    @Test fun audioProcessingOptions() {
        val o = EngineOptions.build(AppSettings(volumeNormalizer = true, stereoWiden = true, replayGain = ReplayGainMode.Album,
            replayGainPreamp = 2f, stereoMode = StereoMode.Mono))
        assertTrue("--audio-filter=normvol:stereo_widen" in o)
        assertTrue("--audio-replay-gain-mode=album" in o)
        assertTrue("--audio-replay-gain-preamp=2.0" in o)
        assertTrue("--stereo-mode=7" in o)
    }

    @Test fun subtitleBackgroundOpacityIsPercentToByte() {
        assertTrue("--freetype-background-opacity=255" in EngineOptions.build(AppSettings(subtitleBackgroundOpacity = 100)))
        assertTrue("--freetype-background-opacity=127" in EngineOptions.build(AppSettings(subtitleBackgroundOpacity = 50)))
    }

    @Test fun sameSettingsSameKey() {
        assertEquals(EngineOptions.build(AppSettings()), EngineOptions.build(AppSettings()))
    }
}
