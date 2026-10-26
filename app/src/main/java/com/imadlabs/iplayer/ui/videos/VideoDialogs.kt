package com.imadlabs.iplayer.ui.videos

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.db.ClassificationOverride
import com.imadlabs.iplayer.data.db.PlaybackRecord
import com.imadlabs.iplayer.data.media.EpisodeParser
import com.imadlabs.iplayer.data.media.VideoItem
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.components.BackTopBar
import com.imadlabs.iplayer.ui.components.Badge
import com.imadlabs.iplayer.ui.components.EmptyState
import com.imadlabs.iplayer.ui.components.ListRowText
import com.imadlabs.iplayer.ui.components.MediaArtwork
import com.imadlabs.iplayer.ui.components.MediaFileOps
import com.imadlabs.iplayer.ui.components.MediaThumb
import com.imadlabs.iplayer.ui.components.SectionHeader
import com.imadlabs.iplayer.ui.components.play
import com.imadlabs.iplayer.ui.components.toQueueItem
import com.imadlabs.iplayer.ui.components.toast
import com.imadlabs.iplayer.ui.theme.MonoStyle
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv

@Composable
fun VideoDetailsDialog(v: VideoItem, record: PlaybackRecord?, onDismiss: () -> Unit) {
    val rows = buildList {
        add("File" to v.displayName)
        v.path?.let { add("Location" to it.substringBeforeLast('/')) }
        add("Size" to Formatters.fileSize(v.sizeBytes))
        if (v.durationMs > 0) add("Duration" to Formatters.duration(v.durationMs))
        if (v.width > 0) add("Resolution" to "${v.width} × ${v.height}")
        v.mimeType?.let { add("Type" to it) }
        add("Added" to DateFormat.getDateTimeInstance().format(Date(v.dateAddedSec * 1000)))
        record?.let {
            add("Last played" to DateFormat.getDateTimeInstance().format(Date(it.lastPlayedAt)))
            add("Play count" to it.playCount.toString())
            if (!it.completed && it.positionMs > 0) add("Resume at" to Formatters.duration(it.positionMs))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (k, value) ->
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MonoStyle)
                    }
                }
                Text("Codec details are shown in the player (Info) once the file is opened.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun RenameDialog(v: VideoItem, ops: MediaFileOps, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ext = v.displayName.substringAfterLast('.', "")
    var name by remember { mutableStateOf(v.displayName.substringBeforeLast('.')) }
    val invalid = name.isBlank() || name.any { it in "/\\:*?\"<>|" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("File name") },
                suffix = { if (ext.isNotEmpty()) Text(".$ext") }, isError = invalid)
        },
        confirmButton = {
            TextButton(enabled = !invalid, onClick = {
                val newName = if (ext.isNotEmpty()) "${name.trim()}.$ext" else name.trim()
                ops.rename(v.uri, newName) { ok -> if (ok) context.toast("Renamed") }
                onDismiss()
            }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lets the user correct (or remove) the automatic TV-episode classification of a video. */
@Composable
fun ClassifyDialog(v: VideoItem, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val detected = remember(v) { EpisodeParser.parse(v.displayName, v.folder) }
    var isEpisode by remember { mutableStateOf(detected != null) }
    var show by remember { mutableStateOf(detected?.show ?: "") }
    var season by remember { mutableStateOf(detected?.season?.toString() ?: "1") }
    var episode by remember { mutableStateOf(detected?.episode?.toString() ?: "1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TV show info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (detected != null) "Detected: ${detected.show} S${detected.season}E${detected.episode}" else "Not detected as an episode.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isEpisode = !isEpisode }) {
                    Checkbox(isEpisode, { isEpisode = it }); Text("This is a TV episode")
                }
                if (isEpisode) {
                    OutlinedTextField(show, { show = it }, label = { Text("Show") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(season, { season = it.filter(Char::isDigit).take(2) }, label = { Text("Season") }, singleLine = true,
                            modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(episode, { episode = it.filter(Char::isDigit).take(3) }, label = { Text("Episode") }, singleLine = true,
                            modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isEpisode || (show.isNotBlank() && season.isNotEmpty() && episode.isNotEmpty()), onClick = {
                scope.launch {
                    container.library.setOverride(ClassificationOverride(v.uri.toString(), isEpisode,
                        show.trim().takeIf { isEpisode }, season.toIntOrNull(), episode.toIntOrNull()))
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { scope.launch { container.library.clearOverride(v.uri.toString()) }; onDismiss() }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun ShowScreen(name: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val shows by container.library.shows.collectAsStateWithLifecycle()
    val recordsFlow = remember { container.database.playbackRecords().all() }
    val records by recordsFlow.collectAsStateWithLifecycle(emptyList())
    val byUri = remember(records) { records.associateBy { it.uri } }
    val show = shows.firstOrNull { it.name == name }
    Scaffold(topBar = { BackTopBar(name, onBack, show?.let { "${it.seasons.size} season(s) · ${it.episodeCount} episodes" }) }) { pad ->
        if (show == null) { EmptyState(Icons.Rounded.Tv, "Show not found", "It may have been renamed or removed.", Modifier.padding(pad)); return@Scaffold }
        val ordered = show.seasons.values.flatten().map { it.second }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp)) {
            show.seasons.forEach { (season, eps) ->
                item(key = "s$season") { SectionHeader("Season $season") }
                items(eps, key = { it.second.id }) { (ep, v) ->
                    val r = byUri[v.uri.toString()]
                    Row(Modifier.fillMaxWidth().clickable {
                        container.play(context, ordered.map { it.toQueueItem() }, ordered.indexOf(v))
                    }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(120.dp).aspectRatio(16 / 9f).clip(RoundedCornerShape(8.dp))) {
                            MediaArtwork(MediaThumb(v.uri, true), Modifier.fillMaxSize(), corner = 8.dp)
                            if (r?.completed == true) Badge("WATCHED", Modifier.align(Alignment.TopEnd).padding(4.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        ListRowText("E${ep.episode} · ${v.title}", Formatters.duration(v.durationMs), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
