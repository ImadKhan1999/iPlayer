package com.imadlabs.iplayer.data.network

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.imadlabs.iplayer.data.media.MediaFormats
import com.imadlabs.iplayer.util.Diagnostics
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.MediaBrowser

data class NetworkEntry(val uri: Uri, val name: String, val isDirectory: Boolean)

sealed interface BrowseUpdate {
    data class Items(val entries: List<NetworkEntry>, val finished: Boolean) : BrowseUpdate
    data object AuthRequired : BrowseUpdate
}

/**
 * Browses SMB/FTP/SFTP/NFS/UPnP locations with LibVLC's MediaBrowser, on a dedicated light
 * LibVLC instance that lives only while a browse screen is open.
 */
class NetworkBrowser(context: Context, private val credentials: (Uri) -> Pair<String, String>?) {
    private val vlc = LibVLC(context.applicationContext, arrayListOf("--no-video", "--no-audio"))

    fun browse(uri: Uri, mediaOptions: List<String>): Flow<BrowseUpdate> = callbackFlow {
        val entries = mutableListOf<NetworkEntry>()
        var loginTried = false
        Dialog.setCallbacks(vlc, object : Dialog.Callbacks {
            override fun onDisplay(dialog: Dialog.ErrorMessage) {
                Diagnostics.log("Network", "Browse error: ${dialog.text}")
            }
            override fun onDisplay(dialog: Dialog.LoginDialog) {
                val c = credentials(uri)
                if (c != null && !loginTried) { loginTried = true; dialog.postLogin(c.first, c.second, false) }
                else { dialog.dismiss(); trySend(BrowseUpdate.AuthRequired) }
            }
            override fun onDisplay(dialog: Dialog.QuestionDialog) = dialog.dismiss()
            override fun onDisplay(dialog: Dialog.ProgressDialog) = Unit
            override fun onCanceled(dialog: Dialog?) = Unit
            override fun onProgressUpdate(dialog: Dialog.ProgressDialog) = Unit
        })
        val browser = MediaBrowser(vlc, object : MediaBrowser.EventListener {
            override fun onMediaAdded(index: Int, media: IMedia) {
                toEntry(media)?.let { entries += it; trySend(BrowseUpdate.Items(sorted(entries), false)) }
            }
            override fun onMediaRemoved(index: Int, media: IMedia) = Unit
            override fun onBrowseEnd() { trySend(BrowseUpdate.Items(sorted(entries), true)) }
        }, Handler(Looper.getMainLooper()))
        val media = Media(vlc, uri)
        mediaOptions.forEach(media::addOption)
        browser.browse(media, MediaBrowser.Flag.Interact)
        media.release()
        awaitClose { browser.release() }
    }

    /** Discovers UPnP/DLNA servers and SMB shares on the local network. */
    fun discover(): Flow<BrowseUpdate> = callbackFlow {
        val entries = mutableListOf<NetworkEntry>()
        val browser = MediaBrowser(vlc, object : MediaBrowser.EventListener {
            override fun onMediaAdded(index: Int, media: IMedia) {
                toEntry(media)?.let { e -> if (entries.none { it.uri == e.uri }) { entries += e; trySend(BrowseUpdate.Items(sorted(entries), false)) } }
            }
            override fun onMediaRemoved(index: Int, media: IMedia) {
                entries.removeAll { it.uri == media.uri }
                trySend(BrowseUpdate.Items(sorted(entries), false))
            }
            override fun onBrowseEnd() { trySend(BrowseUpdate.Items(sorted(entries), true)) }
        }, Handler(Looper.getMainLooper()))
        browser.discoverNetworkShares()
        awaitClose { browser.release() }
    }

    private fun toEntry(media: IMedia): NetworkEntry? {
        val uri = media.uri ?: return null
        val dir = media.type == IMedia.Type.Directory
        val name = media.getMeta(IMedia.Meta.Title)?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment ?: uri.host ?: uri.toString()
        if (!dir) {
            // Hide documents, images and subtitle files; keep media and extension-less UPnP items.
            val ext = MediaFormats.extension(uri.lastPathSegment ?: name)
            if (ext.isNotEmpty() && !MediaFormats.isPlayable("x.$ext")) return null
        }
        return NetworkEntry(uri, name, dir)
    }

    private fun sorted(list: List<NetworkEntry>) = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

    fun release() = vlc.release()
}
