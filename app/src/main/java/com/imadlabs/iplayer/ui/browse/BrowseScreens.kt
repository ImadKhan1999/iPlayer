package com.imadlabs.iplayer.ui.browse

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.data.db.NetworkProtocol
import com.imadlabs.iplayer.data.db.NetworkSource
import com.imadlabs.iplayer.data.media.MediaFormats
import com.imadlabs.iplayer.data.network.BrowseUpdate
import com.imadlabs.iplayer.data.network.NetworkBrowser
import com.imadlabs.iplayer.data.network.NetworkEntry
import com.imadlabs.iplayer.data.network.NetworkRepository
import com.imadlabs.iplayer.playback.QueueItem
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.Routes
import com.imadlabs.iplayer.ui.components.BackTopBar
import com.imadlabs.iplayer.ui.components.BrandTopBar
import com.imadlabs.iplayer.ui.components.ConfirmDialog
import com.imadlabs.iplayer.ui.components.EmptyState
import com.imadlabs.iplayer.ui.components.OpenUrlDialog
import com.imadlabs.iplayer.ui.components.SectionHeader
import com.imadlabs.iplayer.ui.components.play
import com.imadlabs.iplayer.ui.components.toQueueItem
import com.imadlabs.iplayer.ui.music.SongRow
import com.imadlabs.iplayer.ui.videos.VideoListItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun BrowseScreen(onOpen: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(topBar = { BrandTopBar(onOpen, title = "Browse") }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Folders") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Network") })
            }
            if (tab == 0) LocalFolders(onOpen) else NetworkHome(onOpen)
        }
    }
}

@Composable
private fun LocalFolders(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val folders by container.library.folders.collectAsStateWithLifecycle()
    if (folders.isEmpty()) {
        EmptyState(Icons.Rounded.Folder, "No media folders", "Folders that contain videos or music appear here.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(folders, key = { it.path }) { f ->
            ListItem(
                headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(listOfNotNull(f.videos.size.takeIf { it > 0 }?.let { "$it videos" }, f.audio.size.takeIf { it > 0 }?.let { "$it audio" })
                        .joinToString(" · ") + " · " + f.path, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingContent = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(Routes.folder(f.path)) },
            )
        }
    }
}

@Composable
fun FolderScreen(path: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folders by container.library.folders.collectAsStateWithLifecycle()
    val folder = folders.firstOrNull { it.path == path }
    var confirmHide by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        BackTopBar(path.substringAfterLast('/'), onBack, path) {
            TextButton(onClick = { confirmHide = true }) { Text("Hide") }
        }
    }) { pad ->
        if (folder == null) { EmptyState(Icons.Rounded.Folder, "Folder is empty", "Its media may have been moved or hidden.", Modifier.padding(pad)); return@Scaffold }
        LazyColumn(contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = 24.dp)) {
            if (folder.videos.isNotEmpty()) {
                item { SectionHeader("Videos") }
                val videos = folder.videos.sortedBy { it.displayName.lowercase() }
                itemsIndexed(videos, key = { _, v -> "v${v.id}" }) { i, v ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        VideoListItem(v, null, false, true, onClick = { container.play(context, videos.map { it.toQueueItem() }, i) }, onLongClick = {}) {}
                    }
                }
            }
            if (folder.audio.isNotEmpty()) {
                item { SectionHeader("Audio") }
                val songs = folder.audio.sortedBy { it.displayName.lowercase() }
                itemsIndexed(songs, key = { _, s -> "a${s.id}" }) { i, s ->
                    SongRow(s, true, {}) { container.play(context, songs.map { it.toQueueItem() }, i) }
                }
            }
        }
    }
    if (confirmHide) ConfirmDialog("Hide folder?",
        "Media in this folder will no longer appear in your library. Files are not deleted, and you can show it again in Settings › Library.",
        "Hide", { confirmHide = false }) {
        confirmHide = false
        scope.launch { container.library.exclude(path) }
        onBack()
    }
}

