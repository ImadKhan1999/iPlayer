package com.imadlabs.iplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.imadlabs.iplayer.ui.theme.BadgeStyle
import com.imadlabs.iplayer.ui.theme.ImadColors
import com.imadlabs.iplayer.ui.theme.LocalImadExtras

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0x3300F5A0), Color(0x220066FF))))
                .border(1.dp, LocalImadExtras.current.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Brand progress bar: #00F5D4 → #0066FF fill on a slate track. */
@Composable
fun BrandProgress(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height))
        .background(LocalImadExtras.current.border)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(ImadColors.ProgressGradient))
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, background: Color = Color(0xCC070B14), color: Color = Color.White) {
    Text(
        text, style = BadgeStyle, color = color, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/** Thumbnail for a local video or audio item, with a branded placeholder while loading or missing. */
@Composable
fun MediaArtwork(
    thumb: MediaThumb?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    showThumbnail: Boolean = true,
) {
    val shape = RoundedCornerShape(corner)
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHighest))),
            contentAlignment = Alignment.Center) {
            Icon(if (thumb?.isVideo != false) Icons.Rounded.Movie else Icons.Rounded.Audiotrack, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
        }
    }
    // The placeholder is drawn underneath and the image on top once decoded. AsyncImage avoids
    // the per-item subcomposition that SubcomposeAsyncImage costs in long lists.
    Box(modifier.clip(shape)) {
        placeholder()
        if (thumb != null && showThumbnail) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(thumb) { ImageRequest.Builder(context).data(thumb).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ListRowText(title: String, subtitle: String?, modifier: Modifier = Modifier, trailing: String? = null) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank() || trailing != null) {
            Row {
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (trailing != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "",
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
