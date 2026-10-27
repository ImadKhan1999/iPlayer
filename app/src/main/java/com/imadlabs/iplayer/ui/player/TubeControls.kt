package com.imadlabs.iplayer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.playback.PlayerStatus
import com.imadlabs.iplayer.playback.RepeatMode
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.util.Formatters

// "Tube-style" player: the familiar mobile video-site layout (centred previous / play / next,
// red scrubber, time at bottom-left, full screen at bottom-right, captions and settings at
// top-right, double-tap ripples). Layout only: no third-party names, logos or icon assets.

internal val TubeRed = Color(0xFFFF0000)
private val TubeSheetColor = Color(0xFF212121)
private val TubeMuted = Color(0xFFAAAAAA)

@Composable
internal fun TubeControls(
    onClose: () -> Unit,
    onSheet: (PlayerSheet) -> Unit,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onScrub: (Long?) -> Unit,
    onInteract: () -> Unit,
) {
    val pm = LocalContainer.current.playback
    val s by pm.state.collectAsStateWithLifecycle()
    val captionsOn = s.selectedSubtitleTrack >= 0
    Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
        // Top bar: minimise on the left, captions and settings on the right.
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = Color.White, modifier = Modifier.size(30.dp)) }
            if (fullscreen) {
                Text(s.item?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            } else Spacer(Modifier.weight(1f))
            IconButton(onClick = { onSheet(PlayerSheet.Tracks) }) {
                Icon(if (captionsOn) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                    if (captionsOn) "Captions on" else "Captions off", tint = Color.White)
            }
            IconButton(onClick = { onSheet(PlayerSheet.TubeSettings) }) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White) }
        }

        // Centre: previous, play/pause, next.
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            IconButton(onClick = { pm.previous(); onInteract() }, Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
            }
            val ended = s.status == PlayerStatus.Ended
            val playing = s.playWhenReady && !ended
            Box(Modifier.size(68.dp).clip(CircleShape).background(Color(0x4D000000)), contentAlignment = Alignment.Center) {
                IconButton(onClick = { pm.togglePlayPause(); onInteract() }, Modifier.size(68.dp)) {
                    Icon(when { ended -> Icons.Rounded.Replay; playing -> Icons.Rounded.Pause; else -> Icons.Rounded.PlayArrow },
                        when { ended -> "Replay"; playing -> "Pause"; else -> "Play" }, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            val hasNext = s.index in 0 until s.queue.lastIndex || s.repeat == RepeatMode.All
            IconButton(onClick = { pm.next(); onInteract() }, Modifier.size(52.dp), enabled = hasNext) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(38.dp))
            }
        }

        // Bottom: time on the left, full screen on the right, scrubber underneath.
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp).padding(bottom = if (fullscreen) 16.dp else 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Formatters.duration(s.positionMs.coerceAtLeast(0)), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text(" / " + Formatters.duration(s.durationMs.coerceAtLeast(0)), color = TubeMuted, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFullscreen) {
                    Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        if (fullscreen) "Exit full screen" else "Full screen", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            TubeScrubber(s.positionMs, s.durationMs, enabled = s.seekable && s.durationMs > 0, onScrub = { onScrub(it); onInteract() },
                onSeek = { pm.seekTo(it); onInteract() })
        }
    }
}

/** Red progress bar with a round thumb; it thickens while dragging. */
@Composable
internal fun TubeScrubber(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onScrub: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val input = if (!enabled) Modifier else Modifier
        .pointerInput(durationMs) { detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f).times(durationMs).toLong()) } }
        .pointerInput(durationMs) {
            detectHorizontalDragGestures(
                onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f); onScrub((drag!! * durationMs).toLong()) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    onScrub((drag!! * durationMs).toLong())
                },
                onDragEnd = { drag?.let { onSeek((it * durationMs).toLong()) }; drag = null; onScrub(null) },
                onDragCancel = { drag = null; onScrub(null) },
            )
        }
    Box(modifier.fillMaxWidth().height(28.dp).then(input)) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2
            val stroke = (if (drag != null) 5.dp else 3.dp).toPx()
            drawLine(Color(0x4DFFFFFF), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Butt)
            drawLine(TubeRed, Offset(0f, y), Offset(size.width * fraction, y), stroke, StrokeCap.Butt)
            if (enabled) drawCircle(TubeRed, (if (drag != null) 9.dp else 6.dp).toPx(), Offset(size.width * fraction, y))
        }
    }
}