@Composable
private fun NetworkHome(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourcesFlow = remember { container.network.sources }
    val sources by sourcesFlow.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<NetworkSource?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<NetworkSource?>(null) }
    var showUrl by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<NetworkEntry>>(emptyList()) }
    var discovering by remember { mutableStateOf(false) }
    var discoverRun by remember { mutableStateOf(0) }

    if (discoverRun > 0) DisposableEffect(discoverRun) {
        val browser = NetworkBrowser(context) { null }
        discovering = true
        val job = scope.launch {
            withTimeoutOrNull(15_000) {
                browser.discover().collect { u -> if (u is BrowseUpdate.Items) discovered = u.entries }
            }
            discovering = false
        }
        onDispose { job.cancel(); browser.release(); discovering = false }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null); Text(" Add source") }
                TextButton(onClick = { showUrl = true }) { Icon(Icons.Rounded.Link, null); Text(" Stream URL") }
            }
        }
        item { SectionHeader("Saved sources") }
        if (sources.isEmpty()) item {
            Text("Add an SMB share, FTP/SFTP server or NFS export to browse it here. Passwords are encrypted on this device.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(sources, key = { it.id }) { s ->
            ListItem(
                headlineContent = { Text(s.name) },
                supportingContent = { Text("${s.protocol.label} · ${s.host}${s.path}" + (s.username?.let { " · $it" } ?: "")) },
                leadingContent = { Icon(if (s.encryptedPassword != null) Icons.Rounded.Lock else Icons.Rounded.Dns, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { editing = s }) { Icon(Icons.Rounded.Edit, "Edit ${s.name}") }
                        IconButton(onClick = { deleting = s }) { Icon(Icons.Rounded.Delete, "Remove ${s.name}") }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(Routes.network(s.id)) },
            )
        }
        item {
            SectionHeader("On this network") {
                IconButton(onClick = { discoverRun++ }) { Icon(Icons.Rounded.Refresh, "Scan local network") }
            }
        }
        if (discovering) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
        if (discoverRun == 0) item {
            Text("Scan for UPnP/DLNA media servers and Windows shares on your Wi-Fi network.",
                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!discovering && discovered.isEmpty()) item {
            Text("Nothing found. Media servers must be on the same network and allow discovery.",
                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(discovered, key = { it.uri.toString() }) { e ->
            ListItem(
                headlineContent = { Text(e.name) },
                supportingContent = { Text(e.uri.scheme?.uppercase().orEmpty()) },
                leadingContent = { Icon(Icons.Rounded.Router, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onOpen(Routes.network(0, e.uri.toString())) },
            )
        }
    }
    if (adding || editing != null) SourceEditor(editing, onDismiss = { adding = false; editing = null })
    deleting?.let { s ->
        ConfirmDialog("Remove ${s.name}?", "The saved address and credentials will be deleted from this device.", "Remove",
            { deleting = null }) { scope.launch { container.network.delete(s.id) }; deleting = null }
    }
    if (showUrl) OpenUrlDialog(onDismiss = { showUrl = false }) { url ->
        showUrl = false
        container.play(context, listOf(QueueItem(url, Uri.parse(url).lastPathSegment ?: url, isVideo = true)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceEditor(existing: NetworkSource?, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var protocol by remember { mutableStateOf(existing?.protocol ?: NetworkProtocol.Smb) }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.toString() ?: "") }
    var path by remember { mutableStateOf(existing?.path ?: "") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(existing?.domain ?: "") }
    var protoMenu by remember { mutableStateOf(false) }
    val hostValid = host.isNotBlank() && host.none { it.isWhitespace() || it == '/' || it == '@' }
    val portValid = port.isEmpty() || port.toIntOrNull()?.let { it in 1..65535 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add network source" else "Edit network source") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    ExposedDropdownMenuBox(protoMenu, { protoMenu = it }) {
                        OutlinedTextField(protocol.label, {}, readOnly = true, label = { Text("Protocol") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(protoMenu) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                        ExposedDropdownMenu(protoMenu, { protoMenu = false }) {
                            NetworkProtocol.entries.forEach { p ->
                                DropdownMenuItem(text = { Text(p.label) }, onClick = { protocol = p; protoMenu = false })
                            }
                        }
                    }
                }
                item { OutlinedTextField(host, { host = it.trim() }, label = { Text("Server (host name or IP)") }, singleLine = true, isError = host.isNotEmpty() && !hostValid) }
                item { OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port (default ${protocol.defaultPort})") },
                    singleLine = true, isError = !portValid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) }
                item { OutlinedTextField(path, { path = it }, label = { Text("Share / path (optional)") }, singleLine = true) }
                item { OutlinedTextField(name, { name = it }, label = { Text("Display name (optional)") }, singleLine = true) }
                item { OutlinedTextField(user, { user = it }, label = { Text("User name (optional)") }, singleLine = true) }
                item {
                    OutlinedTextField(password, { password = it }, label = {
                        Text(if (existing?.encryptedPassword != null) "Password (leave blank to keep)" else "Password")
                    }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
                if (protocol == NetworkProtocol.Smb) item { OutlinedTextField(domain, { domain = it }, label = { Text("Domain / workgroup (optional)") }, singleLine = true) }
                item {
                    Text("Passwords are encrypted with a key kept in Android's hardware-backed keystore and are never written to logs.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = hostValid && portValid, onClick = {
                scope.launch {
                    container.network.save(existing?.id ?: 0, name, protocol, host, port.toIntOrNull(), path,
                        user, password, domain, keepExistingPassword = password.isEmpty() && user.isNotBlank())
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Browses a saved source ([sourceId] > 0) or a discovered location passed as [path]. */
@Composable
fun NetworkBrowserScreen(sourceId: Long, path: String?, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    var source by remember { mutableStateOf<NetworkSource?>(null) }
    var loaded by remember { mutableStateOf(sourceId == 0L) }
    LaunchedEffect(sourceId) { if (sourceId > 0) { source = container.network.get(sourceId); loaded = true } }
    if (!loaded) return
    val location: Uri = path?.let(Uri::parse) ?: source?.let { NetworkRepository.rootUri(it) } ?: run {
        EmptyState(Icons.Rounded.Dns, "Source not found", "It may have been removed."); return
    }
    var entries by remember(location) { mutableStateOf<List<NetworkEntry>>(emptyList()) }
    var finished by remember(location) { mutableStateOf(false) }
    var authFailed by remember(location) { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    DisposableEffect(location, reload) {
        val creds = source?.let { s -> s.username?.let { it to container.network.password(s).orEmpty() } }
        val browser = NetworkBrowser(context) { creds }
        finished = false; authFailed = false
        val scope = kotlinx.coroutines.MainScope()
        val job = scope.launch {
            val options = container.network.mediaOptionsFor(location)
            val result = withTimeoutOrNull(20_000) {
                browser.browse(location, options).collect { u ->
                    when (u) {
                        is BrowseUpdate.Items -> { entries = u.entries; if (u.finished) finished = true }
                        BrowseUpdate.AuthRequired -> { authFailed = true; finished = true }
                    }
                }
            }
            if (result == null) finished = true
        }
        onDispose { job.cancel(); browser.release() }
    }

    val title = path?.let { Uri.parse(it).lastPathSegment ?: Uri.parse(it).host } ?: source?.name ?: "Network"
    Scaffold(topBar = {
        BackTopBar(title ?: "Network", onBack, location.toString()) {
            IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, "Reload") }
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            if (!finished) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                authFailed -> EmptyState(Icons.Rounded.Lock, "Sign-in failed",
                    "The server needs a valid user name and password. Edit this source in Browse › Network and try again.")
                finished && entries.isEmpty() -> EmptyState(Icons.Rounded.Dns, "Nothing to show",
                    "The location is empty or couldn't be reached. Check the address, that the server is on, and that this device is on the same network.",
                    actionLabel = "Retry", onAction = { reload++ })
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    val playable = entries.filter { !it.isDirectory }
                    items(entries, key = { it.uri.toString() }) { e ->
                        val isAudio = MediaFormats.isAudio(e.name) || MediaFormats.isAudio(e.uri.lastPathSegment.orEmpty())
                        ListItem(
                            headlineContent = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                Icon(when { e.isDirectory -> Icons.Rounded.Folder; isAudio -> Icons.Rounded.MusicNote; else -> Icons.Rounded.Movie },
                                    null, tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                if (e.isDirectory) onOpen(Routes.network(sourceId, e.uri.toString()))
                                else {
                                    val queue = playable.map { p ->
                                        val audio = MediaFormats.isAudio(p.name) || MediaFormats.isAudio(p.uri.lastPathSegment.orEmpty())
                                        QueueItem(p.uri.toString(), p.name.substringBeforeLast('.').ifBlank { p.name }, isVideo = !audio)
                                    }
                                    container.play(context, queue, playable.indexOf(e).coerceAtLeast(0))
                                }
                            },
                        )
                    }
                    if (!finished) item { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}
