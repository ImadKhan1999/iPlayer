package com.imadlabs.iplayer.ui.videos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.db.PlaybackRecord
import com.imadlabs.iplayer.data.media.VideoItem
import com.imadlabs.iplayer.data.settings.LibraryLayout
import com.imadlabs.iplayer.data.settings.SortOrder
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.MediaAccess
import com.imadlabs.iplayer.ui.MediaPermissions
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.Badge
import com.imadlabs.iplayer.ui.components.BrandProgress
import com.imadlabs.iplayer.ui.components.BrandTopBar
import com.imadlabs.iplayer.ui.components.EmptyState
import com.imadlabs.iplayer.ui.components.ListRowText
import com.imadlabs.iplayer.ui.components.MediaArtwork
import com.imadlabs.iplayer.ui.components.MediaThumb
import com.imadlabs.iplayer.ui.components.SectionHeader
import com.imadlabs.iplayer.ui.components.play
import com.imadlabs.iplayer.ui.components.rememberMediaFileOps
import com.imadlabs.iplayer.ui.components.toQueueItem
import com.imadlabs.iplayer.ui.components.toast
import com.imadlabs.iplayer.ui.library.AddToPlaylistDialog
import com.imadlabs.iplayer.ui.rememberMediaPermissionRequest
import com.imadlabs.iplayer.ui.theme.LocalImadExtras
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.launch

enum class VideoFilter(val label: String) { All("All"), Unwatched("Unwatched"), InProgress("In progress"), Favorites("Favorites"), Duplicates("Duplicates") }

