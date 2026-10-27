package com.imadlabs.iplayer.data.media

/** Recognises common TV-episode naming schemes. Purely name based; users can override results. */
object EpisodeParser {
    data class Episode(val show: String, val season: Int, val episode: Int)

    private val patterns = listOf(
        // Show.Name.S01E02, Show Name - s1e2, Show_Name.S01.E02
        Regex("""^(.*?)[\s._\-\[(]*[Ss](\d{1,2})[\s._\-]*[Ee][Pp]?(\d{1,3})"""),
        // Show Name 1x02
        Regex("""^(.*?)[\s._\-\[(]+(\d{1,2})[xX](\d{1,3})(?!\d)"""),
        // Show Name Season 1 Episode 2
        Regex("""^(.*?)[\s._\-]*[Ss]eason[\s._\-]*(\d{1,2})[\s._\-]*[Ee]pisode[\s._\-]*(\d{1,3})"""),
    )

    fun parse(fileName: String, parentFolder: String? = null): Episode? {
        val stem = fileName.substringBeforeLast('.')
        for (p in patterns) {
            val m = p.find(stem) ?: continue
            var show = clean(m.groupValues[1])
            if (show.isEmpty() && parentFolder != null) show = clean(seasonFolderShow(parentFolder) ?: parentFolder)
            if (show.isEmpty()) continue
            val season = m.groupValues[2].toIntOrNull() ?: continue
            val episode = m.groupValues[3].toIntOrNull() ?: continue
            return Episode(show, season, episode)
        }
        // "E05 - Title.mkv" inside a ".../Show/Season 2" folder
        if (parentFolder != null) {
            val season = seasonNumber(parentFolder)
            val ep = Regex("""(?:^|[\s._\-])[Ee][Pp]?(\d{1,3})(?!\d)""").find(stem)?.groupValues?.get(1)?.toIntOrNull()
            if (season != null && ep != null) return Episode(clean(seasonFolderShow(parentFolder) ?: "Unknown show"), season, ep)
        }
        return null
    }

    /** "Season 02" / "S2" folder → 2 */
    fun seasonNumber(folder: String): Int? {
        val last = folder.trimEnd('/').substringAfterLast('/')
        return Regex("""^(?:[Ss]eason|[Ss])[\s._\-]*(\d{1,2})$""").find(last.trim())?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun seasonFolderShow(folder: String): String? {
        val parts = folder.trimEnd('/').split('/')
        return if (parts.size >= 2 && seasonNumber(parts.last()) != null) parts[parts.size - 2] else null
    }

    private fun clean(s: String): String = s.replace('.', ' ').replace('_', ' ')
        .replace(Regex("""\s+"""), " ").trim(' ', '-', '[', '(')

    /** Folder name for a "Season N" grouping key */
    fun groupKey(e: Episode) = e.show.lowercase()
}