/** Thin progress line shown at the very bottom while the controls are hidden. */
@Composable
internal fun TubeMiniProgress(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawRect(Color(0x4DFFFFFF))
        drawRect(TubeRed, size = size.copy(width = size.width * fraction))
    }
}

/** Double-tap feedback: a curved panel on the tapped side with the accumulated seconds. */
@Composable
internal fun TubeSeekRipple(forward: Boolean, seconds: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelWidth = maxWidth * 0.38f
        val arc = remember(forward) {
            GenericShape { size, _ ->
                // An oval wider than the panel, so the inner edge bulges towards the centre.
                val left = if (forward) 0f else -size.width
                addOval(Rect(left, -size.height * 0.2f, left + size.width * 2f, size.height * 1.2f))
            }
        }
        Box(Modifier.align(if (forward) Alignment.CenterEnd else Alignment.CenterStart).width(panelWidth).fillMaxHeight()
            .clip(arc).background(Color(0x40FFFFFF)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.graphicsLayer { scaleX = if (forward) 1f else -1f }) {
                    repeat(3) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                Text("$seconds seconds", color = Color.White, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TubeSheetFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 520.dp,
        containerColor = TubeSheetColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = {
            Box(Modifier.padding(vertical = 8.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color(0x66FFFFFF)))
        },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun TubeRow(icon: ImageVector?, label: String, value: String?, selected: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        when {
            icon != null -> Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            selected -> Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(24.dp))
            else -> Spacer(Modifier.size(24.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 24.dp).weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = TubeMuted, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Settings list opened from the gear: each row names its current value, like the familiar layout. */
@Composable
internal fun TubeSettingsSheet(onOpen: (PlayerSheet) -> Unit, onLock: () -> Unit, onDismiss: () -> Unit) {
    val pm = LocalContainer.current.playback
    val s by pm.state.collectAsStateWithLifecycle()
    val captions = s.subtitleTracks.firstOrNull { it.id == s.selectedSubtitleTrack }?.name ?: "Off"
    val audio = s.audioTracks.firstOrNull { it.id == s.selectedAudioTrack }?.name
    TubeSheetFrame(onDismiss) {
        TubeRow(Icons.Rounded.ClosedCaption, "Captions", captions) { onOpen(PlayerSheet.Tracks) }
        TubeRow(Icons.Rounded.SlowMotionVideo, "Playback speed", speedLabel(s.speed)) { onOpen(PlayerSheet.TubeSpeed) }
        TubeRow(Icons.Rounded.Repeat, "Loop video", if (s.repeat == RepeatMode.One) "On" else "Off") {
            pm.setRepeat(if (s.repeat == RepeatMode.One) RepeatMode.Off else RepeatMode.One)
        }
        if (s.audioTracks.size > 1) TubeRow(Icons.Rounded.Audiotrack, "Audio track", audio) { onOpen(PlayerSheet.Tracks) }
        TubeRow(Icons.Rounded.AspectRatio, "Picture", s.aspect.label) { onOpen(PlayerSheet.Video) }
        TubeRow(Icons.Rounded.Lock, "Lock screen", null) { onLock() }
        TubeRow(Icons.Rounded.Info, "Media info", null) { onOpen(PlayerSheet.Info) }
        TubeRow(Icons.Rounded.MoreHoriz, "Additional settings", null) { onOpen(PlayerSheet.More) }
    }
}

private val TubeSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

private fun speedLabel(speed: Float) = if (speed == 1f) "Normal" else Formatters.speed(speed)

@Composable
internal fun TubeSpeedSheet(onDismiss: () -> Unit) {
    val pm = LocalContainer.current.playback
    val s by pm.state.collectAsStateWithLifecycle()
    TubeSheetFrame(onDismiss) {
        TubeSpeeds.forEach { v ->
            TubeRow(null, speedLabel(v), null, selected = s.speed == v) { pm.setSpeed(v); onDismiss() }
        }
    }
}
