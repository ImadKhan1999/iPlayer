package com.imadlabs.iplayer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.db.Favorite
import com.imadlabs.iplayer.data.db.PlaybackRecord
import com.imadlabs.iplayer.data.db.Playlist
import com.imadlabs.iplayer.playback.QueueItem
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.BackTopBar
import com.imadlabs.iplayer.ui.components.BrandProgress
import com.imadlabs.iplayer.ui.components.BrandTopBar
import com.imadlabs.iplayer.ui.components.ConfirmDialog
import com.imadlabs.iplayer.ui.components.EmptyState
import com.imadlabs.iplayer.ui.components.TextInputDialog
import com.imadlabs.iplayer.ui.components.play
import com.imadlabs.iplayer.ui.components.toQueueItem
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(onOpen: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(topBar = { BrandTopBar(onOpen, title = "Library") }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Playlists") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Favorites") })
                Tab(tab == 2, { tab = 2 }, text = { Text("History") })
            }
            when (tab) {
                0 -> Playlists(onOpen)
                1 -> Favorites()
                else -> History(onOpen)
            }
        }
    }
}

@Composable
private fun Playlists(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val dao = container.database.playlists()
    val playlistsFlow = remember { dao.playlists() }
    val countsFlow = remember { dao.counts() }
    val playlists by playlistsFlow.collectAsStateWithLifecycle(emptyList())
    val counts by countsFlow.collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.Rounded.Add, null); Text(" New playlist")
            }
        }
        if (playlists.isEmpty()) item {
            Text("Create a playlist here, or add songs and videos to one from their menus. You can also save the current queue as a playlist.",
                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(playlists, key = { it.id }) { p ->
            val count = counts.firstOrNull { it.playlistId == p.id }?.count ?: 0
            ListItem(
                headlineContent = { Text(p.name) },
                supportingContent = { Text(if (count == 1) "1 item" else "$count items") },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(Routes.playlist(p.id)) },
            )
        }
    }
    if (creating) TextInputDialog("New playlist", label = "Name", confirmLabel = "Create", onDismiss = { creating = false }) { name ->
        creating = false
        scope.launch {
            val now = System.currentTimeMillis()
            val id = dao.insert(Playlist(name = name, createdAt = now, updatedAt = now))
            onOpen(Routes.playlist(id))
        }
    }
}

