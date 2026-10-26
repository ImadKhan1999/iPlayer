package com.imadlabs.iplayer.ui.player

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.AudioManager
import android.provider.Settings
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.settings.AspectMode
import com.imadlabs.iplayer.playback.PlayerEvent
import com.imadlabs.iplayer.playback.PlayerStatus
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.music.SeekBar
import com.imadlabs.iplayer.ui.theme.MonoStyle
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Per-file picture adjustments. Transforms and colour need the TextureView output. */
data class VideoAdjust(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val rotation: Int = 0,
    val mirror: Boolean = false,
    val flip: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
) {
    val isColorDefault get() = brightness == 0f && contrast == 1f && saturation == 1f
}

enum class PlayerSheet { None, Tracks, Video, Info, More }

@Composable
fun PlayerScreen(
    videoLayout: VLCVideoLayout,
    inPip: Boolean,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    pipSupported: Boolean,
    window: Window,
) {
    val container = LocalContainer.current
    val pm = container.playback
    val context = LocalContext.current
    val s by pm.state.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val textureOutput = settings.useTextureView

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(PlayerSheet.None) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackTick by remember { mutableIntStateOf(0) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    var adjust by remember(s.item?.key) { mutableStateOf(VideoAdjust()) }
    var boostedFrom by remember { mutableStateOf<Float?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun show(text: String) { feedback = text; feedbackTick++ }
    fun poke() { interaction++; controlsVisible = true }

    BackHandler {
        when {
            sheet != PlayerSheet.None -> sheet = PlayerSheet.None
            locked -> show("Screen locked. Tap the lock to unlock.")
            else -> onClose()
        }
    }
    LaunchedEffect(feedbackTick) { if (feedback != null) { delay(900); feedback = null } }
    LaunchedEffect(controlsVisible, interaction, s.playWhenReady, sheet, seekPreviewMs) {
        val timeout = settings.controlsTimeoutSec
        if (controlsVisible && timeout > 0 && s.playWhenReady && sheet == PlayerSheet.None && seekPreviewMs == null) {
            delay(timeout * 1000L); controlsVisible = false
        }
    }
    LaunchedEffect(Unit) {
        pm.events.collect { e -> if (e is PlayerEvent.Notice) { notice = e.text; delay(2500); notice = null } }
    }

    // Brightness and volume state for gestures.
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volumeAcc by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var brightness by remember {
        mutableFloatStateOf(window.attributes.screenBrightness.takeIf { it >= 0 }
            ?: runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f))
    }

    val seekStepMs = settings.seekIntervalSec * 1000L
    val currentS by rememberUpdatedState(s)
    val currentSettings by rememberUpdatedState(settings)
    val currentLocked by rememberUpdatedState(locked)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { videoLayout.also { (it.parent as? ViewGroup)?.removeView(it) } },
            update = { v -> applyAdjust(v, adjust, textureOutput) },
            // Outside full screen the picture sits between the status and navigation bars.
            modifier = Modifier.fillMaxSize()
                .then(if ((context as? PlayerActivity)?.fullscreen == true || inPip) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars)),
        )

        if (!inPip) {
            // Gesture layer.
            Box(Modifier.fillMaxSize()
                .pointerInput(settings.doubleTapSeek, locked) {
                    detectTapGestures(
                        onTap = { if (controlsVisible) controlsVisible = false else poke() },
                        onDoubleTap = if (!locked && settings.doubleTapSeek) { off ->
                            val forward = off.x > size.width / 2
                            pm.seekBy(if (forward) seekStepMs else -seekStepMs)
                            show(if (forward) "+${settings.seekIntervalSec}s" else "−${settings.seekIntervalSec}s")
                        } else null,
                        onLongPress = if (!locked && settings.longPressSpeed) { _ ->
                            if (currentS.playWhenReady) {
                                boostedFrom = currentS.speed
                                pm.setSpeed(currentSettings.longPressSpeedValue)
                                show("${Formatters.speed(currentSettings.longPressSpeedValue)} speed")
                            }
                        } else null,
                        onPress = {
                            tryAwaitRelease()
                            boostedFrom?.let { pm.setSpeed(it); boostedFrom = null }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var mode = 0 // 0 undecided, 1 seek, 2 brightness, 3 volume, 4 pinch
                        var total = Offset.Zero
                        var seekTarget = 0L
                        val startPos = currentS.positionMs
                        var pinchStartDist = 0f
                        var startAdjust = adjust
                        var twoFingerDownAt = 0L
                        var twoFingerMoved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val sett = currentSettings
                            if (pressed.size >= 2) {
                                val d = hypot(pressed[0].position.x - pressed[1].position.x, pressed[0].position.y - pressed[1].position.y)
                                if (twoFingerDownAt == 0L) { twoFingerDownAt = event.changes.first().uptimeMillis; pinchStartDist = d; startAdjust = adjust }
                                if (abs(d - pinchStartDist) > slop * 2) twoFingerMoved = true
                                if (sett.twoFingerLock && !twoFingerMoved && event.changes.first().uptimeMillis - twoFingerDownAt > 800) {
                                    locked = !currentLocked; show(if (locked) "Locked" else "Unlocked")
                                    twoFingerDownAt = Long.MAX_VALUE / 2; twoFingerMoved = true
                                }
                                if (!currentLocked && sett.pinchZoom && textureOutput && twoFingerMoved && pinchStartDist > 0) {
                                    mode = 4
                                    val zoom = (startAdjust.zoom * d / pinchStartDist).coerceIn(0.5f, 6f)
                                    val pan = pressed.map { it.positionChange() }.reduce { a, b -> a + b } / 2f
                                    adjust = adjust.copy(zoom = zoom, panX = adjust.panX + pan.x, panY = adjust.panY + pan.y)
                                    show("Zoom ${(zoom * 100).roundToInt()}%")
                                    event.changes.forEach { it.consume() }
                                }
                                continue
                            }
                            if (currentLocked || mode == 4) continue
                            val change = pressed.first()
                            total += change.positionChange()
                            if (mode == 0 && hypot(total.x, total.y) > slop) {
                                mode = when {
                                    abs(total.x) > abs(total.y) -> if (sett.seekGesture && currentS.seekable && currentS.durationMs > 0) 1 else -1
                                    down.position.x < size.width / 2 -> if (sett.brightnessGesture) 2 else -1
                                    else -> if (sett.volumeGesture) 3 else -1
                                }
                            }
                            when (mode) {
                                1 -> {
                                    val span = minOf(currentS.durationMs, 180_000L).coerceAtLeast(30_000L)
                                    seekTarget = (startPos + (total.x / size.width * span).toLong()).coerceIn(0, currentS.durationMs)
                                    seekPreviewMs = seekTarget
                                    val delta = seekTarget - startPos
                                    show("${Formatters.duration(seekTarget)}  (${if (delta >= 0) "+" else "−"}${Formatters.duration(abs(delta))})")
                                    change.consume()
                                }
                                2 -> {
                                    brightness = (brightness - change.positionChange().y / size.height).coerceIn(0.01f, 1f)
                                    window.attributes = window.attributes.apply { screenBrightness = brightness }
                                    show("Brightness ${(brightness * 100).roundToInt()}%")
                                    change.consume()
                                }
                                3 -> {
                                    volumeAcc = (volumeAcc - change.positionChange().y / size.height * maxVolume * 1.5f).coerceIn(0f, maxVolume.toFloat())
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeAcc.roundToInt(), 0)
                                    show("Volume ${(volumeAcc / maxVolume * 100).roundToInt()}%")
                                    change.consume()
                                }
                            }
                        }
                        if (mode == 1) { pm.seekTo(seekTarget); seekPreviewMs = null }
                    }
                })
        }

        // Buffering indicator.
        if (s.error == null && (s.status == PlayerStatus.Opening || s.status == PlayerStatus.Buffering && s.bufferingPercent < 100f)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                if (s.status == PlayerStatus.Buffering) Text("${s.bufferingPercent.roundToInt()}%", color = Color.White, style = MonoStyle,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }

        // Gesture feedback.
        feedback?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xB3070B14))
                    .padding(horizontal = 18.dp, vertical = 10.dp))
        }
        if (!inPip) seekPreviewMs?.let { SeekPreview(s.item?.uri, it, settings.seekPreview, Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) }

        s.error?.let { err -> if (!inPip) ErrorPanel(err, Modifier.align(Alignment.Center)) }

        notice?.let {
            if (!inPip) Text(it, color = Color.White, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC070B14)).padding(horizontal = 14.dp, vertical = 8.dp))
        }

        // Controls.
        if (!inPip) {
            if (locked) {
                AnimatedVisibility(controlsVisible, Modifier.align(Alignment.CenterStart).windowInsetsPadding(WindowInsets.safeDrawing),
                    enter = fadeIn(), exit = fadeOut()) {
                    RoundButton(Icons.Rounded.Lock, "Unlock screen") { locked = false; poke(); show("Unlocked") }
                }
            } else {
                AnimatedVisibility(controlsVisible, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
                    Controls(
                        onClose = onClose,
                        onSheet = { sheet = it; poke() },
                        onLock = { locked = true; poke(); show("Locked") },
                        onPip = onEnterPip.takeIf { pipSupported },
                        onRotate = { (context as? PlayerActivity)?.toggleOrientation(); poke() },
                        fullscreen = (context as? PlayerActivity)?.fullscreen == true,
                        onFullscreen = { (context as? PlayerActivity)?.toggleFullscreen(); poke() },
                        onInteract = ::poke,
                        seekStepMs = seekStepMs,
                        showRemaining = settings.showRemainingTime,
                    )
                }
            }
        }
    }

    when (sheet) {
        PlayerSheet.None -> Unit
        PlayerSheet.Tracks -> TracksSheet { sheet = PlayerSheet.None }
        PlayerSheet.Video -> VideoSheet(adjust, textureOutput, { adjust = it }) { sheet = PlayerSheet.None }
        PlayerSheet.Info -> InfoSheet { sheet = PlayerSheet.None }
        PlayerSheet.More -> MoreSheet(onOpen = { sheet = it }) { sheet = PlayerSheet.None }
    }
}

