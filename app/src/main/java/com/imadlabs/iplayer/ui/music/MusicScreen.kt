package com.imadlabs.iplayer.ui.music

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.db.Favorite
import com.imadlabs.iplayer.data.media.AudioItem
import com.imadlabs.iplayer.data.settings.SortOrder
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.MediaAccess
import com.imadlabs.iplayer.ui.MediaPermissions
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.BrandTopBar
import com.imadlabs.iplayer.ui.components.EmptyState
import com.imadlabs.iplayer.ui.components.MediaArtwork
import com.imadlabs.iplayer.ui.components.MediaThumb
import com.imadlabs.iplayer.ui.components.play
import com.imadlabs.iplayer.ui.components.rememberMediaFileOps
import com.imadlabs.iplayer.ui.components.shareMedia
import com.imadlabs.iplayer.ui.components.toQueueItem
import com.imadlabs.iplayer.ui.components.toast
import com.imadlabs.iplayer.ui.library.AddToPlaylistDialog
import com.imadlabs.iplayer.ui.rememberMediaPermissionRequest
import com.imadlabs.iplayer.ui.videos.MenuEntry
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.launch

private enum class MusicTab(val label: String) {
    Songs("Songs"), Albums("Albums"), Artists("Artists"), Genres("Genres"), Folders("Folders"),
    Recent("Recently played"), Added("Recently added"), Favorites("Favorites"), MostPlayed("Most played"),
    Downloads("Downloads"), Recordings("Recordings"),
}

fun List<AudioItem>.sortedBy(order: SortOrder): List<AudioItem> = when (order) {
    SortOrder.DateAddedDesc -> sortedByDescending { it.dateAddedSec }
    SortOrder.DateAddedAsc -> sortedBy { it.dateAddedSec }
    SortOrder.NameAsc -> sortedBy { it.title.lowercase() }
    SortOrder.NameDesc -> sortedByDescending { it.title.lowercase() }
    SortOrder.DurationDesc -> sortedByDescending { it.durationMs }
    SortOrder.DurationAsc -> sortedBy { it.durationMs }
    SortOrder.SizeDesc -> sortedByDescending { it.sizeBytes }
    SortOrder.SizeAsc -> sortedBy { it.sizeBytes }
}

@Composable
fun MusicScreen(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val access by MediaPermissions.access.collectAsStateWithLifecycle()
    val library by container.library.library.collectAsStateWithLifecycle()
    val albums by container.library.albums.collectAsStateWithLifecycle()
    val artists by container.library.artists.collectAsStateWithLifecycle()
    val genres by container.library.genres.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val recentFlow = remember { container.database.playbackRecords().recent(100) }
    val recent by recentFlow.collectAsStateWithLifecycle(emptyList())
    val mostFlow = remember { container.database.playbackRecords().mostPlayedAudio(100) }
    val most by mostFlow.collectAsStateWithLifecycle(emptyList())
    val favFlow = remember { container.database.favorites().all() }
    val favs by favFlow.collectAsStateWithLifecycle(emptyList())
    val request = rememberMediaPermissionRequest()
    var tab by rememberSaveable { mutableStateOf(MusicTab.Songs) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }

    val byUri = remember(library) { library.audio.associateBy { it.uri.toString() } }
    // Sorting and grouping thousands of tracks is kept out of recomposition.
    val songs = remember(library, settings.musicSort) { library.music.sortedBy(settings.musicSort) }
    val added = remember(library) { library.music.sortedByDescending { it.dateAddedSec }.take(200) }
    val audioFolders = remember(library) {
        library.audio.groupBy { it.folder }.map { (path, list) ->
            Triple(path.substringAfterLast('/').ifBlank { path }, "${list.size} files · $path", Routes.folder(path))
        }.sortedBy { it.first.lowercase() }
    }
    fun filter(list: List<AudioItem>) = if (query.isBlank()) list else list.filter {
        it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
    }

    Scaffold(topBar = {
        BrandTopBar(onOpen, title = "Music") {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Rounded.Search, "Search music") }
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            if (access == MediaAccess.None) {
                EmptyState(Icons.Rounded.LibraryMusic, "Your music will appear here",
                    "Allow iPlayer to read audio on this device to build your music library.", actionLabel = "Allow access", onAction = request)
                return@Column
            }
            if (searching) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Songs, artists, albums") }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                MusicTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) }) }
            }
            when (tab) {
                MusicTab.Songs -> SongList(remember(songs, query) { filter(songs) }, "No songs yet", onOpen, showShuffle = true)
                MusicTab.Albums -> {
                    val list = albums.filter { query.isBlank() || it.name.contains(query, true) || it.artist.contains(query, true) }
                    if (list.isEmpty()) EmptyState(Icons.Rounded.LibraryMusic, "No albums", "Albums are built from your music's tags.")
                    else LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(list, key = { it.id }) { a ->
                            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(Routes.album(a.id)) }) {
                                MediaArtwork(a.artUri?.let { MediaThumb(it, false, a.id) }, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp)
                                Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                Text(a.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
                MusicTab.Artists -> GroupList(artists.filter { query.isBlank() || it.name.contains(query, true) }.map {
                    Triple(it.name, "${it.albums.size} albums · ${it.tracks.size} songs", Routes.artist(it.name))
                }, Icons.Rounded.Person, onOpen)
                MusicTab.Genres -> GroupList(genres.map { Triple(it.name, "${it.tracks.size} songs", Routes.genre(it.name)) },
                    Icons.Rounded.LibraryMusic, onOpen)
                MusicTab.Folders -> GroupList(audioFolders, Icons.Rounded.Folder, onOpen)
                MusicTab.Recent -> SongList(filter(recent.filter { !it.isVideo }.mapNotNull { byUri[it.uri] }), "Nothing played yet", onOpen)
                MusicTab.Added -> SongList(remember(added, query) { filter(added) }, "No music yet", onOpen)
                MusicTab.Favorites -> SongList(filter(favs.filter { !it.isVideo }.mapNotNull { byUri[it.uri] }),
                    "Tap ⋮ › Add to favorites on any song", onOpen)
                MusicTab.MostPlayed -> SongList(filter(most.mapNotNull { byUri[it.uri] }), "Play some music to see it here", onOpen)
                MusicTab.Downloads -> SongList(filter(library.audio.filter { it.isDownload }), "No audio in Download folders", onOpen)
                MusicTab.Recordings -> SongList(filter(library.audio.filter { it.isRecording }), "No voice or call recordings found", onOpen)
            }
        }
    }
}