fun List<VideoItem>.sortedBy(order: SortOrder): List<VideoItem> = when (order) {
    SortOrder.DateAddedDesc -> sortedByDescending { it.dateAddedSec }
    SortOrder.DateAddedAsc -> sortedBy { it.dateAddedSec }
    SortOrder.NameAsc -> sortedBy { it.title.lowercase() }
    SortOrder.NameDesc -> sortedByDescending { it.title.lowercase() }
    SortOrder.DurationDesc -> sortedByDescending { it.durationMs }
    SortOrder.DurationAsc -> sortedBy { it.durationMs }
    SortOrder.SizeDesc -> sortedByDescending { it.sizeBytes }
    SortOrder.SizeAsc -> sortedBy { it.sizeBytes }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val access by MediaPermissions.access.collectAsStateWithLifecycle()
    val library by container.library.library.collectAsStateWithLifecycle()
    val shows by container.library.shows.collectAsStateWithLifecycle()
    val duplicates by container.library.duplicateNames.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val recordsFlow = remember { container.database.playbackRecords().all() }
    val records by recordsFlow.collectAsStateWithLifecycle(emptyList())
    val favFlow = remember { container.database.favorites().all() }
    val favorites by favFlow.collectAsStateWithLifecycle(emptyList())
    val request = rememberMediaPermissionRequest()
    val fileOps = rememberMediaFileOps()

    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(VideoFilter.All) }
    var selection by remember { mutableStateOf(setOf<Long>()) }
    var sortMenu by remember { mutableStateOf(false) }
    var playlistItems by remember { mutableStateOf<List<VideoItem>?>(null) }
    var detailsFor by remember { mutableStateOf<VideoItem?>(null) }
    var renameFor by remember { mutableStateOf<VideoItem?>(null) }
    var classifyFor by remember { mutableStateOf<VideoItem?>(null) }

    val recordByUri = remember(records) { records.associateBy { it.uri } }
    val favUris = remember(favorites) { favorites.map { it.uri }.toSet() }
    val visible = remember(library, query, filter, settings.videoSort, recordByUri, favUris, duplicates) {
        library.videos.asSequence()
            .filter { query.isBlank() || it.title.contains(query, true) || it.folderName.contains(query, true) }
            .filter {
                val r = recordByUri[it.uri.toString()]
                when (filter) {
                    VideoFilter.All -> true
                    VideoFilter.Unwatched -> r == null || (!r.completed && r.positionMs < 5000)
                    VideoFilter.InProgress -> r != null && !r.completed && r.positionMs >= 5000
                    VideoFilter.Favorites -> it.uri.toString() in favUris
                    VideoFilter.Duplicates -> it.displayName.lowercase() in duplicates
                }
            }.toList().sortedBy(settings.videoSort)
    }
    val selectedItems = visible.filter { it.id in selection }

    fun playFrom(v: VideoItem) {
        val list = visible.map { it.toQueueItem() }
        container.play(context, list, visible.indexOf(v))
    }

    Scaffold(topBar = {
        when {
            selection.isNotEmpty() -> TopAppBar(
                title = { Text("${selection.size} selected") },
                navigationIcon = { IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Rounded.Close, "Clear selection") } },
                actions = {
                    IconButton(onClick = { selection = visible.map { it.id }.toSet() }) { Icon(Icons.Rounded.SelectAll, "Select all") }
                    IconButton(onClick = { container.play(context, selectedItems.map { it.toQueueItem() }); selection = emptySet() }) {
                        Icon(Icons.Rounded.PlayArrow, "Play selected")
                    }
                    IconButton(onClick = { container.playback.addToQueue(selectedItems.map { it.toQueueItem() }); selection = emptySet() }) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue")
                    }
                    IconButton(onClick = { playlistItems = selectedItems }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") }
                    IconButton(onClick = {
                        fileOps.delete(selectedItems.map { it.uri }) { ok -> if (ok) { context.toast("Deleted"); selection = emptySet() } }
                    }) { Icon(Icons.Rounded.Delete, "Delete") }
                },
            )
            else -> BrandTopBar(onOpen, title = "Videos") {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Rounded.Search, "Search") }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        SortOrder.entries.forEach { o ->
                            DropdownMenuItem(text = { Text(o.label) }, onClick = {
                                sortMenu = false; scope.launch { container.settings.update { it.copy(videoSort = o) } }
                            }, trailingIcon = { if (o == settings.videoSort) Icon(Icons.Rounded.CheckCircle, null) })
                        }
                    }
                }
                IconButton(onClick = {
                    scope.launch { container.settings.update { it.copy(videoLayout = if (it.videoLayout == LibraryLayout.Grid) LibraryLayout.List else LibraryLayout.Grid) } }
                }) {
                    Icon(if (settings.videoLayout == LibraryLayout.Grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        if (settings.videoLayout == LibraryLayout.Grid) "List view" else "Grid view")
                }
            }
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            if (access == MediaAccess.None) {
                EmptyState(Icons.Rounded.Movie, "Your videos will appear here",
                    "Allow iPlayer to read videos on this device to build your library.", actionLabel = "Allow access", onAction = request)
                return@Column
            }
            if (searching) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search videos and folders") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) })
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VideoFilter.entries.toList()) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            if (library.loaded && visible.isEmpty()) {
                EmptyState(Icons.Rounded.Movie,
                    if (library.videos.isEmpty()) "No videos yet" else "Nothing matches",
                    if (library.videos.isEmpty()) "Videos saved on this device will show up here automatically."
                    else "Try another search or filter.")
                return@Column
            }
            val grid = settings.videoLayout == LibraryLayout.Grid
            LazyVerticalGrid(
                columns = if (grid) GridCells.Adaptive(168.dp) else GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (grid) 16.dp else 4.dp),
            ) {
                if (settings.groupEpisodes && shows.isNotEmpty() && filter == VideoFilter.All && query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("TV shows", Modifier.padding(horizontal = 0.dp)) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(shows, key = { it.name }) { s ->
                                FilterChip(selected = false, onClick = { onOpen(Routes.show(s.name)) },
                                    label = { Text("${s.name} · ${s.episodeCount}") })
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("All videos", Modifier.padding(horizontal = 0.dp)) }
                }
                items(visible, key = { it.id }) { v ->
                    val record = recordByUri[v.uri.toString()]
                    val selected = v.id in selection
                    val onClick = { if (selection.isNotEmpty()) selection = if (selected) selection - v.id else selection + v.id else playFrom(v) }
                    val onLong = { selection = selection + v.id }
                    val menu: @Composable () -> Unit = {
                        VideoMenu(v, record, v.uri.toString() in favUris,
                            onAddToPlaylist = { playlistItems = listOf(v) }, onDetails = { detailsFor = v },
                            onRename = { renameFor = v }, onClassify = { classifyFor = v },
                            onDelete = { fileOps.delete(listOf(v.uri)) { ok -> if (ok) context.toast("Deleted") } })
                    }
                    if (grid) VideoGridItem(v, record, selected, settings.showThumbnails, onClick, onLong, menu)
                    else VideoListItem(v, record, selected, settings.showThumbnails, onClick, onLong, menu)
                }
            }
        }
    }
    playlistItems?.let { items -> AddToPlaylistDialog(items.map { it.toQueueItem() }) { playlistItems = null; selection = emptySet() } }
    detailsFor?.let { v -> VideoDetailsDialog(v, recordByUri[v.uri.toString()]) { detailsFor = null } }
    renameFor?.let { v -> RenameDialog(v, fileOps) { renameFor = null } }
    classifyFor?.let { v -> ClassifyDialog(v) { classifyFor = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridItem(v: VideoItem, record: PlaybackRecord?, selected: Boolean, thumbs: Boolean,
                  onClick: () -> Unit, onLongClick: () -> Unit, menu: @Composable () -> Unit) {
    val accent = LocalImadExtras.current.accent
    Column(Modifier.clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp)) else Modifier)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f)) {
            MediaArtwork(MediaThumb(v.uri, true), Modifier.fillMaxSize(), showThumbnail = thumbs)
            Badge(Formatters.duration(v.durationMs), Modifier.align(Alignment.BottomEnd).padding(6.dp))
            v.resolutionLabel?.let { Badge(it, Modifier.align(Alignment.TopStart).padding(6.dp)) }
            if (record?.completed == true) Badge("WATCHED", Modifier.align(Alignment.TopEnd).padding(6.dp), background = accent, color = Color.Black)
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = accent,
                modifier = Modifier.align(Alignment.Center).size(36.dp).background(Color(0x99000000), CircleShape))
        }
        if (record != null && !record.completed && record.durationMs > 0 && record.positionMs > 0)
            BrandProgress(record.positionMs.toFloat() / record.durationMs, Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(v.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = 6.dp, start = 2.dp))
            menu()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(v: VideoItem, record: PlaybackRecord?, selected: Boolean, thumbs: Boolean,
                  onClick: () -> Unit, onLongClick: () -> Unit, menu: @Composable () -> Unit) {
    val accent = LocalImadExtras.current.accent
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else Modifier)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(128.dp).aspectRatio(16 / 9f)) {
            MediaArtwork(MediaThumb(v.uri, true), Modifier.fillMaxSize(), corner = 8.dp, showThumbnail = thumbs)
            Badge(Formatters.duration(v.durationMs), Modifier.align(Alignment.BottomEnd).padding(4.dp))
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = accent, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ListRowText(v.title, listOfNotNull(v.folderName, v.resolutionLabel, Formatters.fileSize(v.sizeBytes)).joinToString(" · "))
            if (record != null && !record.completed && record.durationMs > 0 && record.positionMs > 0) {
                Spacer(Modifier.height(6.dp))
                BrandProgress(record.positionMs.toFloat() / record.durationMs, Modifier.width(120.dp))
            }
        }
        menu()
    }
}

