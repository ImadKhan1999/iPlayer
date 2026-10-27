package com.imadlabs.iplayer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.imadlabs.iplayer.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaAccess { None, Partial, Full }

/** Storage access state, re-evaluated whenever the app returns to the foreground. */
object MediaPermissions {
    private val _access = MutableStateFlow(MediaAccess.None)
    val access: StateFlow<MediaAccess> = _access.asStateFlow()

    val requested: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun refresh(context: Context): MediaAccess {
        fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val access = when {
            Build.VERSION.SDK_INT >= 33 -> when {
                has(Manifest.permission.READ_MEDIA_VIDEO) && has(Manifest.permission.READ_MEDIA_AUDIO) -> MediaAccess.Full
                has(Manifest.permission.READ_MEDIA_VIDEO) || has(Manifest.permission.READ_MEDIA_AUDIO) -> MediaAccess.Partial
                Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial
                else -> MediaAccess.None
            }
            else -> if (has(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.Full else MediaAccess.None
        }
        _access.value = access
        return access
    }

    /**
     * Optional "All files access" (direct builds only): lets iPlayer read subtitle and lyric files that
     * sit next to media, which scoped storage otherwise hides. Never required for playback.
     */
    fun allFilesSupported(): Boolean = BuildConfig.SELF_UPDATE && Build.VERSION.SDK_INT >= 30
    fun hasAllFiles(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
    fun allFilesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}

/** Keeps [MediaPermissions] current and starts the library once access exists. */
@Composable
fun PermissionGate() {
    val context = LocalContext.current
    val container = LocalContainer.current
    LaunchedEffect(Unit) {
        if (MediaPermissions.refresh(context) != MediaAccess.None) container.library.start()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val before = MediaPermissions.access.value
        val now = MediaPermissions.refresh(context)
        if (now != MediaAccess.None && now != before) container.library.start()
    }
}

/** Returns a function that asks for media access (the system shows its own dialog). */
@Composable
fun rememberMediaPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val container = LocalContainer.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (MediaPermissions.refresh(context) != MediaAccess.None) container.library.start()
    }
    return { launcher.launch(MediaPermissions.requested) }
}
