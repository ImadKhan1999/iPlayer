package com.imadlabs.iplayer.ui.music

import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.media.AudioItem
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.BackTopBar
import com.imadlabs.iplayer.ui.components.EmptyState
import com.imadlabs.iplayer.ui.components.MediaArtwork
import com.imadlabs.iplayer.ui.components.MediaThumb
import com.imadlabs.iplayer.ui.components.SectionHeader
import com.imadlabs.iplayer.ui.theme.MonoStyle
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AlbumScreen(id: Long, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val albums by container.library.albums.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.id == id }
    Scaffold(topBar = { BackTopBar(album?.name ?: "Album", onBack, album?.artist) }) { pad ->
        if (album == null) { EmptyState(Icons.Rounded.Album, "Album not found", "It may have been removed.", Modifier.padding(pad)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            SongList(album.tracks, "", onOpen, showArt = false, header = {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    MediaArtwork(album.artUri?.let { MediaThumb(it, false, album.id) }, Modifier.size(132.dp), corner = 14.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(album.name, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpen(Routes.artist(album.artist)) })
                        Text(listOfNotNull(album.year.takeIf { it > 0 }?.toString(), "${album.tracks.size} songs").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            })
        }
    }
}

@Composable
fun ArtistScreen(name: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val artists by container.library.artists.collectAsStateWithLifecycle()
    val artist = artists.firstOrNull { it.name == name }
    Scaffold(topBar = { BackTopBar(name, onBack, artist?.let { "${it.albums.size} albums · ${it.tracks.size} songs" }) }) { pad ->
        if (artist == null) { EmptyState(Icons.Rounded.Album, "Artist not found", "It may have been removed.", Modifier.padding(pad)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            SongList(artist.tracks, "", onOpen, header = {
                if (artist.albums.isNotEmpty()) Column {
                    SectionHeader("Albums")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(artist.albums, key = { it.id }) { a ->
                            Column(Modifier.width(120.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(Routes.album(a.id)) }) {
                                MediaArtwork(a.artUri?.let { MediaThumb(it, false, a.id) }, Modifier.size(120.dp))
                                Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    SectionHeader("Songs")
                }
            })
        }
    }
}

@Composable
fun GenreScreen(name: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val genres by container.library.genres.collectAsStateWithLifecycle()
    val genre = genres.firstOrNull { it.name == name }
    Scaffold(topBar = { BackTopBar(name, onBack, genre?.let { "${it.tracks.size} songs" }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            SongList(genre?.tracks.orEmpty(), "No songs in this genre", {}, showShuffle = true)
        }
    }
}

/** Tag and stream details. Values the platform can't read are left out rather than guessed. */
@Composable
fun AudioDetailsDialog(s: AudioItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val technical by produceState<List<Pair<String, String>>>(emptyList(), s.uri) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, s.uri)
                buildList {
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { add("Format" to it) }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { add("Bitrate" to Formatters.bitrate(it)) }
                    if (Build.VERSION.SDK_INT >= 31) {
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let { add("Sample rate" to Formatters.sampleRate(it)) }
                        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.takeIf { it > 0 }?.let { add("Bit depth" to "$it-bit") }
                    }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { add("Composer" to it) }
                    if (r.embeddedPicture != null) add("Cover art" to "Embedded")
                }
            } catch (e: Exception) {
                listOf("Stream details" to "Not readable by Android's parser; the player's Info panel shows the engine's view")
            } finally {
                runCatching { r.release() }
            }
        }
    }
    val tags = buildList {
        add("Title" to s.title); add("Artist" to s.artist); add("Album" to s.album)
        s.albumArtist?.let { add("Album artist" to it) }
        s.genre?.let { add("Genre" to it) }
        if (s.year > 0) add("Year" to s.year.toString())
        if (s.trackNumber > 0) add("Track" to s.trackNumber.toString())
        if (s.discNumber > 0) add("Disc" to s.discNumber.toString())
        add("Duration" to Formatters.duration(s.durationMs))
        add("Size" to Formatters.fileSize(s.sizeBytes))
        add("File" to s.displayName)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (tags + technical).forEach { (k, v) ->
                    Row {
                        Text(k, Modifier.width(110.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MonoStyle)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
