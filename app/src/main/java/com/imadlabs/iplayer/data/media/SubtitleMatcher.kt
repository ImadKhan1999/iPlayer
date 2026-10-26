package com.imadlabs.iplayer.data.media

/**
 * Finds external subtitle files that belong to a video, VLC/MPC style:
 * "Movie.mkv" matches "Movie.srt", "Movie.en.srt", "Movie.English.forced.ass", "Movie.eng.sdh.vtt".
 * Results are ranked: exact base name first, then preferred-language matches, then others.
 */
object SubtitleMatcher {
    data class Match(val fileName: String, val language: String?, val score: Int)

    fun match(videoFileName: String, candidates: List<String>, preferredLanguage: String = ""): List<Match> {
        val base = videoFileName.substringBeforeLast('.').lowercase()
        val pref = preferredLanguage.lowercase()
        return candidates.asSequence()
            .filter { MediaFormats.isSubtitle(it) }
            .filter { !MediaFormats.extension(it).equals("txt") || it.substringBeforeLast('.').lowercase() == base }
            .mapNotNull { name ->
                val stem = name.substringBeforeLast('.').lowercase()
                when {
                    stem == base -> Match(name, null, 100)
                    stem.startsWith("$base.") || stem.startsWith("${base}_") || stem.startsWith("$base ") -> {
                        val tag = stem.substring(base.length + 1)
                        val lang = tag.split('.', '_', ' ', '-').firstOrNull { it.isNotBlank() && it !in noise }
                        val prefBonus = if (pref.isNotEmpty() && lang != null && languageMatches(lang, pref)) 40 else 0
                        Match(name, lang, 60 + prefBonus)
                    }
                    else -> null
                }
            }
            .sortedWith(compareByDescending<Match> { it.score }.thenBy { it.fileName })
            .toList()
    }

    private val noise = setOf("forced", "sdh", "hi", "cc", "default", "full")

    private val aliases = mapOf(
        "en" to setOf("en", "eng", "english"), "ar" to setOf("ar", "ara", "arabic"),
        "ur" to setOf("ur", "urd", "urdu"), "hi" to setOf("hin", "hindi"),
        "fr" to setOf("fr", "fre", "fra", "french"), "de" to setOf("de", "ger", "deu", "german"),
        "es" to setOf("es", "spa", "spanish"), "it" to setOf("it", "ita", "italian"),
        "pt" to setOf("pt", "por", "portuguese"), "ru" to setOf("ru", "rus", "russian"),
        "tr" to setOf("tr", "tur", "turkish"), "fa" to setOf("fa", "per", "fas", "persian"),
        "zh" to setOf("zh", "chi", "zho", "chinese"), "ja" to setOf("ja", "jpn", "japanese"),
        "ko" to setOf("ko", "kor", "korean"),
    )

    fun languageMatches(tag: String, preferred: String): Boolean {
        val t = tag.lowercase(); val p = preferred.lowercase()
        if (t == p) return true
        val group = aliases.values.firstOrNull { p in it } ?: aliases[p] ?: return false
        return t in group
    }
}