@Composable
private fun VideoMenu(
    v: VideoItem, record: PlaybackRecord?, favorite: Boolean,
    onAddToPlaylist: () -> Unit, onDetails: () -> Unit, onRename: () -> Unit, onClassify: () -> Unit, onDelete: () -> Unit,
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${v.title}") }
        DropdownMenu(open, { open = false }) {
            val close = { open = false }
            MenuEntry(close, "Play from start") { container.play(context, listOf(v.toQueueItem()), 0, 0) }
            MenuEntry(close, "Play next") { container.playback.playNext(listOf(v.toQueueItem())) }
            MenuEntry(close, "Add to queue") { container.playback.addToQueue(listOf(v.toQueueItem())) }
            MenuEntry(close, "Add to playlist", onAddToPlaylist)
            MenuEntry(close, if (favorite) "Remove from favorites" else "Add to favorites") {
                scope.launch {
                    if (favorite) container.database.favorites().remove(v.uri.toString())
                    else container.database.favorites().add(com.imadlabs.iplayer.data.db.Favorite(v.uri.toString(), v.title, v.folderName, true, System.currentTimeMillis()))
                }
            }
            if (record?.completed == true) MenuEntry(close, "Mark as unwatched") { scope.launch { container.database.playbackRecords().markUnwatched(v.uri.toString()) } }
            else MenuEntry(close, "Mark as watched") {
                scope.launch {
                    val dao = container.database.playbackRecords()
                    if (dao.get(v.uri.toString()) == null) dao.upsert(PlaybackRecord(v.uri.toString(), v.title, true, 0, v.durationMs, System.currentTimeMillis(), 0, true))
                    else dao.markWatched(v.uri.toString())
                }
            }
            MenuEntry(close, "TV show info…", onClassify)
            MenuEntry(close, "Share") { com.imadlabs.iplayer.ui.components.shareMedia(context, v.uri, v.mimeType) }
            MenuEntry(close, "Rename…", onRename)
            MenuEntry(close, "Hide this folder") { scope.launch { container.library.exclude(v.folder); context.toast("Folder hidden from library") } }
            MenuEntry(close, "Details", onDetails)
            MenuEntry(close, "Delete", onDelete)
        }
    }
}

@Composable
fun MenuEntry(close: () -> Unit, label: String, action: () -> Unit) =
    DropdownMenuItem(text = { Text(label) }, onClick = { close(); action() })
