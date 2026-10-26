package com.imadlabs.iplayer.ui.settings

import android.content.ActivityNotFoundException
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.BuildConfig
import com.imadlabs.iplayer.data.settings.AppSettings
import com.imadlabs.iplayer.data.settings.AspectMode
import com.imadlabs.iplayer.data.settings.DecoderMode
import com.imadlabs.iplayer.data.settings.EndAction
import com.imadlabs.iplayer.data.settings.OrientationPref
import com.imadlabs.iplayer.data.settings.ReplayGainMode
import com.imadlabs.iplayer.data.settings.StereoMode
import com.imadlabs.iplayer.data.settings.SubtitleColor
import com.imadlabs.iplayer.data.settings.SubtitleOutline
import com.imadlabs.iplayer.data.settings.SubtitleSize
import com.imadlabs.iplayer.data.settings.ThemeMode
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.MediaPermissions
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.BackTopBar
import com.imadlabs.iplayer.ui.components.ConfirmDialog
import com.imadlabs.iplayer.ui.components.toast
import com.imadlabs.iplayer.ui.theme.AccentColor
import com.imadlabs.iplayer.update.UpdateState
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.launch

private data class Category(val key: String, val title: String, val summary: String, val icon: ImageVector)

private val categories = listOf(
    Category("playback", "Playback", "Resume, speed, end of file, decoder", Icons.Rounded.PlayCircle),
    Category("video", "Video", "Aspect ratio, orientation, picture-in-picture", Icons.Rounded.Videocam),
    Category("gestures", "Gestures", "Swipes, double tap, pinch, lock", Icons.Rounded.TouchApp),
    Category("audio", "Audio", "Equalizer, ReplayGain, normalizer, focus", Icons.Rounded.Audiotrack),
    Category("subtitles", "Subtitles", "Style, languages, encoding", Icons.Rounded.ClosedCaption),
    Category("library", "Library", "Hidden folders, storage access, rescan", Icons.Rounded.VideoLibrary),
    Category("network", "Network", "Streaming buffer", Icons.Rounded.Lan),
    Category("appearance", "Appearance", "Theme, accent colour, motion", Icons.Rounded.Palette),
    Category("privacy", "Privacy", "History and stored data", Icons.Rounded.PrivacyTip),
    Category("updates", "Updates", "Version and update checks", Icons.Rounded.SystemUpdate),
    Category("advanced", "Advanced", "Diagnostics and reset", Icons.Rounded.BugReport),
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Scaffold(topBar = { BackTopBar("Settings", onBack) }) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp)) {
            items(categories, key = { it.key }) { c ->
                ListItem(
                    headlineContent = { Text(c.title) },
                    supportingContent = { Text(c.summary) },
                    leadingContent = { Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(Routes.settings(c.key)) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("About iPlayer") },
                    supportingContent = { Text("Version ${BuildConfig.VERSION_NAME} · ImadLabs") },
                    leadingContent = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(Routes.ABOUT) },
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryScreen(category: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val s by container.settings.state.collectAsStateWithLifecycle()
    val title = categories.firstOrNull { it.key == category }?.title ?: "Settings"
    val set: ((AppSettings) -> AppSettings) -> Unit = { t -> scope.launch { container.settings.update(t) } }
    Scaffold(topBar = { BackTopBar(title, onBack) }) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 32.dp)) {
            when (category) {
                "playback" -> playback(s, set)
                "video" -> video(s, set)
                "gestures" -> gestures(s, set) { scope.launch { container.settings.resetGestures() } }
                "audio" -> audio(s, set, onOpen)
                "subtitles" -> subtitles(s, set)
                "library" -> item { LibrarySettings(s, set) }
                "network" -> network(s, set)
                "appearance" -> appearance(s, set)
                "privacy" -> item { PrivacySettings(s, set) }
                "updates" -> item { UpdateSettings(s, set) }
                "advanced" -> item { AdvancedSettings(onOpen) }
            }
        }
    }
}

// ------------------------------------------------------------------ categories

