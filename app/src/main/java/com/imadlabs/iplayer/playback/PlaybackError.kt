package com.imadlabs.iplayer.playback

/** User-facing playback problem with an optional next action. Technical detail is for diagnostics. */
data class PlaybackError(
    val kind: Kind,
    val message: String,
    val technical: String? = null,
    val actions: List<Action> = emptyList(),
) {
    enum class Kind { NotFound, Permission, Network, Auth, Decoder, Unsupported, Unknown }
    enum class Action(val label: String) {
        TrySoftware("Try software decoding"),
        TryHardware("Try hardware decoding"),
        Retry("Retry"),
        SkipNext("Play next"),
    }
}

private val networkSchemes = setOf("http", "https", "smb", "ftp", "ftps", "sftp", "nfs", "rtsp", "rtmp", "mms", "udp", "rtp", "upnp")

fun isNetworkUri(uri: String): Boolean = uri.substringBefore("://", "").lowercase() in networkSchemes

object PlaybackErrors {
    fun notFound() = PlaybackError(PlaybackError.Kind.NotFound,
        "This file can't be found. It may have been moved, renamed or deleted.",
        actions = listOf(PlaybackError.Action.SkipNext))

    fun permission() = PlaybackError(PlaybackError.Kind.Permission,
        "iPlayer doesn't have permission to read this file. Open it again from your file manager or grant media access in Settings.")

    fun auth() = PlaybackError(PlaybackError.Kind.Auth,
        "The server rejected the sign-in. Check the user name and password saved for this network source.",
        actions = listOf(PlaybackError.Action.Retry))

    fun network(detail: String?) = PlaybackError(PlaybackError.Kind.Network,
        "Couldn't load this stream. Check your connection and that the address points to a media file or stream.",
        detail, listOf(PlaybackError.Action.Retry))

    fun decoder(current: com.imadlabs.iplayer.data.settings.DecoderMode, detail: String?) = PlaybackError(
        PlaybackError.Kind.Decoder,
        "Unable to play this file with the current decoder. Try another playback mode.",
        detail,
        if (current == com.imadlabs.iplayer.data.settings.DecoderMode.Software)
            listOf(PlaybackError.Action.TryHardware, PlaybackError.Action.SkipNext)
        else listOf(PlaybackError.Action.TrySoftware, PlaybackError.Action.SkipNext),
    )
}
