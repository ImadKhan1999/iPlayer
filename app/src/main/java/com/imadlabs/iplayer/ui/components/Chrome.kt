package com.imadlabs.iplayer.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imadlabs.iplayer.AppContainer
import com.imadlabs.iplayer.R
import com.imadlabs.iplayer.data.media.MediaFormats
import com.imadlabs.iplayer.playback.QueueItem
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.theme.LocalImadExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandTopBar(
    onOpen: (String) -> Unit,
    title: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.iplayer_app_icon), contentDescription = null,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title ?: "iPlayer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    if (title == null) Text("by ImadLabs", style = MaterialTheme.typography.labelSmall,
                        color = LocalImadExtras.current.accent)
                }
            }
        },
        actions = {
            actions()
            IconButton(onClick = { onOpen(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

private val streamSchemes = setOf("http", "https", "rtsp", "rtmp", "rtp", "udp", "mms", "mmsh", "smb", "ftp", "ftps", "sftp", "nfs")

fun validateStreamUrl(text: String): String? {
    val uri = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return "Enter a valid address"
    val scheme = uri.scheme?.lowercase() ?: return "Include the protocol, for example https://"
    if (scheme !in streamSchemes) return "Unsupported protocol: $scheme"
    if (uri.host.isNullOrBlank() && scheme != "udp" && scheme != "rtp") return "The address has no host name"
    return null
}

@Composable
fun OpenUrlDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val error = if (text.isBlank()) null else validateStreamUrl(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open network stream") },
        text = {
            Column {
                Text("Direct media links, HLS (.m3u8), DASH (.mpd), RTSP, SMB, FTP and other protocols supported by the engine.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(text, { text = it }, label = { Text("URL") }, singleLine = true,
                    isError = error != null, supportingText = { error?.let { Text(it) } })
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank() && error == null, onClick = { onOpen(text.trim()) }) { Text("Play") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Queue item for a document/stream URI opened from outside the library. */
fun queueItemForUri(context: Context, uri: Uri, mime: String?): QueueItem {
    val name = when (uri.scheme) {
        "content" -> runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        else -> null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
    val isAudio = mime?.startsWith("audio/") == true || (mime == null && MediaFormats.isAudio(name))
    return QueueItem(uri.toString(), name.substringBeforeLast('.').ifBlank { name }, isVideo = !isAudio,
        artworkUri = if (isAudio) uri.toString() else null)
}

fun AppContainer.openExternal(context: Context, uri: Uri, mime: String?) {
    if (uri.scheme == "content") runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    play(context, listOf(queueItemForUri(context, uri, mime)))
}
