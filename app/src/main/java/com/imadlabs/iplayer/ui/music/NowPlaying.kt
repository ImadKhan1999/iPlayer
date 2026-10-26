package com.imadlabs.iplayer.ui.music

import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.media.LrcParser
import com.imadlabs.iplayer.data.media.Lyrics
import com.imadlabs.iplayer.data.media.LyricsLoader
import com.imadlabs.iplayer.playback.PlayerState
import com.imadlabs.iplayer.playback.PlayerStatus
import com.imadlabs.iplayer.playback.RepeatMode
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.MediaArtwork
import com.imadlabs.iplayer.ui.components.MediaThumb
import com.imadlabs.iplayer.ui.library.SaveQueueDialog
import com.imadlabs.iplayer.ui.theme.LocalImadExtras
import com.imadlabs.iplayer.ui.theme.MonoStyle
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.delay

val SPEED_PRESETS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val container = LocalContainer.current
    val s by container.playback.state.collectAsStateWithLifecycle()
    val item = s.item ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            val frac = if (s.durationMs > 0) s.positionMs.toFloat() / s.durationMs else 0f
            LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent, drawStopIndicator = {})
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaArtwork(MediaThumb(Uri.parse(item.uri), false), Modifier.size(44.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.error?.message ?: item.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        color = if (s.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = container.playback::previous) { Icon(Icons.Rounded.SkipPrevious, "Previous") }
                IconButton(onClick = container.playback::togglePlayPause) {
                    Icon(if (s.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (s.playWhenReady) "Pause" else "Play")
                }
                IconButton(onClick = container.playback::next) { Icon(Icons.Rounded.SkipNext, "Next") }
            }
        }
    }
}

@Composable
fun SeekBar(s: PlayerState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, light: Boolean = false) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = s.durationMs.coerceAtLeast(1)
    val value = dragging ?: (s.positionMs.toFloat() / dur).coerceIn(0f, 1f)
    Column(modifier) {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { onSeek((it * dur).toLong()) }; dragging = null },
            enabled = s.seekable && s.durationMs > 0,
            colors = if (light) SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)) else SliderDefaults.colors(),
            modifier = Modifier.semantics { contentDescription = "Seek bar" },
        )
        Row {
            val shown = dragging?.let { (it * dur).toLong() } ?: s.positionMs
            Text(Formatters.duration(shown), style = MonoStyle, color = if (light) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(if (s.durationMs > 0) Formatters.duration(s.durationMs) else "--:--", style = MonoStyle,
                color = if (light) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val pm = container.playback
    val s by pm.state.collectAsStateWithLifecycle()
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val item = s.item
    if (item == null) { LaunchedEffect(Unit) { onBack() }; return }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Soft brand glow behind the artwork
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), Color.Transparent))))
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val wide = maxWidth > maxHeight
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ExpandMore, "Close now playing") }
                    Text("Now playing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { onOpen(Routes.EQUALIZER) }) { Icon(Icons.Rounded.Equalizer, "Equalizer") }
                }
                val content: @Composable (Modifier) -> Unit = { m ->
                    Box(m, contentAlignment = Alignment.Center) {
                        if (showLyrics) LyricsView(s, Modifier.fillMaxSize())
                        else MediaArtwork(MediaThumb(Uri.parse(item.uri), false), Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f), corner = 20.dp)
                    }
                }
                val controls: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        Text(item.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(item.artist, item.album).joinToString(" · "), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        s.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(12.dp))
                        SeekBar(s, pm::seekTo)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { pm.setShuffle(!s.shuffle) }) {
                                Icon(Icons.Rounded.Shuffle, if (s.shuffle) "Shuffle on" else "Shuffle off",
                                    tint = if (s.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = pm::previous, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                            FilledIconButton(onClick = pm::togglePlayPause, Modifier.size(76.dp), shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                if (s.status == PlayerStatus.Opening || s.status == PlayerStatus.Buffering)
                                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary)
                                else Icon(if (s.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (s.playWhenReady) "Pause" else "Play", Modifier.size(40.dp))
                            }
                            IconButton(onClick = pm::next, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp)) }
                            IconButton(onClick = pm::cycleRepeat) {
                                Icon(if (s.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    "Repeat: ${s.repeat.name}",
                                    tint = if (s.repeat != RepeatMode.Off) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = { showSpeed = true }) { Icon(Icons.Rounded.Speed, null); Spacer(Modifier.width(4.dp)); Text(Formatters.speed(s.speed)) }
                            TextButton(onClick = { showSleep = true }) {
                                Icon(Icons.Rounded.Bedtime, null); Spacer(Modifier.width(4.dp))
                                Text(sleepLabel(s))
                            }
                            IconButton(onClick = { showLyrics = !showLyrics }) {
                                Icon(Icons.Rounded.Lyrics, "Lyrics", tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue") }
                        }
                    }
                }
                if (wide) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    content(Modifier.weight(1f).fillMaxHeight())
                    controls(Modifier.weight(1f))
                } else {
                    content(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp))
                    controls(Modifier.fillMaxWidth().padding(bottom = 16.dp))
                }
            }
        }
    }
    if (showQueue) QueueSheet { showQueue = false }
    if (showSpeed) SpeedDialog(s.speed, onDismiss = { showSpeed = false }) { pm.setSpeed(it); showSpeed = false }
    if (showSleep) SleepDialog(onDismiss = { showSleep = false }, onEndOfItem = { pm.setSleepAtEndOfItem(); showSleep = false }) {
        pm.setSleepTimer(it); showSleep = false
    }
}

