package com.imadlabs.iplayer.data.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads lyrics from a sidecar file next to the track ("Song.lrc" or "Song.txt"). iPlayer does not
 * fetch lyrics from the internet. Reading sidecar files needs the folder to be readable
 * (all-files access on Android 11+), so the result explains when access is the obstacle.
 */
object LyricsLoader {
    sealed interface Result {
        data class Found(val lyrics: Lyrics, val source: String) : Result
        data object NotFound : Result
        data object NoAccess : Result
    }

    suspend fun load(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val path = when (uri.scheme) {
            "file" -> uri.path
            "content" -> runCatching {
                context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()
            else -> null
        } ?: return@withContext Result.NotFound
        val file = File(path)
        val dir = file.parentFile ?: return@withContext Result.NotFound
        val base = file.nameWithoutExtension
        val candidates = listOf("$base.lrc", "$base.LRC", "$base.txt")
        var sawUnreadable = false
        for (name in candidates) {
            val f = File(dir, name)
            if (!f.exists()) continue
            if (!f.canRead()) { sawUnreadable = true; continue }
            if (f.length() > 512 * 1024) continue
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            LrcParser.parse(text)?.let { return@withContext Result.Found(it, f.name) }
        }
        if (sawUnreadable || dir.list() == null) Result.NoAccess else Result.NotFound
    }
}
