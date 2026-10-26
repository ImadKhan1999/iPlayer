package com.imadlabs.iplayer.ui.components

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.imadlabs.iplayer.AppContainer
import com.imadlabs.iplayer.data.db.Favorite
import com.imadlabs.iplayer.data.db.PlaylistEntry
import com.imadlabs.iplayer.data.media.AudioItem
import com.imadlabs.iplayer.data.media.VideoItem
import com.imadlabs.iplayer.playback.QueueItem
import com.imadlabs.iplayer.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun VideoItem.toQueueItem() = QueueItem(uri.toString(), title, null, folderName, null, durationMs, true)
fun AudioItem.toQueueItem() = QueueItem(uri.toString(), title, artist, album, uri.toString(), durationMs, false)
fun QueueItem.toPlaylistEntry() = PlaylistEntry(0, 0, 0, uri, title, artist, durationMs, isVideo, artworkUri)
fun PlaylistEntry.toQueueItem() = QueueItem(uri, title, artist, null, artworkUri, durationMs, isVideo)

/** Plays [items] starting at [index]; video opens the full-screen player, audio stays in the app. */
fun AppContainer.play(context: Context, items: List<QueueItem>, index: Int = 0, positionMs: Long? = null) {
    if (items.isEmpty()) return
    playback.playQueue(items, index, positionMs)
    if (items[index.coerceIn(0, items.lastIndex)].isVideo) {
        context.startActivity(Intent(context, PlayerActivity::class.java).setAction(PlayerActivity.ACTION_RESUME))
    }
}

fun AppContainer.toggleFavorite(scopeLaunch: (suspend () -> Unit) -> Unit, item: QueueItem, isFavorite: Boolean) = scopeLaunch {
    if (isFavorite) database.favorites().remove(item.uri)
    else database.favorites().add(Favorite(item.uri, item.title, item.artist ?: item.album, item.isVideo, System.currentTimeMillis()))
}

fun shareMedia(context: Context, uri: Uri, mime: String?) {
    val send = Intent(Intent.ACTION_SEND).setType(mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share"))
}

/**
 * Delete and rename through MediaStore. On Android 11+ the system asks the user to confirm
 * (createDeleteRequest / createWriteRequest); iPlayer never modifies files silently.
 */
class MediaFileOps(
    private val context: Context,
    private val launch: (IntentSenderRequest) -> Unit,
) {
    var pendingRename: Pair<Uri, String>? = null
    var onDone: ((Boolean) -> Unit)? = null

    fun delete(uris: List<Uri>, done: (Boolean) -> Unit) {
        if (uris.isEmpty()) return
        onDone = done
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
            launch(IntentSenderRequest.Builder(pi.intentSender).build())
            return
        }
        try {
            uris.forEach { context.contentResolver.delete(it, null, null) }
            done(true)
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException) {
                launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } else {
                Toast.makeText(context, "iPlayer isn't allowed to delete this file.", Toast.LENGTH_LONG).show()
                done(false)
            }
        }
    }

    fun rename(uri: Uri, newDisplayName: String, done: (Boolean) -> Unit) {
        onDone = done
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(context, "Renaming needs Android 11 or newer.", Toast.LENGTH_LONG).show()
            done(false); return
        }
        pendingRename = uri to newDisplayName
        val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
        launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    suspend fun onResult(ok: Boolean) {
        val rename = pendingRename
        pendingRename = null
        if (ok && rename != null) {
            val renamed = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.update(rename.first,
                        ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, rename.second) }, null, null) > 0
                }.getOrDefault(false)
            }
            if (!renamed) Toast.makeText(context, "The file couldn't be renamed.", Toast.LENGTH_LONG).show()
            onDone?.invoke(renamed)
        } else onDone?.invoke(ok)
        onDone = null
    }
}

@Composable
fun rememberMediaFileOps(): MediaFileOps {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember { arrayOfNulls<MediaFileOps>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        scope.launch { holder[0]?.onResult(res.resultCode == Activity.RESULT_OK) }
    }
    return remember { MediaFileOps(context) { launcher.launch(it) }.also { holder[0] = it } }
}

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