private fun LazyListScope.playback(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    item { SwitchRow("Resume where you left off", "Continue files from the last position", s.resumePlayback) { v -> set { it.copy(resumePlayback = v) } } }
    item { SwitchRow("Play next automatically", "Continue with the next item in the queue", s.autoPlayNext) { v -> set { it.copy(autoPlayNext = v) } } }
    item { ChoiceRow("When a file ends", EndAction.entries, s.endAction, { it.label }) { v -> set { it.copy(endAction = v) } } }
    item {
        SliderRow("Default speed", s.defaultSpeed, 0.25f..3f, 10, Formatters.speed(s.defaultSpeed)) { v ->
            set { it.copy(defaultSpeed = (v * 4).let(Math::round) / 4f) }
        }
    }
    item { SwitchRow("Reset speed for each file", "Otherwise the last speed carries over", s.resetSpeedOnNewMedia) { v -> set { it.copy(resetSpeedOnNewMedia = v) } } }
    item {
        ChoiceRow("Seek step", listOf(5, 10, 15, 30, 60), s.seekIntervalSec, { "$it seconds" }) { v -> set { it.copy(seekIntervalSec = v) } }
    }
    item { Header("Decoding") }
    item { ChoiceRow("Decoder", DecoderMode.entries, s.decoderMode, { it.label }, { it.description }) { v -> set { it.copy(decoderMode = v) } } }
    item {
        SwitchRow("Fall back to software decoding", "Retry once with software decoding if hardware decoding fails", s.decoderFallback) { v ->
            set { it.copy(decoderFallback = v) }
        }
    }
}

private fun LazyListScope.video(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    item { ChoiceRow("Default aspect ratio", AspectMode.entries, s.defaultAspect, { it.label }) { v -> set { it.copy(defaultAspect = v) } } }
    item { ChoiceRow("Screen orientation", OrientationPref.entries, s.orientation, { it.label }) { v -> set { it.copy(orientation = v) } } }
    item { ChoiceRow("Hide controls after", listOf(2, 3, 4, 6, 8, 0), s.controlsTimeoutSec, { if (it == 0) "Never" else "$it seconds" }) { v -> set { it.copy(controlsTimeoutSec = v) } } }
    item { SwitchRow("Show remaining time", "Instead of the total duration", s.showRemainingTime) { v -> set { it.copy(showRemainingTime = v) } } }
    item { SwitchRow("Seek preview thumbnails", "Local files only; uses Android's frame extractor", s.seekPreview) { v -> set { it.copy(seekPreview = v) } } }
    item { Header("Background") }
    item { SwitchRow("Picture-in-picture on leave", "Keep watching in a small window when you go home", s.autoPip) { v -> set { it.copy(autoPip = v) } } }
    item { SwitchRow("Keep audio when leaving a video", "If picture-in-picture is off or unavailable, continue as audio", s.backgroundVideoAudio) { v -> set { it.copy(backgroundVideoAudio = v) } } }
    item { Header("Rendering") }
    item {
        SwitchRow("Use TextureView output", "Required for zoom, pan, rotation, mirroring and colour adjustments. Turn off to use SurfaceView, which can be more efficient for HDR and high-bitrate video.",
            s.useTextureView) { v -> set { it.copy(useTextureView = v) } }
    }
}

private fun LazyListScope.gestures(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit, reset: () -> Unit) {
    item { SwitchRow("Brightness", "Swipe up and down on the left side", s.brightnessGesture) { v -> set { it.copy(brightnessGesture = v) } } }
    item { SwitchRow("Volume", "Swipe up and down on the right side", s.volumeGesture) { v -> set { it.copy(volumeGesture = v) } } }
    item { SwitchRow("Seek", "Swipe left and right", s.seekGesture) { v -> set { it.copy(seekGesture = v) } } }
    item { SwitchRow("Double tap to seek", "Left or right side, by the seek step", s.doubleTapSeek) { v -> set { it.copy(doubleTapSeek = v) } } }
    item { SwitchRow("Pinch to zoom", "Pinch, then drag to pan (TextureView output)", s.pinchZoom) { v -> set { it.copy(pinchZoom = v) } } }
    item { SwitchRow("Hold for fast playback", "Press and hold the video", s.longPressSpeed) { v -> set { it.copy(longPressSpeed = v) } } }
    if (s.longPressSpeed) item {
        ChoiceRow("Hold speed", listOf(1.5f, 2f, 2.5f, 3f), s.longPressSpeedValue, { Formatters.speed(it) }) { v -> set { it.copy(longPressSpeedValue = v) } }
    }
    item { SwitchRow("Two-finger long press locks", "Lock or unlock the screen with a two-finger hold", s.twoFingerLock) { v -> set { it.copy(twoFingerLock = v) } } }
    item { ActionRow("Reset gestures", "Restore the default gesture settings", onClick = reset) }
}