@Composable
private fun GroupList(rows: List<Triple<String, String, String>>, icon: androidx.compose.ui.graphics.vector.ImageVector, onOpen: (String) -> Unit) {
    if (rows.isEmpty()) { EmptyState(icon, "Nothing here yet", "This list fills in from your music library."); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(rows, key = { it.third }) { (title, sub, route) ->
            ListItem(headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(route) })
        }
    }
}

@Composable
fun SongList(
    songs: List<AudioItem>,
    emptyText: String,
    onOpen: (String) -> Unit,
    showShuffle: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    showArt: Boolean = true,
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    if (songs.isEmpty() && header == null) { EmptyState(Icons.Rounded.LibraryMusic, emptyText, "Music on this device is picked up automatically."); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        header?.let { h -> item { h() } }
        if (showShuffle || header != null) item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { container.play(context, songs.map { it.toQueueItem() }); container.playback.setShuffle(false) },
                    label = { Text("Play all") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) })
                AssistChip(onClick = {
                    val q = songs.map { it.toQueueItem() }
                    container.play(context, q, q.indices.randomOrNull() ?: 0); container.playback.setShuffle(true)
                }, label = { Text("Shuffle") }, leadingIcon = { Icon(Icons.Rounded.Shuffle, null) })
                Text("${songs.size} songs · ${Formatters.duration(songs.sumOf { it.durationMs })}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, showArt, onOpen) { container.play(context, songs.map { it.toQueueItem() }, i) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(s: AudioItem, showArt: Boolean, onOpen: (String) -> Unit, onClick: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileOps = rememberMediaFileOps()
    val current by container.playback.state.collectAsStateWithLifecycle()
    val isCurrent = current.item?.uri == s.uri.toString()
    var menu by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true })
        .padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showArt) MediaArtwork(MediaThumb(s.uri, false, s.albumId), Modifier.size(48.dp), corner = 8.dp)
        else Text(if (s.trackNumber > 0) s.trackNumber.toString() else "–", Modifier.width(32.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
            Text("${s.artist} · ${Formatters.duration(s.durationMs)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${s.title}") }
            DropdownMenu(menu, { menu = false }) {
                val close = { menu = false }
                MenuEntry(close, "Play next") { container.playback.playNext(listOf(s.toQueueItem())) }
                MenuEntry(close, "Add to queue") { container.playback.addToQueue(listOf(s.toQueueItem())) }
                MenuEntry(close, "Add to playlist") { playlist = true }
                MenuEntry(close, "Add to favorites") {
                    scope.launch { container.database.favorites().add(Favorite(s.uri.toString(), s.title, s.artist, false, System.currentTimeMillis())) }
                    context.toast("Added to favorites")
                }
                MenuEntry(close, "Go to album") { onOpen(Routes.album(s.albumId)) }
                MenuEntry(close, "Go to artist") { onOpen(Routes.artist(s.albumArtist ?: s.artist)) }
                MenuEntry(close, "Share") { shareMedia(context, s.uri, s.mimeType) }
                MenuEntry(close, "Details") { details = true }
                MenuEntry(close, "Delete") { fileOps.delete(listOf(s.uri)) { ok -> if (ok) context.toast("Deleted") } }
            }
        }
    }
    if (playlist) AddToPlaylistDialog(listOf(s.toQueueItem())) { playlist = false }
    if (details) AudioDetailsDialog(s) { details = false }
}

@Composable
fun PlayAllIcon() = Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null)

internal fun Uri?.thumb(albumId: Long) = this?.let { MediaThumb(it, false, albumId) }
