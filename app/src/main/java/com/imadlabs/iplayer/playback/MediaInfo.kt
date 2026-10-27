package com.imadlabs.iplayer.playback

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.imadlabs.iplayer.util.Formatters
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

/**
 * Technical information reported by the engine. Every field is optional: values the engine
 * cannot provide are omitted rather than guessed.
 */
data class MediaInfo(val sections: List<Section>) {
    data class Section(val title: String, val rows: List<Pair<String, String>>)

    companion object {
        fun from(context: Context, state: PlayerState, media: IMedia, player: MediaPlayer): MediaInfo {
            try {
                val sections = mutableListOf<Section>()
                val item = state.item
                val general = mutableListOf<Pair<String, String>>()
                item?.let { general += "Title" to it.title }
                val uri = item?.uri?.let(Uri::parse)
                val (name, size) = uri?.let { nameAndSize(context, it) } ?: (null to null)
                name?.let {
                    general += "File" to it
                    val ext = it.substringAfterLast('.', "")
                    if (ext.isNotEmpty()) general += "Container" to ext.uppercase()
                }
                size?.let { general += "File size" to Formatters.fileSize(it) }
                if (state.durationMs > 0) general += "Duration" to Formatters.duration(state.durationMs)
                general += "Decoder mode" to state.decoder.label
                uri?.scheme?.let { if (it != "content" && it != "file") general += "Protocol" to it.uppercase() }
                sections += Section("General", general)

                var streams = 0
                for (i in 0 until media.trackCount) {
                    val t = media.getTrack(i) ?: continue
                    streams++
                    val rows = mutableListOf<Pair<String, String>>()
                    rows += "Codec" to codecName(t)
                    if (t.profile > 0) rows += "Profile" to t.profile.toString()
                    if (t.level > 0) rows += "Level" to t.level.toString()
                    if (t.bitrate > 0) rows += "Bitrate" to Formatters.bitrate(t.bitrate.toLong())
                    t.language?.takeIf { it.isNotBlank() }?.let { rows += "Language" to it }
                    t.description?.takeIf { it.isNotBlank() }?.let { rows += "Description" to it }
                    val title = when (t) {
                        is IMedia.VideoTrack -> {
                            if (t.width > 0) rows += "Resolution" to "${t.width} × ${t.height}"
                            if (t.sarNum > 0 && t.sarDen > 0 && t.sarNum != t.sarDen) rows += "Pixel aspect" to "${t.sarNum}:${t.sarDen}"
                            Formatters.frameRate(t.frameRateNum, t.frameRateDen)?.let { rows += "Frame rate" to it }
                            hdrHint(t)?.let { rows += "HDR" to it }
                            "Video"
                        }
                        is IMedia.AudioTrack -> {
                            if (t.channels > 0) rows += "Channels" to Formatters.channels(t.channels)
                            if (t.rate > 0) rows += "Sample rate" to Formatters.sampleRate(t.rate)
                            "Audio"
                        }
                        is IMedia.SubtitleTrack -> {
                            t.encoding?.takeIf { it.isNotBlank() }?.let { rows += "Encoding" to it }
                            "Subtitle"
                        }
                        else -> "Stream"
                    }
                    sections += Section("$title #${t.id}", rows)
                }
                if (streams == 0) sections += Section("Streams", listOf("Status" to "Stream details are not available yet"))

                media.stats?.let { st ->
                    val rows = mutableListOf<Pair<String, String>>()
                    if (st.inputBitrate > 0) rows += "Input bitrate" to Formatters.bitrate((st.inputBitrate * 8_000_000).toLong())
                    if (st.demuxBitrate > 0) rows += "Demux bitrate" to Formatters.bitrate((st.demuxBitrate * 8_000_000).toLong())
                    if (st.decodedVideo > 0) rows += "Decoded video frames" to st.decodedVideo.toString()
                    if (st.displayedPictures > 0) rows += "Displayed frames" to st.displayedPictures.toString()
                    if (st.displayedPictures > 0 || st.lostPictures > 0) rows += "Dropped frames" to st.lostPictures.toString()
                    if (st.lostAbuffers > 0) rows += "Lost audio buffers" to st.lostAbuffers.toString()
                    if (st.demuxCorrupted > 0) rows += "Corrupted packets" to st.demuxCorrupted.toString()
                    if (rows.isNotEmpty()) sections += Section("Playback statistics", rows)
                }
                if (state.speed != 1f) sections += Section("Playback", listOf("Speed" to Formatters.speed(state.speed)))
                return MediaInfo(sections)
            } finally {
                media.release()
            }
        }

        private fun codecName(t: IMedia.Track): String {
            val raw = (t.originalCodec ?: t.codec ?: "").trim()
            return knownCodecs[raw.lowercase()] ?: raw.ifBlank { "Unknown" }
        }

        /** Transfer characteristics are not exposed by LibVLC 3; a 10-bit HEVC/AV1/VP9 profile is only a hint. */
        private fun hdrHint(t: IMedia.VideoTrack): String? {
            val c = (t.codec ?: "").lowercase()
            return when {
                c.contains("hevc") && t.profile == 2 -> "Main 10 profile (HDR-capable; transfer function not reported)"
                c.contains("vp9") && t.profile >= 2 -> "10/12-bit profile (HDR-capable; transfer function not reported)"
                else -> null
            }
        }

        private val knownCodecs = mapOf(
            "h264" to "H.264 / AVC", "avc1" to "H.264 / AVC", "hevc" to "H.265 / HEVC", "h265" to "H.265 / HEVC",
            "av01" to "AV1", "vp80" to "VP8", "vp90" to "VP9", "mpgv" to "MPEG-2 Video", "mp2v" to "MPEG-2 Video",
            "mp4v" to "MPEG-4 Part 2", "xvid" to "MPEG-4 Part 2 (Xvid)", "divx" to "MPEG-4 Part 2 (DivX)",
            "wvc1" to "VC-1", "wmv3" to "WMV9 / VC-1", "mjpg" to "Motion JPEG", "apcn" to "ProRes 422",
            "apch" to "ProRes 422 HQ", "apcs" to "ProRes 422 LT", "ap4h" to "ProRes 4444", "theo" to "Theora",
            "mp4a" to "AAC", "mpga" to "MPEG Audio (MP1/2)", "mp3 " to "MP3", "mp3" to "MP3", "flac" to "FLAC", "opus" to "Opus",
            "vorb" to "Vorbis", "a52 " to "AC-3", "a52" to "AC-3", "eac3" to "E-AC-3", "dts " to "DTS", "dts" to "DTS",
            "mlp " to "TrueHD/MLP", "trhd" to "Dolby TrueHD", "alac" to "ALAC", "wma2" to "WMA", "wmap" to "WMA Pro",
            "ape " to "Monkey's Audio", "wvpk" to "WavPack", "araw" to "PCM", "s16l" to "PCM 16-bit", "s24l" to "PCM 24-bit",
            "fl32" to "PCM float", "dsd " to "DSD", "subt" to "Text subtitles", "ssa " to "SSA/ASS", "tx3g" to "MP4 timed text",
            "spu " to "VobSub", "pgs " to "PGS (Blu-ray)", "dvbs" to "DVB subtitles", "webv" to "WebVTT", "ttml" to "TTML",
        )

        private fun nameAndSize(context: Context, uri: Uri): Pair<String?, Long?> = when (uri.scheme) {
            "content" -> runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) to (if (it.isNull(1)) null else it.getLong(1)) else null
                }
            }.getOrNull() ?: (null to null)
            "file" -> uri.path?.let { java.io.File(it) }?.let { it.name to it.length().takeIf { l -> l > 0 } } ?: (null to null)
            else -> (uri.lastPathSegment to null)
        }
    }
}