private fun LazyListScope.audio(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit, onOpen: (String) -> Unit) {
    item {
        ActionRow("Equalizer", if (s.equalizer.enabled) "On" else "Off", Icons.Rounded.Tune) { onOpen(Routes.EQUALIZER) }
    }
    item { ChoiceRow("ReplayGain", ReplayGainMode.entries, s.replayGain, { it.label }, { "Uses gain tags in the file, when present" }) { v -> set { it.copy(replayGain = v) } } }
    if (s.replayGain != ReplayGainMode.Off) item {
        SliderRow("ReplayGain pre-amp", s.replayGainPreamp, -6f..6f, 11, "%+.0f dB".format(s.replayGainPreamp)) { v -> set { it.copy(replayGainPreamp = Math.round(v).toFloat()) } }
    }
    item { SwitchRow("Volume normalizer", "Evens out loud and quiet content", s.volumeNormalizer) { v -> set { it.copy(volumeNormalizer = v) } } }
    item { SwitchRow("Stereo widening", "Spatial enhancement for headphones", s.stereoWiden) { v -> set { it.copy(stereoWiden = v) } } }
    item { ChoiceRow("Channels", StereoMode.entries, s.stereoMode, { it.label }) { v -> set { it.copy(stereoMode = v) } } }
    item {
        SwitchRow("Digital passthrough", "Send AC-3/DTS untouched to a compatible receiver over HDMI or S/PDIF. Disables the equalizer for those tracks.",
            s.digitalPassthrough) { v -> set { it.copy(digitalPassthrough = v) } }
    }
    item { Header("Interruptions") }
    item { SwitchRow("Pause when headphones disconnect", null, s.pauseOnHeadsetDisconnect) { v -> set { it.copy(pauseOnHeadsetDisconnect = v) } } }
    item { SwitchRow("Resume after interruptions", "For example after a short notification or call", s.resumeOnFocusGain) { v -> set { it.copy(resumeOnFocusGain = v) } } }
    item {
        Text("Note: gapless playback and crossfade aren't available with the current engine, so they aren't offered.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val encodings = listOf("" to "Automatic (UTF-8, then system)", "UTF-8" to "UTF-8", "Windows-1252" to "Western (Windows-1252)",
    "ISO-8859-1" to "Western (ISO-8859-1)", "Windows-1250" to "Central European (Windows-1250)", "Windows-1251" to "Cyrillic (Windows-1251)",
    "KOI8-R" to "Cyrillic (KOI8-R)", "Windows-1253" to "Greek (Windows-1253)", "Windows-1254" to "Turkish (Windows-1254)",
    "Windows-1255" to "Hebrew (Windows-1255)", "Windows-1256" to "Arabic (Windows-1256)", "Windows-1257" to "Baltic (Windows-1257)",
    "Windows-1258" to "Vietnamese (Windows-1258)", "Windows-874" to "Thai (Windows-874)", "GB18030" to "Chinese simplified (GB18030)",
    "Big5" to "Chinese traditional (Big5)", "Shift_JIS" to "Japanese (Shift_JIS)", "EUC-KR" to "Korean (EUC-KR)")

private fun LazyListScope.subtitles(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    item { SwitchRow("Show subtitles by default", "When the file or folder has them", s.subtitlesEnabled) { v -> set { it.copy(subtitlesEnabled = v) } } }
    item { TextRow("Preferred subtitle language", s.preferredSubtitleLanguage, "Language code or name, e.g. en, ar, French") { v -> set { it.copy(preferredSubtitleLanguage = v) } } }
    item { TextRow("Preferred audio language", s.preferredAudioLanguage, "Language code or name, e.g. en, ja") { v -> set { it.copy(preferredAudioLanguage = v) } } }
    item { SwitchRow("Load matching subtitle files", "Same-name .srt/.ass/.vtt files next to the video, where storage access allows", s.subtitleAutoDetect) { v -> set { it.copy(subtitleAutoDetect = v) } } }
    item { ChoiceRow("Text encoding", encodings.map { it.first }, s.subtitleEncoding, { k -> encodings.first { it.first == k }.second }) { v -> set { it.copy(subtitleEncoding = v) } } }
    item { Header("Style") }
    item {
        Text("Style applies to text subtitles (SRT, VTT). ASS/SSA keep their own styling. Changes apply on the next file or when the engine restarts.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item { ChoiceRow("Size", SubtitleSize.entries, s.subtitleSize, { it.label }) { v -> set { it.copy(subtitleSize = v) } } }
    item { ChoiceRow("Colour", SubtitleColor.entries, s.subtitleColor, { it.label }) { v -> set { it.copy(subtitleColor = v) } } }
    item { SwitchRow("Bold", null, s.subtitleBold) { v -> set { it.copy(subtitleBold = v) } } }
    item { ChoiceRow("Outline", SubtitleOutline.entries, s.subtitleOutline, { it.label }) { v -> set { it.copy(subtitleOutline = v) } } }
    item { SwitchRow("Shadow", null, s.subtitleShadow) { v -> set { it.copy(subtitleShadow = v) } } }
    item {
        SliderRow("Background opacity", s.subtitleBackgroundOpacity.toFloat(), 0f..100f, 0, "${s.subtitleBackgroundOpacity}%") { v ->
            set { it.copy(subtitleBackgroundOpacity = v.toInt()) }
        }
    }
    item {
        SliderRow("Bottom margin", s.subtitleBottomMargin.toFloat(), 0f..200f, 0, "${s.subtitleBottomMargin}px") { v ->
            set { it.copy(subtitleBottomMargin = v.toInt()) }
        }
    }
}

private fun LazyListScope.network(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    item {
        ChoiceRow("Network buffer", listOf(300, 1000, 1500, 3000, 5000, 10000), s.networkCachingMs,
            { "${it / 1000.0}".removeSuffix(".0") + " s" }, { "More buffer helps on slow or unstable connections, at the cost of a slower start" }) { v ->
            set { it.copy(networkCachingMs = v) }
        }
    }
    item {
        Text("Saved servers and their passwords are managed in Browse › Network. Passwords are encrypted with a key kept in the Android Keystore.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.appearance(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    item { ChoiceRow("Theme", ThemeMode.entries, s.themeMode, { it.label }) { v -> set { it.copy(themeMode = v) } } }
    item { ChoiceRow("Accent colour", AccentColor.entries, s.accent, { it.label }) { v -> set { it.copy(accent = v) } } }
    item { SwitchRow("Animations", "Transitions and motion effects", s.animations) { v -> set { it.copy(animations = v) } } }
    item { SwitchRow("Thumbnails", "Show video frames and album art in lists", s.showThumbnails) { v -> set { it.copy(showThumbnails = v) } } }
    item { SwitchRow("Group TV episodes", "Detect shows and seasons from file names", s.groupEpisodes) { v -> set { it.copy(groupEpisodes = v) } } }
}

@Composable
private fun LibrarySettings(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val excluded by container.library.excludedFolders.collectAsStateWithLifecycle(emptyList())
    var hasAllFiles by remember { mutableStateOf(MediaPermissions.hasAllFiles()) }
    LaunchedEffect(Unit) { hasAllFiles = MediaPermissions.hasAllFiles() }
    Column {
        ActionRow("Rescan media", "Refresh the library from Android's media index") {
            scope.launch { container.library.refresh(); context.toast("Library refreshed") }
        }
        ActionRow("Storage permission", "Change which photos and media iPlayer can read") {
            context.startActivity(MediaPermissions.appSettingsIntent(context))
        }
        if (MediaPermissions.allFilesSupported()) {
            ActionRow("All files access", if (hasAllFiles) "Allowed. iPlayer can find subtitle and lyric files next to your media."
            else "Optional. Lets iPlayer find subtitle (.srt, .ass) and lyric (.lrc) files next to your media.") {
                try { context.startActivity(MediaPermissions.allFilesIntent(context)) } catch (_: ActivityNotFoundException) { context.toast("Not available on this device") }
            }
        }
        Header("Hidden folders")
        if (excluded.isEmpty()) Text("No hidden folders. Hide one from a folder's screen or a video's menu. Hiding only removes it from iPlayer's library; it is not encrypted or protected.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        excluded.forEach { f ->
            ListItem(
                headlineContent = { Text(f.path.substringAfterLast('/')) },
                supportingContent = { Text(f.path) },
                trailingContent = { IconButton(onClick = { scope.launch { container.library.include(f.path) } }) { Icon(Icons.Rounded.Close, "Show again") } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun PrivacySettings(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    Column {
        SwitchRow("Keep playback history", "Resume positions, recently played and most played. Stored only on this device.", s.historyEnabled) { v ->
            set { it.copy(historyEnabled = v) }
        }
        ActionRow("Clear playback history", "Removes resume positions and per-file track choices") { confirm = true }
        Text("iPlayer has no analytics, ads or tracking. The only network requests it makes on its own are the update check (if enabled) and loading the media you open. App data is excluded from cloud backup.",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirm) ConfirmDialog("Clear playback history?", "This can't be undone. Your media files are not affected.", "Clear", { confirm = false }) {
        confirm = false
        scope.launch { container.database.playbackRecords().clear(); context.toast("History cleared") }
    }
}

@Composable
private fun UpdateSettings(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val state by container.updates.state.collectAsStateWithLifecycle()
    val cached by container.appState.lastUpdateCheck.collectAsStateWithLifecycle(null)
    Column {
        ListItem(
            headlineContent = { Text("Installed version") },
            supportingContent = { Text("iPlayer ${container.updates.installedVersionName} (${container.updates.installedVersionCode}) · ${if (BuildConfig.SELF_UPDATE) "direct build" else "store build"}") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        SwitchRow("Check for updates on launch", "At most once per launch, in the background. Nothing is downloaded without your confirmation.", s.autoUpdateCheck) { v ->
            set { it.copy(autoUpdateCheck = v) }
        }
        val status = when (val st = state) {
            UpdateState.Idle -> cached?.takeIf { it.checkedAt > 0 }?.let { "Last checked ${DateUtils.getRelativeTimeSpanString(it.checkedAt)}" } ?: "Not checked yet"
            UpdateState.Checking -> "Checking…"
            is UpdateState.UpToDate -> "You have the latest version"
            is UpdateState.Available -> "iPlayer ${st.release.versionName} is available"
            is UpdateState.Failed -> st.message
        }
        ActionRow("Check now", status) { scope.launch { container.updates.check(userInitiated = true) } }
        Text("Update source: ${BuildConfig.UPDATE_MANIFEST_URL}",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdvancedSettings(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    Column {
        ActionRow("Diagnostics", "Engine log for troubleshooting (passwords and tokens are redacted)", Icons.Rounded.BugReport) { onOpen(Routes.DIAGNOSTICS) }
        ActionRow("Reset all settings", "Restore every setting to its default. Library, playlists and history are kept.") { confirm = true }
    }
    if (confirm) ConfirmDialog("Reset all settings?", "Every setting returns to its default value.", "Reset", { confirm = false }) {
        confirm = false
        scope.launch { container.settings.resetAll(); context.toast("Settings reset") }
    }
}

// ------------------------------------------------------------------ rows

@Composable
private fun Header(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(checked, role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
private fun ActionRow(title: String, summary: String?, icon: ImageVector? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, null) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    summary: ((T) -> String)? = null,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(label(selected)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { open = true },
    )
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options) { o ->
                    Row(Modifier.fillMaxWidth().selectable(o == selected, role = Role.RadioButton) { onSelect(o); open = false }
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(o == selected, null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(label(o))
                            summary?.invoke(o)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
    )
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, display: String, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(local, { local = it }, valueRange = range, steps = steps, onValueChangeFinished = { onChange(local) })
    }
}

@Composable
private fun TextRow(title: String, value: String, hint: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value.ifBlank { "Not set (use the file's default)" }) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { OutlinedTextField(text, { text = it }, label = { Text(hint) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onChange(text.trim()); open = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { onChange(""); open = false }) { Text("Clear") } },
        )
    }
}