@Composable
private fun Controls(
    onClose: () -> Unit,
    onSheet: (PlayerSheet) -> Unit,
    onLock: () -> Unit,
    onPip: (() -> Unit)?,
    onRotate: () -> Unit,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onInteract: () -> Unit,
    seekStepMs: Long,
    showRemaining: Boolean,
) {
    val pm = LocalContainer.current.playback
    val s by pm.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
        // Top bar.
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close player", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(s.item?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                val info = listOfNotNull(
                    s.videoHeight.takeIf { it > 0 }?.let { "${s.videoWidth}×${s.videoHeight}" },
                    s.decoder.label.takeIf { s.status != PlayerStatus.Idle },
                    if (s.queue.size > 1) "${s.index + 1}/${s.queue.size}" else null,
                    s.abStartMs?.let { "A–B" + if (s.abEndMs == null) " (A set)" else "" },
                ).joinToString(" · ")
                if (info.isNotEmpty()) Text(info, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            IconButton(onClick = { onSheet(PlayerSheet.Tracks) }) { Icon(Icons.Rounded.ClosedCaption, "Audio and subtitles", tint = Color.White) }
            IconButton(onClick = { onSheet(PlayerSheet.More) }) { Icon(Icons.Rounded.MoreVert, "More options", tint = Color.White) }
        }

        // Centre transport.
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.queue.size > 1) RoundButton(Icons.Rounded.SkipPrevious, "Previous") { pm.previous(); onInteract() }
            RoundButton(Icons.Rounded.Replay10, "Back ${seekStepMs / 1000} seconds") { pm.seekBy(-seekStepMs); onInteract() }
            val playing = s.playWhenReady && s.status != PlayerStatus.Ended
            Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                IconButton(onClick = { pm.togglePlayPause(); onInteract() }, Modifier.size(72.dp)) {
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                }
            }
            RoundButton(Icons.Rounded.Forward10, "Forward ${seekStepMs / 1000} seconds") { pm.seekBy(seekStepMs); onInteract() }
            if (s.queue.size > 1) RoundButton(Icons.Rounded.SkipNext, "Next") { pm.next(); onInteract() }
        }

        // Bottom bar.
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 4.dp)) {
            SeekBar(s, onSeek = { pm.seekTo(it); onInteract() }, light = true)
            if (showRemaining && s.durationMs > 0) {
                Text("−" + Formatters.duration((s.durationMs - s.positionMs).coerceAtLeast(0)), color = Color.White, style = MonoStyle,
                    modifier = Modifier.align(Alignment.End))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLock) { Icon(Icons.Rounded.LockOpen, "Lock screen", tint = Color.White) }
                IconButton(onClick = {
                    val next = AspectMode.entries[(s.aspect.ordinal + 1) % AspectMode.entries.size]
                    pm.setAspect(next); onInteract()
                }) { Icon(Icons.Rounded.AspectRatio, "Aspect ratio: ${s.aspect.label}", tint = Color.White) }
                Text(s.aspect.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onSheet(PlayerSheet.More) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Speed, "Speed", tint = Color.White) }
                }
                if (s.speed != 1f) Text(Formatters.speed(s.speed), color = Color.White, style = MonoStyle)
                IconButton(onClick = { onSheet(PlayerSheet.Tracks) }) { Icon(Icons.Rounded.Audiotrack, "Audio track", tint = Color.White) }
                IconButton(onClick = onRotate) { Icon(Icons.Rounded.ScreenRotation, "Rotate screen", tint = Color.White) }
                IconButton(onClick = onFullscreen) {
                    Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        if (fullscreen) "Exit full screen" else "Full screen", tint = Color.White)
                }
                if (onPip != null) IconButton(onClick = onPip) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture-in-picture", tint = Color.White) }
            }
        }
    }
}

