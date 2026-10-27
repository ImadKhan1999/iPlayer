package com.imadlabs.iplayer.ui.startup

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.BuildConfig
import com.imadlabs.iplayer.R
import com.imadlabs.iplayer.startup.DialogCoordinator
import com.imadlabs.iplayer.startup.StartupDialog
import com.imadlabs.iplayer.startup.TelegramLauncher
import com.imadlabs.iplayer.startup.WelcomePolicy
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.components.toast
import com.imadlabs.iplayer.update.ApkUpdater
import com.imadlabs.iplayer.update.DownloadStatus
import com.imadlabs.iplayer.update.UpdateManifest
import com.imadlabs.iplayer.update.UpdateState
import com.imadlabs.iplayer.util.Formatters
import kotlinx.coroutines.launch

/** Process-wide flag so the welcome dialog appears at most once per app session. */
private object SessionFlags { var welcomeEvaluated = false; var welcomePending = false }

@Composable
fun StartupDialogsHost() {
    val container = LocalContainer.current
    val update by container.updates.state.collectAsStateWithLifecycle()
    val player by container.playback.state.collectAsStateWithLifecycle()
    var welcomePending by remember { mutableStateOf(SessionFlags.welcomePending) }
    var dismissed by rememberSaveable { mutableStateOf(setOf<StartupDialog>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!SessionFlags.welcomeEvaluated) {
            SessionFlags.welcomeEvaluated = true
            val show = WelcomePolicy.shouldShow(container.appState.welcomeState(), System.currentTimeMillis(),
                launchedForPlayback = container.playback.state.value.playWhenReady)
            if (show) container.appState.recordWelcomeShown(System.currentTimeMillis())
            SessionFlags.welcomePending = show
            welcomePending = show
        }
    }

    // A fresh check (each time the app is opened) brings a "Later"-dismissed update back.
    val checkedAt = (update as? UpdateState.Available)?.checkedAt
    LaunchedEffect(checkedAt) {
        if (checkedAt != null) dismissed = dismissed - StartupDialog.OptionalUpdate - StartupDialog.MandatoryUpdate
    }

    val playbackActive = player.playWhenReady && player.item?.isVideo == true
    when (DialogCoordinator.next(welcomePending, update, dismissed, playbackActive)) {
        StartupDialog.Welcome -> WelcomeDialog { dontShow ->
            scope.launch { container.appState.setWelcomeDontShowAgain(dontShow) }
            SessionFlags.welcomePending = false
            welcomePending = false
            dismissed = dismissed + StartupDialog.Welcome
        }
        StartupDialog.MandatoryUpdate, StartupDialog.OptionalUpdate -> {
            val a = update as UpdateState.Available
            UpdateDialog(a.release, a.mandatory,
                onLater = { container.updates.dismissForSession(); dismissed = dismissed + StartupDialog.OptionalUpdate + StartupDialog.MandatoryUpdate },
                onSkip = { scope.launch { container.updates.skip(a.release) }; dismissed = dismissed + StartupDialog.OptionalUpdate })
        }
        null -> Unit
    }
}

@Composable
fun WelcomeDialog(onClose: (dontShowAgain: Boolean) -> Unit) {
    val context = LocalContext.current
    var dontShow by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onClose(dontShow) },
        icon = {
            Image(painterResource(R.drawable.iplayer_app_icon), contentDescription = "iPlayer",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
        },
        title = { Text("Welcome to iPlayer", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enjoy a powerful media experience from ImadLabs.", style = MaterialTheme.typography.bodyLarge)
                Text("Follow ImadLabs on Telegram for application news, new releases, announcements, and updates.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { TelegramLauncher.open(context) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Join ImadLabs on Telegram")
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Checkbox) { dontShow = !dontShow }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontShow, onCheckedChange = { dontShow = it })
                    Text("Don't show this again", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onClose(dontShow) }) { Text("Continue") } },
    )
}

@Composable
fun UpdateDialog(
    release: UpdateManifest.Release,
    mandatory: Boolean,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val updater = remember { ApkUpdater(context) }
    var status by remember { mutableStateOf<DownloadStatus?>(null) }
    var awaitingInstallPermission by remember { mutableStateOf(false) }
    val apk = remember(release) { container.updates.apkFor(release) }

    fun openReleasePage() {
        val url = release.releasePage ?: BuildConfig.RELEASES_PAGE_URL
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { context.toast("No browser available to open $url") }
    }

    fun startUpdate() {
        if (!BuildConfig.SELF_UPDATE) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            try { context.startActivity(market) } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
            }
            return
        }
        if (apk == null) { openReleasePage(); return }
        if (!updater.canRequestInstall()) {
            context.toast("Allow iPlayer to install updates, then come back to continue.")
            awaitingInstallPermission = true
            context.startActivity(updater.installPermissionIntent())
            return
        }
        scope.launch {
            updater.download(apk, release.versionCode).collect { s ->
                status = s
                if (s is DownloadStatus.Ready) updater.install(s.file)
            }
        }
    }

    // Continue the update automatically once the user returns having allowed installs.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingInstallPermission && updater.canRequestInstall()) {
            awaitingInstallPermission = false
            startUpdate()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!mandatory) onLater() },
        properties = DialogProperties(dismissOnClickOutside = !mandatory, dismissOnBackPress = !mandatory),
        title = { Text(if (mandatory) "Update required" else "Update available") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("iPlayer ${release.versionName}" + (release.releaseDate?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium)
                if (mandatory) Text("This version of iPlayer is no longer supported. Please update to keep using all features.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (release.changelog.isNotEmpty()) {
                    Text("What's new", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    release.changelog.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
                apk?.let { Text("Download size: ${Formatters.fileSize(it.sizeBytes)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                when (val s = status) {
                    is DownloadStatus.Progress -> {
                        LinearProgressIndicator(progress = { if (s.total > 0) s.downloaded.toFloat() / s.total else 0f }, Modifier.fillMaxWidth())
                        Text("${Formatters.fileSize(s.downloaded)} of ${Formatters.fileSize(s.total)}", style = MaterialTheme.typography.bodySmall)
                    }
                    DownloadStatus.Verifying -> Text("Verifying checksum and signature…", style = MaterialTheme.typography.bodySmall)
                    is DownloadStatus.Ready -> Text("Verified. Confirm the installation in the system dialog.", style = MaterialTheme.typography.bodySmall)
                    is DownloadStatus.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    null -> Unit
                }
                TextButton(onClick = ::openReleasePage, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Open release page")
                }
            }
        },
        confirmButton = {
            val busy = status is DownloadStatus.Progress || status == DownloadStatus.Verifying
            Button(onClick = ::startUpdate, enabled = !busy) { Text(if (status is DownloadStatus.Failed) "Retry" else "Update") }
        },
        dismissButton = {
            if (!mandatory) Row {
                TextButton(onClick = onSkip) { Text("Skip version") }
                TextButton(onClick = onLater) { Text("Later") }
            } else TextButton(onClick = onLater) { Text("Not now") }
        },
    )
}
