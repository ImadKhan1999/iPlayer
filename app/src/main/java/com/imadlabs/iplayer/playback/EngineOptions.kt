package com.imadlabs.iplayer.playback

import com.imadlabs.iplayer.data.settings.AppSettings
import com.imadlabs.iplayer.data.settings.ReplayGainMode
import com.imadlabs.iplayer.data.settings.StereoMode
import java.util.Locale

/**
 * LibVLC instance options derived from settings. These are instance-wide in LibVLC, so a change
 * means the engine is re-created (current playback resumes at the same position).
 */
object EngineOptions {
    fun build(s: AppSettings): List<String> {
        val o = mutableListOf(
            "--audio-time-stretch",            // keep pitch when changing speed
            "--avcodec-skiploopfilter=1",
            "--avcodec-threads=0",
            "--http-reconnect",
            "--stats", // needed for dropped-frame statistics
            "--freetype-rel-fontsize=${s.subtitleSize.vlcRelFontSize}",
            "--freetype-color=${s.subtitleColor.rgb}",
            "--freetype-outline-thickness=${s.subtitleOutline.vlcValue}",
            "--freetype-background-opacity=${(s.subtitleBackgroundOpacity * 255 / 100).coerceIn(0, 255)}",
            "--freetype-shadow-opacity=${if (s.subtitleShadow) 128 else 0}",
            "--sub-margin=${s.subtitleBottomMargin.coerceIn(0, 400)}",
        )
        if (s.subtitleBold) o += "--freetype-bold"
        if (s.subtitleEncoding.isNotBlank()) o += "--subsdec-encoding=${s.subtitleEncoding}"
        o += if (s.subtitleAutoDetect) "--sub-autodetect-file" else "--no-sub-autodetect-file"
        if (s.replayGain != ReplayGainMode.Off) {
            o += "--audio-replay-gain-mode=${s.replayGain.vlcValue}"
            o += "--audio-replay-gain-preamp=${String.format(Locale.US, "%.1f", s.replayGainPreamp)}"
        }
        val filters = buildList {
            if (s.volumeNormalizer) add("normvol")
            if (s.stereoWiden) add("stereo_widen")
        }
        if (filters.isNotEmpty()) o += "--audio-filter=${filters.joinToString(":")}"
        if (s.volumeNormalizer) o += "--norm-max-level=2.0"
        if (s.stereoMode != StereoMode.Default) o += "--stereo-mode=${s.stereoMode.vlcValue}"
        return o
    }
}
