package com.imadlabs.iplayer.ui.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.media.MediaFormats
import com.imadlabs.iplayer.data.settings.DecoderMode
import com.imadlabs.iplayer.playback.MediaInfo
import com.imadlabs.iplayer.playback.PlaybackError
import com.imadlabs.iplayer.playback.RepeatMode
import com.imadlabs.iplayer.playback.TrackOption
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.components.toast
import com.imadlabs.iplayer.ui.music.SleepDialog
import com.imadlabs.iplayer.ui.music.SpeedDialog
import com.imadlabs.iplayer.ui.music.sleepLabel
import com.imadlabs.iplayer.ui.theme.MonoStyle
import com.imadlabs.iplayer.util.Diagnostics
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheetFrame(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun SheetHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun TrackList(tracks: List<TrackOption>, selected: Int, empty: String, onSelect: (Int) -> Unit) {
    if (tracks.isEmpty()) Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
    tracks.forEach { t ->
        Row(Modifier.fillMaxWidth().selectable(t.id == selected, role = Role.RadioButton) { onSelect(t.id) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(t.id == selected, null)
            Text(t.name, Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun DelayRow(label: String, valueMs: Long, step: Long, onChange: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange(valueMs - step) }) { Text("−${step}ms") }
        Text(Formatters.delay(valueMs), style = MonoStyle, modifier = Modifier.widthIn(min = 64.dp))
        TextButton(onClick = { onChange(valueMs + step) }) { Text("+${step}ms") }
        if (valueMs != 0L) TextButton(onClick = { onChange(0) }) { Text("Reset") }
    }
}

@Composable
fun TracksSheet(onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val pm = container.playback
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by pm.state.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Copy into app storage so the engine can read it regardless of the provider.
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "subtitle.srt"
                    if (!MediaFormats.isSubtitle(name)) error("Not a subtitle file: $name")
                    val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
                    if (size > 20L * 1024 * 1024) error("Subtitle file too large")
                    val dir = File(context.filesDir, "subtitles").apply { mkdirs() }
                    val out = File(dir, "${System.currentTimeMillis()}_${name.replace(Regex("[^\\w.\\-]"), "_")}")
                    context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                    out
                }.onFailure { Diagnostics.log("Subtitles", "Couldn't import subtitle", it) }.getOrNull()
            }
            if (file == null) context.toast("Choose a subtitle file (.srt, .ass, .ssa, .vtt, .sub)")
            else pm.addSubtitle(Uri.fromFile(file))
        }
    }
    PlayerSheetFrame("Audio & subtitles", onDismiss) {
        SheetHeader("Audio track")
        TrackList(s.audioTracks, s.selectedAudioTrack, "No audio tracks", pm::selectAudioTrack)
        DelayRow("Audio delay", s.audioDelayMs, 50, pm::setAudioDelay)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SheetHeader("Subtitles")
        TrackList(s.subtitleTracks, s.selectedSubtitleTrack, "No subtitles in this file", pm::selectSubtitleTrack)
        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.Add, null); Text(" Load subtitle file")
        }
        DelayRow("Subtitle delay", s.subtitleDelayMs, 100, pm::setSubtitleDelay)
        Text("Positive delay shows subtitles later. Subtitle delay is remembered for this file.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoSheet(adjust: VideoAdjust, textureOutput: Boolean, onChange: (VideoAdjust) -> Unit, onDismiss: () -> Unit) {
    val pm = LocalContainer.current.playback
    val s by pm.state.collectAsStateWithLifecycle()
    PlayerSheetFrame("Picture", onDismiss) {
        SheetHeader("Aspect ratio")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.imadlabs.iplayer.data.settings.AspectMode.entries.forEach { m ->
                FilterChip(s.aspect == m, { pm.setAspect(m) }, label = { Text(m.label) })
            }
        }
        if (!textureOutput) {
            Text("Zoom, rotation, mirroring and colour adjustments need the TextureView output (Settings › Video › Rendering).",
                Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@PlayerSheetFrame
        }
        SheetHeader("Transform")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip({ onChange(adjust.copy(rotation = (adjust.rotation + 90) % 360)) }, label = { Text("Rotate ${adjust.rotation}°") })
            FilterChip(adjust.mirror, { onChange(adjust.copy(mirror = !adjust.mirror)) }, label = { Text("Mirror") })
            FilterChip(adjust.flip, { onChange(adjust.copy(flip = !adjust.flip)) }, label = { Text("Flip vertical") })
            AssistChip({ onChange(adjust.copy(zoom = 1f, panX = 0f, panY = 0f)) }, label = { Text("Reset zoom (${(adjust.zoom * 100).toInt()}%)") })
        }
        SheetHeader("Colour")
        LabeledSlider("Brightness", adjust.brightness, -0.5f..0.5f, "%+.0f%%".format(adjust.brightness * 100)) { onChange(adjust.copy(brightness = it)) }
        LabeledSlider("Contrast", adjust.contrast, 0.5f..2f, "%.0f%%".format(adjust.contrast * 100)) { onChange(adjust.copy(contrast = it)) }
        LabeledSlider("Saturation", adjust.saturation, 0f..2f, "%.0f%%".format(adjust.saturation * 100)) { onChange(adjust.copy(saturation = it)) }
        TextButton(onClick = { onChange(adjust.copy(brightness = 0f, contrast = 1f, saturation = 1f)) }) { Text("Reset colour") }
        Text("Adjustments apply to this file only. Colour filters are applied by the GPU on the rendered frame and don't change the file.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp))
        Slider(value, onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(display, style = MonoStyle, modifier = Modifier.widthIn(min = 52.dp).padding(start = 8.dp))
    }
}

@Composable
fun InfoSheet(onDismiss: () -> Unit) {
    val pm = LocalContainer.current.playback
    val info: MediaInfo? = remember { runCatching { pm.mediaInfo() }.getOrNull() }
    PlayerSheetFrame("Media info", onDismiss) {
        if (info == null) { Text("Information isn't available until the file has opened."); return@PlayerSheetFrame }
        info.sections.forEach { section ->
            SheetHeader(section.title)
            section.rows.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(k, Modifier.width(130.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, style = MonoStyle)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreSheet(onOpen: (PlayerSheet) -> Unit, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val pm = container.playback
    val s by pm.state.collectAsStateWithLifecycle()
    var speedDialog by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    PlayerSheetFrame("Playback", onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip({ speedDialog = true }, label = { Text("Speed ${Formatters.speed(s.speed)}") })
            AssistChip({ sleepDialog = true }, label = { Text("Sleep: ${sleepLabel(s)}") })
            AssistChip({ pm.cycleRepeat() }, label = {
                Text(when (s.repeat) { RepeatMode.Off -> "Repeat off"; RepeatMode.All -> "Repeat all"; RepeatMode.One -> "Repeat one" })
            })
            AssistChip({ onOpen(PlayerSheet.Video) }, label = { Text("Picture") })
            AssistChip({ onOpen(PlayerSheet.Info) }, label = { Text("Media info") })
        }
        SheetHeader("A–B repeat")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = pm::setAbPoint) {
                Text(when { s.abStartMs == null -> "Set A"; s.abEndMs == null -> "Set B"; else -> "Clear" })
            }
            Spacer(Modifier.width(12.dp))
            Text(listOfNotNull(s.abStartMs?.let { "A ${Formatters.duration(it)}" }, s.abEndMs?.let { "B ${Formatters.duration(it)}" })
                .joinToString("  →  ").ifEmpty { "Loop a section of this file" }, style = MonoStyle)
        }
        SheetHeader("Frame by frame")
        Row {
            OutlinedButton(onClick = { pm.frameStep(false) }) { Text("◀ Frame") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { pm.frameStep(true) }) { Text("Frame ▶") }
        }
        Text("Pauses playback and steps by one frame (${if (s.frameRate > 0) "%.3f fps".format(s.frameRate) else "frame rate unknown, 30 fps assumed"}).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.chapters.isNotEmpty()) {
            SheetHeader("Chapters")
            s.chapters.forEach { c ->
                Text("${Formatters.duration(c.startMs)}  ${c.name}", Modifier.fillMaxWidth().clickable { pm.seekTo(c.startMs); onDismiss() }
                    .padding(vertical = 8.dp))
            }
        }
        SheetHeader("Decoder")
        DecoderMode.entries.forEach { m ->
            Row(Modifier.fillMaxWidth().selectable(s.decoder == m, role = Role.RadioButton) { if (s.decoder != m) pm.setDecoderAndReload(m) }
                .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(s.decoder == m, null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(m.label)
                    Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (speedDialog) SpeedDialog(s.speed, { speedDialog = false }) { pm.setSpeed(it); speedDialog = false }
    if (sleepDialog) SleepDialog({ sleepDialog = false }, { pm.setSleepAtEndOfItem(); sleepDialog = false }) { pm.setSleepTimer(it); sleepDialog = false }
}

/** Frame preview while seeking, from Android's extractor (local files only; throttled). */
@Composable
fun SeekPreview(uri: String?, targetMs: Long, enabled: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val local = enabled && uri != null && (uri.startsWith("content://") || uri.startsWith("file://"))
    val retriever = remember(uri, local) {
        if (!local) null else runCatching { MediaMetadataRetriever().apply { setDataSource(context, Uri.parse(uri)) } }.getOrNull()
    }
    DisposableEffect(retriever) { onDispose { runCatching { retriever?.release() } } }
    val bucket = targetMs / 2000
    val frame by produceState<Bitmap?>(null, retriever, bucket) {
        val r = retriever ?: return@produceState
        delay(120)
        value = withContext(Dispatchers.IO) {
            runCatching {
                val us = bucket * 2000 * 1000
                if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
                else r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull()
        } ?: value
    }
    frame?.let {
        Image(it.asImageBitmap(), contentDescription = "Preview at ${Formatters.duration(targetMs)}",
            modifier = modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(10.dp)))
    }
}

/** Visible error with the actions the engine can actually take. Never hidden behind a toast. */
@Composable
fun ErrorPanel(err: PlaybackError, modifier: Modifier) {
    val pm = LocalContainer.current.playback
    var details by remember(err) { mutableStateOf(false) }
    Column(modifier.padding(24.dp).widthIn(max = 460.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xE6070B14)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Text("Can't play this file", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(err.message, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            err.actions.forEach { a ->
                Button(onClick = {
                    when (a) {
                        PlaybackError.Action.TrySoftware -> pm.setDecoderAndReload(DecoderMode.Software)
                        PlaybackError.Action.TryHardware -> pm.setDecoderAndReload(DecoderMode.Hardware)
                        PlaybackError.Action.Retry -> pm.retry()
                        PlaybackError.Action.SkipNext -> pm.next()
                    }
                }) { Text(a.label) }
            }
        }
        if (err.technical != null) {
            TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Technical details") }
            if (details) Text(err.technical, color = Color.White.copy(alpha = 0.7f), style = MonoStyle)
        }
    }
}
