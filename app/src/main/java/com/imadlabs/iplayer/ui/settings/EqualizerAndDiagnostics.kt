package com.imadlabs.iplayer.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.BuildConfig
import com.imadlabs.iplayer.data.settings.EqualizerSettings
import com.imadlabs.iplayer.playback.EqualizerCatalog
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.components.BackTopBar
import com.imadlabs.iplayer.ui.components.toast
import com.imadlabs.iplayer.ui.theme.MonoStyle
import com.imadlabs.iplayer.util.Diagnostics
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC

/**
 * 10-band equalizer backed by LibVLC's audio equalizer (applied live to the playing stream).
 * Presets come from the engine; moving a band turns the setting into a custom curve.
 */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val catalog: EqualizerCatalog? = remember { runCatching { container.playback.equalizerCatalog() }.getOrNull() }
    val s by container.settings.state.collectAsStateWithLifecycle()
    val eq = s.equalizer
    fun save(t: (EqualizerSettings) -> EqualizerSettings) = scope.launch { container.settings.update { it.copy(equalizer = t(it.equalizer)) } }

    Scaffold(topBar = {
        BackTopBar("Equalizer", onBack) {
            TextButton(onClick = {
                val preset = catalog?.presets?.getOrNull(0)
                save { it.copy(presetIndex = 0, preamp = preset?.preamp ?: 12f, bands = emptyList(), customName = null) }
            }) { Text("Reset") }
        }
    }) { pad ->
        if (catalog == null) {
            Text("The audio engine couldn't be started on this device, so the equalizer is unavailable.", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        val presetBands = catalog.presets.getOrNull(eq.presetIndex)?.bands ?: List(catalog.bandFrequencies.size) { 0f }
        val bands = if (eq.bands.size == catalog.bandFrequencies.size) eq.bands else presetBands
        val custom = eq.bands.size == catalog.bandFrequencies.size && eq.bands != presetBands
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).verticalScroll(rememberScrollState())) {
            SwitchRow("Equalizer", "Applies to all audio and video playback", eq.enabled) { v -> save { it.copy(enabled = v) } }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (custom) item { FilterChip(true, {}, label = { Text("Custom") }) }
                itemsIndexed(catalog.presets) { i, p ->
                    FilterChip(!custom && eq.presetIndex == i, onClick = {
                        save { it.copy(enabled = true, presetIndex = i, preamp = p.preamp, bands = emptyList(), customName = null) }
                    }, label = { Text(p.name) })
                }
            }
            Row(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 8.dp, vertical = 16.dp)
                .horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
                bands.forEachIndexed { i, gain ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                        Text("%+.0f".format(gain), style = MonoStyle)
                        VerticalSlider(gain, -20f..20f, Modifier.weight(1f), enabled = eq.enabled) { v ->
                            save { cur -> cur.copy(bands = bands.toMutableList().also { it[i] = v }, customName = "Custom") }
                        }
                        Text(frequencyLabel(catalog.bandFrequencies[i]), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row { Text("Pre-amp", Modifier.weight(1f)); Text("%+.1f dB".format(eq.preamp), style = MonoStyle) }
                var pre by remember(eq.preamp) { mutableStateOf(eq.preamp) }
                Slider(pre, { pre = it }, valueRange = -20f..20f, enabled = eq.enabled, onValueChangeFinished = { save { it.copy(preamp = pre) } })
                Text("Lower the pre-amp if boosted bands cause distortion. Bypassed for digital passthrough tracks.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun frequencyLabel(hz: Float) = if (hz >= 1000) "${(hz / 1000).let { if (it % 1f == 0f) it.toInt().toString() else "%.1f".format(it) }}k" else hz.toInt().toString()

/** A Material slider rotated to vertical, committing on release. */
@Composable
private fun VerticalSlider(value: Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier, enabled: Boolean, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableStateOf(value) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Slider(
            local, { local = it }, valueRange = range, enabled = enabled,
            onValueChangeFinished = { onChange(Math.round(local).toFloat()) },
            modifier = Modifier
                .graphicsLayer { rotationZ = -90f }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints(minWidth = constraints.maxHeight, maxWidth = constraints.maxHeight,
                        minHeight = 0, maxHeight = constraints.maxWidth))
                    layout(placeable.height, placeable.width) {
                        placeable.place(-(placeable.width - placeable.height) / 2, (placeable.width - placeable.height) / 2)
                    }
                },
        )
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf(Diagnostics.snapshot()) }
    val header = remember {
        "iPlayer ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · " +
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · ABIs ${android.os.Build.SUPPORTED_ABIS.joinToString()} · LibVLC ${runCatching { LibVLC.version() }.getOrDefault("not loaded")}"
    }
    Scaffold(topBar = {
        BackTopBar("Diagnostics", onBack, "${lines.size} entries") {
            IconButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("iPlayer diagnostics", (listOf(header) + lines).joinToString("\n")))
                context.toast("Copied")
            }) { Icon(Icons.Rounded.ContentCopy, "Copy log") }
            IconButton(onClick = { Diagnostics.clear(); lines = emptyList() }) { Icon(Icons.Rounded.DeleteSweep, "Clear log") }
        }
    }) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                Text(header, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Kept in memory only and cleared when the app closes. Passwords, tokens and URL credentials are redacted before they're recorded.",
                    Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(lines.asReversed()) { Text(it, style = MonoStyle, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}
