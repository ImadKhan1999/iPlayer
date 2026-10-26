package com.imadlabs.iplayer.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

object Formatters {
    /** 0:42, 12:05, 1:02:03. Negative values are rendered with a leading minus sign. */
    fun duration(ms: Long): String {
        if (ms < 0) return "-" + duration(-ms)
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun fileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
        return String.format(Locale.US, "%.1f %s", bytes / 1024.0.pow(exp), units[exp - 1])
    }

    fun bitrate(bitsPerSecond: Long): String = when {
        bitsPerSecond <= 0 -> "Unknown"
        bitsPerSecond >= 1_000_000 -> String.format(Locale.US, "%.2f Mbps", bitsPerSecond / 1_000_000.0)
        else -> String.format(Locale.US, "%d kbps", bitsPerSecond / 1000)
    }

    fun frameRate(num: Int, den: Int): String? {
        if (num <= 0 || den <= 0) return null
        val fps = num.toDouble() / den
        return String.format(Locale.US, if (abs(fps - fps.toInt()) < 0.005) "%.0f fps" else "%.3f fps", fps)
    }

    fun sampleRate(hz: Int): String = if (hz % 1000 == 0) "${hz / 1000} kHz"
        else String.format(Locale.US, "%.1f kHz", hz / 1000.0)

    fun channels(count: Int): String = when (count) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$count channels"
    }

    /** Subtitle/audio delay in milliseconds shown with sign: +250 ms, -1.50 s. */
    fun delay(ms: Long): String = when {
        ms == 0L -> "0 ms"
        abs(ms) < 1000 -> (if (ms > 0) "+" else "") + "$ms ms"
        else -> (if (ms > 0) "+" else "-") + String.format(Locale.US, "%.2f s", abs(ms) / 1000.0)
    }

    /** 1.0×, 1.25×, 0.5× */
    fun speed(rate: Float): String {
        var s = String.format(Locale.US, "%.2f", rate).trimEnd('0')
        if (s.endsWith('.')) s += "0"
        return s + "×"
    }
}
