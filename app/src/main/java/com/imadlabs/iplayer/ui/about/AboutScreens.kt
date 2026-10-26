package com.imadlabs.iplayer.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imadlabs.iplayer.BuildConfig
import com.imadlabs.iplayer.R
import com.imadlabs.iplayer.startup.TelegramLauncher
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.BackTopBar
import com.imadlabs.iplayer.ui.components.toast
import com.imadlabs.iplayer.ui.theme.LocalImadExtras
import com.imadlabs.iplayer.ui.theme.MonoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import java.util.Calendar

@Composable
fun AboutScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val dark = LocalImadExtras.current.isDark
    Scaffold(topBar = { BackTopBar("About", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.iplayer_app_icon), contentDescription = "iPlayer icon",
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
            Spacer(Modifier.height(16.dp))
            Text("iPlayer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (BuildConfig.SELF_UPDATE) "Direct build" else "Store build", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            Image(painterResource(if (dark) R.drawable.imadlabs_logo_on_dark else R.drawable.imadlabs_logo_on_light),
                contentDescription = "ImadLabs", modifier = Modifier.widthIn(max = 220.dp).fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Developed by ImadLabs", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { TelegramLauncher.open(context) }) {
                Icon(Icons.AutoMirrored.Rounded.Send, null); Text("  Join ImadLabs on Telegram")
            }
            Text(TelegramLauncher.CHANNEL_URL, style = MaterialTheme.typography.bodySmall, color = LocalImadExtras.current.accent,
                modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.RELEASES_PAGE_URL))) }
                catch (_: ActivityNotFoundException) { context.toast("No browser available") }
            }) { Text("Releases and changelog") }
            Spacer(Modifier.height(28.dp))
            ListItem(
                headlineContent = { Text("Open-source licenses") },
                supportingContent = { Text("LibVLC, AndroidX, fonts and other components") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(Routes.LICENSES) },
            )
            Text("Playback engine: LibVLC ${runCatching { LibVLC.version() }.getOrDefault("").ifBlank { "3.x" }}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("© ${Calendar.getInstance().get(Calendar.YEAR)} ImadLabs. All rights reserved.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private data class License(val component: String, val notice: String, val file: String)

private val licenses = listOf(
    License("LibVLC (VideoLAN)", "Playback engine. GNU LGPL 2.1 or later. Source: code.videolan.org/videolan/vlc-android", "LGPL-2.1.txt"),
    License("AndroidX, Jetpack Compose, Media3, Room, DataStore", "Apache License 2.0, © The Android Open Source Project", "Apache-2.0.txt"),
    License("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0, © JetBrains s.r.o.", "Apache-2.0.txt"),
    License("Coil", "Apache License 2.0, © Coil Contributors", "Apache-2.0.txt"),
    License("Material Symbols / Icons", "Apache License 2.0, © Google", "Apache-2.0.txt"),
    License("Plus Jakarta Sans", "SIL Open Font License 1.1, © The Plus Jakarta Sans Project Authors", "OFL-PlusJakartaSans.txt"),
    License("Inter", "SIL Open Font License 1.1, © The Inter Project Authors", "OFL-Inter.txt"),
    License("JetBrains Mono", "SIL Open Font License 1.1, © The JetBrains Mono Project Authors", "OFL-JetBrainsMono.txt"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    var open by remember { mutableStateOf<License?>(null) }
    Scaffold(topBar = { BackTopBar("Open-source licenses", onBack) }) { pad ->
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp)) {
            item {
                Text("iPlayer uses LibVLC as a dynamically linked library under the LGPL. You may replace it with a modified version; " +
                    "see the VideoLAN source link above and the full license text.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(licenses) { l ->
                ListItem(
                    headlineContent = { Text(l.component) },
                    supportingContent = { Text(l.notice) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { open = l },
                )
            }
        }
    }
    open?.let { l ->
        val context = LocalContext.current
        val text by produceState("Loading…", l.file) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open("licenses/${l.file}").bufferedReader().use { it.readText() } }
                    .getOrDefault("License text unavailable.")
            }
        }
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(l.component) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(text, style = MonoStyle) } },
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close") } },
        )
    }
}
