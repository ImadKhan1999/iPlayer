package com.imadlabs.iplayer.data.media

/**
 * File-extension hints used for folder browsing and subtitle discovery. An extension only says
 * which files to *offer*; whether a file actually plays is decided by the engine at playback
 * time, and failures are reported to the user.
 */
object MediaFormats {
    val video = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "qt", "ts", "mts", "m2ts", "m2t", "tp", "trp",
        "3gp", "3g2", "ogv", "ogm", "flv", "f4v", "vob", "mpg", "mpeg", "mpe", "m1v", "m2v", "mpv",
        "wmv", "asf", "mxf", "dv", "divx", "xvid", "rm", "rmvb", "nut", "nsv", "y4m", "h264", "h265",
        "hevc", "264", "265", "av1", "ivf", "wtv", "dvr-ms", "amv", "evo", "bik",
    )
    val audio = setOf(
        "mp3", "aac", "m4a", "m4b", "flac", "wav", "wave", "aif", "aiff", "aifc", "alac", "ogg", "oga",
        "opus", "spx", "wma", "ape", "wv", "dsf", "dff", "ac3", "eac3", "ec3", "dts", "dtshd", "thd",
        "truehd", "mka", "mpc", "tta", "amr", "awb", "au", "snd", "caf", "mid", "midi", "mod", "xm",
        "it", "s3m", "w64", "tak", "ra", "mp2", "mpa", "mp1", "8svx", "voc", "gsm",
    )
    val subtitles = setOf("srt", "ass", "ssa", "vtt", "webvtt", "ttml", "dfxp", "sub", "idx", "smi", "sami", "sup", "txt", "usf", "jss", "lrc", "mpl", "pjs", "rt", "stl")
    val playlists = setOf("m3u", "m3u8", "pls", "xspf", "cue", "asx", "wpl")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()
    fun isVideo(name: String) = extension(name) in video
    fun isAudio(name: String) = extension(name) in audio
    fun isSubtitle(name: String) = extension(name) in subtitles && extension(name) != "lrc"
    fun isPlayable(name: String) = isVideo(name) || isAudio(name) || extension(name) in playlists
}