@Composable
private fun Favorites() {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { container.database.favorites().all() }
    val favorites by flow.collectAsStateWithLifecycle(emptyList())
    if (favorites.isEmpty()) {
        EmptyState(Icons.Rounded.Favorite, "No favorites yet", "Mark songs and videos as favorites from their menus.")
        return
    }
    val queue = favorites.map { it.toQueueItem() }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(favorites, key = { _, f -> f.uri }) { i, f ->
            ListItem(
                headlineContent = { Text(f.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = f.subtitle?.let { { Text(it, maxLines = 1) } },
                leadingContent = { Icon(if (f.isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null) },
                trailingContent = {
                    IconButton(onClick = { scope.launch { container.database.favorites().remove(f.uri) } }) {
                        Icon(Icons.Rounded.Close, "Remove ${f.title} from favorites")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { container.play(context, queue, i) },
            )
        }
    }
}

private fun Favorite.toQueueItem() = QueueItem(uri, title, if (isVideo) null else subtitle, null, if (isVideo) null else uri, 0, isVideo)

@Composable
private fun History(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val flow = remember { container.database.playbackRecords().recent() }
    val records by flow.collectAsStateWithLifecycle(emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = { confirmClear = true }, enabled = records.isNotEmpty()) {
                    Icon(Icons.Rounded.DeleteSweep, null); Text(" Clear history")
                }
                TextButton(onClick = { onOpen(Routes.settings("privacy")) }) { Text("Privacy settings") }
            }
        }
        if (!settings.historyEnabled) item {
            Text("History is turned off. Resume positions and recently played items aren't being recorded.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (records.isEmpty()) item {
            EmptyState(Icons.Rounded.History, "Nothing played yet", "Items you play appear here with their resume position.")
        }
        items(records, key = { it.uri }) { r ->
            HistoryRow(r,
                onPlay = {
                    container.play(context, listOf(r.toQueueItem()), 0, positionMs = if (r.completed) 0 else r.positionMs)
                },
                onRemove = { scope.launch { container.database.playbackRecords().delete(r.uri) } })
        }
    }
    if (confirmClear) ConfirmDialog("Clear playback history?",
        "Resume positions, play counts and per-file subtitle and track choices will be removed. Your media files are not affected.",
        "Clear", { confirmClear = false }) {
        confirmClear = false
        scope.launch { container.database.playbackRecords().clear() }
    }
}

private fun PlaybackRecord.toQueueItem() = QueueItem(uri, title, null, null, if (isVideo) null else uri, durationMs, isVideo)

@Composable
private fun HistoryRow(r: PlaybackRecord, onPlay: () -> Unit, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                val status = when {
                    r.completed -> "Finished"
                    r.positionMs > 0 && r.durationMs > 0 -> "${Formatters.duration(r.positionMs)} of ${Formatters.duration(r.durationMs)}"
                    else -> Formatters.duration(r.durationMs)
                }
                Text("$status · played ${r.playCount}×", maxLines = 1)
                if (!r.completed && r.durationMs > 0 && r.positionMs > 0) {
                    BrandProgress(r.positionMs.toFloat() / r.durationMs, Modifier.padding(top = 6.dp))
                }
            }
        },
        leadingContent = { Icon(if (r.isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null) },
        trailingContent = { IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove from history") } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onPlay),
    )
}

@Composable
fun PlaylistScreen(id: Long, onBack: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = container.database.playlists()
    val playlistFlow = remember(id) { dao.playlist(id) }
    val entriesFlow = remember(id) { dao.entries(id) }
    val playlist by playlistFlow.collectAsStateWithLifecycle(null)
    val entries by entriesFlow.collectAsStateWithLifecycle(emptyList())
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    fun save(newOrder: List<com.imadlabs.iplayer.data.db.PlaylistEntry>) =
        scope.launch { dao.replaceEntries(id, newOrder, System.currentTimeMillis()) }

    Scaffold(topBar = {
        BackTopBar(playlist?.name ?: "Playlist", onBack, "${entries.size} items · ${Formatters.duration(entries.sumOf { it.durationMs })}") {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.KeyboardArrowDown, "Playlist options") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; deleting = true })
            }
        }
    }) { pad ->
        if (entries.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Rounded.PlaylistPlay, "This playlist is empty",
                "Add songs or videos from their menus with “Add to playlist”.", Modifier.padding(pad))
            return@Scaffold
        }
        val queue = entries.map { it.toQueueItem() }
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    AssistChip(onClick = { container.play(context, queue, 0) }, label = { Text("Play all") },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) })
                    AssistChip(onClick = {
                        container.playback.setShuffle(true)
                        container.play(context, queue, queue.indices.random())
                    }, label = { Text("Shuffle") }, leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
            itemsIndexed(entries, key = { _, e -> e.id }) { i, e ->
                ListItem(
                    headlineContent = { Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(listOfNotNull(e.artist, Formatters.duration(e.durationMs).takeIf { e.durationMs > 0 }).joinToString(" · ")) },
                    leadingContent = { Icon(if (e.isVideo) Icons.Rounded.Movie else Icons.Rounded.MusicNote, null) },
                    trailingContent = {
                        Row {
                            IconButton(enabled = i > 0, onClick = { save(entries.toMutableList().apply { add(i - 1, removeAt(i)) }) }) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                            }
                            IconButton(enabled = i < entries.lastIndex, onClick = { save(entries.toMutableList().apply { add(i + 1, removeAt(i)) }) }) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                            }
                            IconButton(onClick = { save(entries.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Rounded.Close, "Remove") }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { container.play(context, queue, i) },
                )
            }
        }
    }
    if (renaming) TextInputDialog("Rename playlist", playlist?.name.orEmpty(), "Name", onDismiss = { renaming = false }) { name ->
        renaming = false
        scope.launch { dao.rename(id, name, System.currentTimeMillis()) }
    }
    if (deleting) ConfirmDialog("Delete “${playlist?.name}”?", "Only the playlist is deleted. The songs and videos stay on your device.",
        "Delete", { deleting = false }) {
        deleting = false
        scope.launch { dao.delete(id) }
        onBack()
    }
}
