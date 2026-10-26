package com.imadlabs.iplayer.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.db.PlaybackRecord
import com.imadlabs.iplayer.data.media.AudioItem
import com.imadlabs.iplayer.data.media.VideoItem
import com.imadlabs.iplayer.playback.QueueItem
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.MediaAccess
import com.imadlabs.iplayer.ui.MediaPermissions
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.Badge
import com.imadlabs.iplayer.ui.components.BrandProgress
import com.imadlabs.iplayer.ui.components.BrandTopBar
import com.imadlabs.iplayer.ui.components.MediaArtwork
import com.imadlabs.iplayer.ui.components.MediaThumb
import com.imadlabs.iplayer.ui.components.OpenUrlDialog
import com.imadlabs.iplayer.ui.components.SectionHeader
import com.imadlabs.iplayer.ui.components.openExternal
import com.imadlabs.iplayer.ui.components.play
import com.imadlabs.iplayer.ui.components.toQueueItem
import com.imadlabs.iplayer.ui.rememberMediaPermissionRequest
import com.imadlabs.iplayer.ui.theme.LocalImadExtras
import com.imadlabs.iplayer.util.Formatters
import android.net.Uri
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val access by MediaPermissions.access.collectAsStateWithLifecycle()
    val library by container.library.library.collectAsStateWithLifecycle()
    val recordsFlow = remember { container.database.playbackRecords().recent(60) }
    val records by recordsFlow.collectAsStateWithLifecycle(emptyList())
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var showUrl by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) container.openExternal(context, uri, context.contentResolver.getType(uri))
    }

    val videoByUri = remember(library) { library.videos.associateBy { it.uri.toString() } }
    val continueWatching = remember(records, videoByUri) {
        records.filter { it.isVideo && !it.completed && it.positionMs > 5000 }.take(12)
    }
    val recentVideos = remember(library) { library.videos.sortedByDescending { it.dateAddedSec }.take(15) }
    val recentAudio = remember(records) { records.filter { !it.isVideo }.take(15) }
    val folders by container.library.folders.collectAsStateWithLifecycle()
    // Video folders, newest activity first; each shows its most recently added video as the cover.
    val videoFolders = remember(folders) {
        folders.mapNotNull { f -> f.videos.maxByOrNull { it.dateAddedSec }?.let { latest -> Triple(f, latest, f.videos.size) } }
            .sortedByDescending { it.second.dateAddedSec }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { BrandTopBar(onOpen, scrollBehavior = scroll) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp)) {
            if (access == MediaAccess.None) item { AccessCard() }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Rounded.FileOpen, "Open file", Modifier.weight(1f)) {
                        openDoc.launch(arrayOf("video/*", "audio/*", "application/ogg", "application/x-matroska", "application/octet-stream"))
                    }
                    QuickAction(Icons.Rounded.Link, "Stream URL", Modifier.weight(1f)) { showUrl = true }
                    QuickAction(Icons.Rounded.Shuffle, "Shuffle music", Modifier.weight(1f)) {
                        val songs = library.music.shuffled().map { it.toQueueItem() }
                        if (songs.isNotEmpty()) { container.play(context, songs); container.playback.setShuffle(false) }
                    }
                }
            }
            if (continueWatching.isNotEmpty()) {
                item { SectionHeader("Continue watching") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching, key = { it.uri }) { r ->
                            ContinueCard(r, videoByUri[r.uri], settings.showThumbnails) {
                                container.play(context, listOf(QueueItem(r.uri, r.title, durationMs = r.durationMs, isVideo = true)),
                                    0, r.positionMs)
                            }
                        }
                    }
                }
            }
            if (recentVideos.isNotEmpty()) {
                item { SectionHeader("Recently added") { TextButton(onClick = { onOpen(Routes.VIDEOS) }) { Text("All videos") } } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentVideos, key = { it.id }) { v ->
                            VideoCard(v, settings.showThumbnails) {
                                container.play(context, recentVideos.map { it.toQueueItem() }, recentVideos.indexOf(v))
                            }
                        }
                    }
                }
            }
            if (videoFolders.isNotEmpty()) {
                item { SectionHeader("Video folders") }
                items(videoFolders, key = { "folder:" + it.first.path }) { (f, latest, count) ->
                    FolderRow(f.name, count, latest, settings.showThumbnails) { onOpen(Routes.folder(f.path)) }
                }
            }
            if (recentAudio.isNotEmpty()) {
                item { SectionHeader("Recently played music") { TextButton(onClick = { onOpen(Routes.MUSIC) }) { Text("Music") } } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentAudio, key = { it.uri }) { r ->
                            val audio = library.audio.firstOrNull { it.uri.toString() == r.uri }
                            AudioCard(r.title, audio?.artist, Uri.parse(r.uri)) {
                                container.play(context, listOf(audio?.toQueueItem() ?: QueueItem(r.uri, r.title, isVideo = false)))
                            }
                        }
                    }
                }
            }
            if (access != MediaAccess.None && library.loaded && library.videos.isEmpty() && library.audio.isEmpty()) {
                item {
                    Text("No videos or music were found on this device yet. Files you add will appear here automatically.",
                        Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (library.loaded) item {
                Text("${library.videos.size} videos · ${library.music.size} songs",
                    style = MaterialTheme.typography.bodySmall, color = LocalImadExtras.current.textMuted,
                    modifier = Modifier.padding(16.dp))
            }
        }
    }
    if (showUrl) OpenUrlDialog(onDismiss = { showUrl = false }) { url ->
        showUrl = false
        container.play(context, listOf(QueueItem(url, Uri.parse(url).lastPathSegment ?: url, isVideo = true)))
    }
}

@Composable
private fun AccessCard() {
    val request = rememberMediaPermissionRequest()
    val context = LocalContext.current
    val container = LocalContainer.current
    val asked by container.appState.mediaPermissionAsked.collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, LocalImadExtras.current.border),
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(Icons.Rounded.LockOpen, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text("Show your videos and music", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("iPlayer needs access to media on this device to build your library. Files stay on your device; nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { container.appState.setMediaPermissionAsked() }; request() }) { Text("Allow access") }
                if (asked) OutlinedButton(onClick = { context.startActivity(MediaPermissions.appSettingsIntent(context)) }) {
                    Text("App settings")
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, LocalImadExtras.current.border),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun ContinueCard(r: PlaybackRecord, video: VideoItem?, thumbs: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f)) {
            MediaArtwork(video?.let { MediaThumb(it.uri, true) }, Modifier.fillMaxSize(), showThumbnail = thumbs)
            Badge(Formatters.duration(r.durationMs - r.positionMs) + " left", Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        BrandProgress(if (r.durationMs > 0) r.positionMs.toFloat() / r.durationMs else 0f, Modifier.padding(top = 4.dp))
        Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun VideoCard(v: VideoItem, thumbs: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(180.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f)) {
            MediaArtwork(MediaThumb(v.uri, true), Modifier.fillMaxSize(), showThumbnail = thumbs)
            Badge(Formatters.duration(v.durationMs), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            v.resolutionLabel?.let { Badge(it, Modifier.align(Alignment.TopStart).padding(6.dp)) }
        }
        Text(v.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun AudioCard(title: String, artist: String?, uri: Uri, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        MediaArtwork(MediaThumb(uri, false), Modifier.size(132.dp), corner = 12.dp)
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
        if (artist != null) Text(artist, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FolderRow(name: String, count: Int, cover: VideoItem, thumbs: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(10.dp))) {
            MediaArtwork(MediaThumb(cover.uri, true), Modifier.fillMaxSize(), showThumbnail = thumbs)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 video" else "$count videos", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
