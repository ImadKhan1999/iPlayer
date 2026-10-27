package com.imadlabs.iplayer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.db.Playlist
import com.imadlabs.iplayer.playback.QueueItem
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.components.TextInputDialog
import com.imadlabs.iplayer.ui.components.toPlaylistEntry
import com.imadlabs.iplayer.ui.components.toast
import kotlinx.coroutines.launch

/** Picks an existing playlist or creates a new one, then appends [items]. */
@Composable
fun AddToPlaylistDialog(items: List<QueueItem>, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { container.database.playlists().playlists() }
    val playlists by flow.collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }

    fun addTo(p: Playlist) = scope.launch {
        container.database.playlists().append(p.id, items.map { it.toPlaylistEntry() }, System.currentTimeMillis())
        context.toast("Added ${items.size} to ${p.name}")
        onDismiss()
    }

    if (creating) {
        TextInputDialog("New playlist", label = "Name", confirmLabel = "Create", onDismiss = { creating = false }) { name ->
            scope.launch {
                val now = System.currentTimeMillis()
                val id = container.database.playlists().insert(Playlist(name = name, createdAt = now, updatedAt = now))
                container.database.playlists().append(id, items.map { it.toPlaylistEntry() }, now)
                context.toast("Created $name")
                onDismiss()
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                ListItem(headlineContent = { Text("New playlist") }, leadingContent = { Icon(Icons.Rounded.Add, null) },
                    modifier = Modifier.clickable { creating = true })
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        ListItem(headlineContent = { Text(p.name) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                            modifier = Modifier.clickable { addTo(p) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Saves the current play queue as a new playlist. */
@Composable
fun SaveQueueDialog(items: List<QueueItem>, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    TextInputDialog("Save queue as playlist", label = "Name", confirmLabel = "Save", onDismiss = onDismiss) { name ->
        scope.launch {
            val now = System.currentTimeMillis()
            val id = container.database.playlists().insert(Playlist(name = name, createdAt = now, updatedAt = now))
            container.database.playlists().replaceEntries(id, items.map { it.toPlaylistEntry() }, now)
            context.toast("Saved $name")
            onDismiss()
        }
    }
}
