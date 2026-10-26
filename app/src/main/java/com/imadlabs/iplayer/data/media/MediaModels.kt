package com.imadlabs.iplayer.data.media

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val title: String,
    val path: String?,
    val folder: String,
    val folderName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedSec: Long,
    val mimeType: String?,
) {
    val resolutionLabel: String?
        get() {
            val h = minOf(width, height).takeIf { it > 0 } ?: return null
            return when {
                h >= 2000 -> "4K"
                h >= 1400 -> "1440p"
                h >= 1000 -> "1080p"
                h >= 700 -> "720p"
                h >= 470 -> "480p"
                else -> "${h}p"
            }
        }
}

data class AudioItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val genre: String?,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val mimeType: String?,
    val path: String?,
    val folder: String,
    val isMusic: Boolean,
    val isRecording: Boolean,
    val isDownload: Boolean,
)

data class Album(val id: Long, val name: String, val artist: String, val year: Int, val tracks: List<AudioItem>) {
    val artUri: Uri? get() = tracks.firstOrNull()?.uri
}

data class Artist(val name: String, val albums: List<Album>, val tracks: List<AudioItem>)

data class Genre(val name: String, val tracks: List<AudioItem>)

data class MediaFolder(
    val path: String,
    val name: String,
    val videos: List<VideoItem>,
    val audio: List<AudioItem>,
) {
    val count get() = videos.size + audio.size
}

data class ShowGroup(val name: String, val seasons: Map<Int, List<Pair<EpisodeParser.Episode, VideoItem>>>) {
    val episodeCount get() = seasons.values.sumOf { it.size }
}

const val UNKNOWN_ARTIST = "Unknown artist"
const val UNKNOWN_ALBUM = "Unknown album"