@Composable
internal fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x66070B14)), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, Modifier.size(52.dp)) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

/** Applies zoom/pan/rotation/mirroring as view transforms and colour as a TextureView layer paint. */
private fun applyAdjust(layout: VLCVideoLayout, a: VideoAdjust, texture: Boolean) {
    if (!texture) {
        layout.scaleX = 1f; layout.scaleY = 1f; layout.rotation = 0f; layout.translationX = 0f; layout.translationY = 0f
        return
    }
    val w = layout.width.toFloat().coerceAtLeast(1f)
    val h = layout.height.toFloat().coerceAtLeast(1f)
    val fit = if (a.rotation % 180 != 0) minOf(w / h, h / w) else 1f
    layout.rotation = a.rotation.toFloat()
    layout.scaleX = a.zoom * fit * if (a.mirror) -1f else 1f
    layout.scaleY = a.zoom * fit * if (a.flip) -1f else 1f
    layout.translationX = a.panX
    layout.translationY = a.panY
    findTextureView(layout)?.let { tv ->
        if (a.isColorDefault) tv.setLayerPaint(null) else tv.setLayerPaint(Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix(a)) })
    }
}

internal fun colorMatrix(a: VideoAdjust): ColorMatrix {
    val m = ColorMatrix().apply { setSaturation(a.saturation) }
    val c = a.contrast
    val t = (1f - c) * 128f + a.brightness * 255f
    m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    return m
}

private fun findTextureView(v: View): TextureView? {
    if (v is TextureView) return v
    if (v is ViewGroup) for (i in 0 until v.childCount) findTextureView(v.getChildAt(i))?.let { return it }
    return null
}

