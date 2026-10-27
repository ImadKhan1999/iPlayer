package com.imadlabs.iplayer.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.imadlabs.iplayer.util.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Reads the device media index (MediaStore). Android maintains this index itself, so iPlayer
 * never walks the whole file system; it re-queries only when MediaStore reports a change.
 */
class MediaStoreSource(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    private val videoCollection = if (Build.VERSION.SDK_INT >= 29)
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    private val audioCollection = if (Build.VERSION.SDK_INT >= 29)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    /** Emits Unit whenever audio or video in MediaStore changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(videoCollection, true, observer)
        resolver.registerContentObserver(audioCollection, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    suspend fun queryVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        val out = ArrayList<VideoItem>()
        safeQuery(videoCollection, projection, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val title = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val data = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val size = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val w = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val h = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val added = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mime = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val bucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val vid = c.getLong(id)
                val display = c.getString(name) ?: continue
                val path = c.getString(data)
                val folder = path?.substringBeforeLast('/', "") ?: ""
                out += VideoItem(
                    id = vid,
                    uri = ContentUris.withAppendedId(videoCollection, vid),
                    displayName = display,
                    title = display.substringBeforeLast('.').ifBlank { c.getString(title) ?: display },
                    path = path,
                    folder = folder,
                    folderName = c.getString(bucket) ?: folder.substringAfterLast('/'),
                    durationMs = c.getLong(dur),
                    sizeBytes = c.getLong(size),
                    width = c.getInt(w),
                    height = c.getInt(h),
                    dateAddedSec = c.getLong(added),
                    mimeType = c.getString(mime),
                )
            }
        }
        out
    }

    suspend fun queryAudio(): List<AudioItem> = withContext(Dispatchers.IO) {
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.IS_MUSIC, "album_artist",
        )
        if (Build.VERSION.SDK_INT >= 30) cols += MediaStore.Audio.Media.GENRE
        if (Build.VERSION.SDK_INT >= 31) cols += MediaStore.Audio.Media.IS_RECORDING
        val genresById = if (Build.VERSION.SDK_INT < 30) legacyGenres() else emptyMap()
        val out = ArrayList<AudioItem>()
        safeQuery(audioCollection, cols.toTypedArray(), null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val mime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val isMusic = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
            val albumArtist = c.getColumnIndex("album_artist")
            val genre = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            val recording = if (Build.VERSION.SDK_INT >= 31) c.getColumnIndex(MediaStore.Audio.Media.IS_RECORDING) else -1
            while (c.moveToNext()) {
                val aid = c.getLong(id)
                val display = c.getString(name) ?: continue
                val path = c.getString(data)
                val folder = path?.substringBeforeLast('/', "") ?: ""
                val lowerFolder = folder.lowercase()
                val rawTrack = c.getInt(track)
                val rec = (recording >= 0 && c.getInt(recording) == 1) ||
                    listOf("/recordings", "/voice recorder", "/call", "/soundrecorder", "/recorder").any { lowerFolder.contains(it) }
                out += AudioItem(
                    id = aid,
                    uri = ContentUris.withAppendedId(audioCollection, aid),
                    displayName = display,
                    title = c.getString(title)?.takeIf { it.isNotBlank() } ?: display.substringBeforeLast('.'),
                    artist = c.getString(artist)?.takeUnless { it.isBlank() || it == "<unknown>" } ?: UNKNOWN_ARTIST,
                    album = c.getString(album)?.takeUnless { it.isBlank() || it == "<unknown>" } ?: UNKNOWN_ALBUM,
                    albumId = c.getLong(albumId),
                    albumArtist = if (albumArtist >= 0) c.getString(albumArtist)?.takeIf { it.isNotBlank() } else null,
                    genre = if (genre >= 0) c.getString(genre)?.takeIf { it.isNotBlank() } else genresById[aid],
                    // MediaStore encodes disc in the thousands: 2005 = disc 2, track 5
                    trackNumber = rawTrack % 1000,
                    discNumber = (rawTrack / 1000).coerceAtLeast(1),
                    year = c.getInt(year),
                    durationMs = c.getLong(dur),
                    sizeBytes = c.getLong(size),
                    dateAddedSec = c.getLong(added),
                    mimeType = c.getString(mime),
                    path = path,
                    folder = folder,
                    isMusic = c.getInt(isMusic) != 0,
                    isRecording = rec,
                    isDownload = lowerFolder.contains("/download"),
                )
            }
        }
        out
    }

    @Suppress("DEPRECATION")
    private fun legacyGenres(): Map<Long, String> {
        val map = HashMap<Long, String>()
        val genres = safeQuery(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null) ?: return map
        genres.use { g ->
            while (g.moveToNext()) {
                val gid = g.getLong(0)
                val name = g.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                safeQuery(MediaStore.Audio.Genres.Members.getContentUri("external", gid),
                    arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null)?.use { m ->
                    while (m.moveToNext()) map[m.getLong(0)] = name
                }
            }
        }
        return map
    }

    private fun safeQuery(uri: android.net.Uri, projection: Array<String>, selection: String?): Cursor? = try {
        resolver.query(uri, projection, selection, null, null)
    } catch (e: SecurityException) {
        Diagnostics.log("Library", "MediaStore query denied (permission not granted)")
        null
    } catch (e: Exception) {
        Diagnostics.log("Library", "MediaStore query failed", e)
        null
    }
}
