package com.imadlabs.iplayer.data.media

/** Lyrics from a sidecar .lrc (timestamped) or .txt (plain) file next to the audio file. */
sealed interface Lyrics {
    data class Synced(val lines: List<Line>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data class Line(val timeMs: Long, val text: String)
}

object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    /** Parses LRC text. Returns [Lyrics.Plain] when no timestamps are present, null for empty input. */
    fun parse(text: String): Lyrics? {
        if (text.isBlank()) return null
        var offset = 0L
        val lines = mutableListOf<Lyrics.Line>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            offsetTag.find(line)?.let { offset = it.groupValues[1].toLong(); continue }
            val stamps = stamp.findAll(line).toList()
            if (stamps.isEmpty() || stamps.first().range.first != 0) continue
            val content = line.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                // LRC offset: positive values make lyrics appear sooner
                lines += Lyrics.Line((min * 60_000 + sec * 1000 + ms - offset).coerceAtLeast(0), content)
            }
        }
        if (lines.isEmpty()) {
            val plain = text.lineSequence().filterNot { it.trim().matches(Regex("""^\[[a-zA-Z]+:.*]$""")) }
                .joinToString("\n").trim()
            return if (plain.isEmpty()) null else Lyrics.Plain(plain)
        }
        return Lyrics.Synced(lines.sortedBy { it.timeMs })
    }

    /** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
    fun activeIndex(lines: List<Lyrics.Line>, positionMs: Long): Int {
        var lo = 0; var hi = lines.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