@Composable
fun sleepLabel(s: PlayerState): String {
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(s.sleepAtElapsed) { while (s.sleepAtElapsed != null) { now = SystemClock.elapsedRealtime(); delay(1000) } }
    return when {
        s.sleepAtEndOfItem -> "End of track"
        s.sleepAtElapsed != null -> Formatters.duration((s.sleepAtElapsed - now).coerceAtLeast(0))
        else -> "Sleep"
    }
}

@Composable
private fun LyricsView(s: PlayerState, modifier: Modifier) {
    val context = LocalContext.current
    val pm = LocalContainer.current.playback
    val item = s.item ?: return
    var result by remember(item.uri) { mutableStateOf<LyricsLoader.Result?>(null) }
    LaunchedEffect(item.uri) { result = LyricsLoader.load(context, Uri.parse(item.uri)) }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp)) {
        when (val r = result) {
            null -> Text("Looking for lyrics…", Modifier.align(Alignment.Center))
            LyricsLoader.Result.NotFound -> Text("No lyrics found. Put a .lrc or .txt file with the same name next to the song.",
                Modifier.align(Alignment.Center), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LyricsLoader.Result.NoAccess -> Text("Lyrics files next to this song can't be read. Grant all-files access in Settings › Library to enable sidecar lyrics.",
                Modifier.align(Alignment.Center), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is LyricsLoader.Result.Found -> when (val l = r.lyrics) {
                is Lyrics.Plain -> LazyColumn { item { Text(l.text, style = MaterialTheme.typography.bodyLarge) } }
                is Lyrics.Synced -> {
                    val active = LrcParser.activeIndex(l.lines, s.positionMs)
                    val listState = rememberLazyListState()
                    LaunchedEffect(active) { if (active > 2) listState.animateScrollToItem(active - 2) }
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(l.lines) { i, line ->
                            val alpha by animateFloatAsState(if (i == active) 1f else 0.45f, label = "lyric")
                            Text(line.text.ifBlank { "♪" }, style = MaterialTheme.typography.titleMedium,
                                color = (if (i == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
                                modifier = Modifier.fillMaxWidth().clickable { pm.seekTo(line.timeMs) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val pm = LocalContainer.current.playback
    val s by pm.state.collectAsStateWithLifecycle()
    var save by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Queue · ${s.queue.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { save = true }, enabled = s.queue.isNotEmpty()) { Text("Save as playlist") }
            TextButton(onClick = { pm.clearQueue(); onDismiss() }) { Text("Clear") }
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (s.index - 2).coerceAtLeast(0))
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(s.queue, key = { _, q -> q.key }) { i, q ->
                val current = i == s.index
                ListItem(
                    headlineContent = { Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    supportingContent = { Text(listOfNotNull(q.artist, Formatters.duration(q.durationMs).takeIf { q.durationMs > 0 }).joinToString(" · "), maxLines = 1) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { pm.moveInQueue(i, i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { pm.moveInQueue(i, i + 1) }, enabled = i < s.queue.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { pm.removeFromQueue(i) }) { Icon(Icons.Rounded.Close, "Remove from queue") }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = if (current) LocalImadExtras.current.cardElevated else Color.Transparent),
                    modifier = Modifier.clickable { pm.skipTo(i) },
                )
            }
        }
    }
    if (save) SaveQueueDialog(s.queue) { save = false }
}

@Composable
fun SpeedDialog(current: Float, onDismiss: () -> Unit, onPick: (Float) -> Unit) {
    var custom by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                Text(Formatters.speed(custom), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Slider(custom, { custom = (Math.round(it * 20) / 20f) }, valueRange = 0.25f..4f, steps = 74)
                Text("Pitch is preserved. Speeds above 3× may skip audio on some files.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SPEED_PRESETS.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { p ->
                            TextButton(onClick = { onPick(p) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Text(Formatters.speed(p), color = if (p == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(custom) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SleepDialog(onDismiss: () -> Unit, onEndOfItem: () -> Unit, onPick: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(5, 15, 30, 45, 60, 90).forEach { m ->
                    Text("$m minutes", Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 12.dp))
                }
                Text("End of current item", Modifier.fillMaxWidth().clickable(onClick = onEndOfItem).padding(vertical = 12.dp))
                Text("Off", Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
