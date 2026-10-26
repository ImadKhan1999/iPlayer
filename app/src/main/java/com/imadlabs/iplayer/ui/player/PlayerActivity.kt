package com.imadlabs.iplayer.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.imadlabs.iplayer.AppContainer
import com.imadlabs.iplayer.IPlayerApp
import com.imadlabs.iplayer.data.settings.OrientationPref
import com.imadlabs.iplayer.playback.PlayerEvent
import com.imadlabs.iplayer.playback.PlayerState
import com.imadlabs.iplayer.ui.LocalContainer
import com.imadlabs.iplayer.ui.MainActivity
import com.imadlabs.iplayer.ui.components.queueItemForUri
import com.imadlabs.iplayer.ui.theme.IPlayerTheme
import com.imadlabs.iplayer.util.Diagnostics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Full-screen video player. The engine lives in [com.imadlabs.iplayer.playback.PlaybackManager];
 * this activity only owns the video surface, window state (immersive, brightness, orientation)
 * and picture-in-picture.
 */
class PlayerActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as IPlayerApp).container
    private lateinit var videoLayout: VLCVideoLayout
    private var inPip by mutableStateOf(false)
    private var surfaceAttached = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pm = container.playback
            when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                CONTROL_TOGGLE -> pm.togglePlayPause()
                CONTROL_BACK -> pm.seekBy(-container.settings.current.seekIntervalSec * 1000L)
                CONTROL_FORWARD -> pm.seekBy(container.settings.current.seekIntervalSec * 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && !handleIntent(intent)) { finish(); return }
        if (container.playback.state.value.item == null) { finish(); return }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        fullscreen = savedInstanceState?.getBoolean(KEY_FULLSCREEN) ?: false
        applySystemBars()
        videoLayout = VLCVideoLayout(this)
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP_CONTROL), ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalContainer provides container) {
                IPlayerTheme(com.imadlabs.iplayer.data.settings.ThemeMode.Dark, settings.accent, 1f, settings.animations) {
                    PlayerScreen(
                        videoLayout = videoLayout,
                        inPip = inPip,
                        onClose = { closePlayer() },
                        onEnterPip = { enterPip() },
                        pipSupported = pipSupported(),
                        window = window,
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    container.playback.state.map { Triple(it.isPlaying, it.videoWidth to it.videoHeight, it.item?.isVideo) }
                        .distinctUntilChanged().collect { (playing, size, isVideo) ->
                            if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            applyOrientation(size.first, size.second)
                            updatePipParams(container.playback.state.value)
                            if (isVideo == false) openAudioUi()
                        }
                }
                launch {
                    container.playback.events.collect { if (it is PlayerEvent.CloseRequested) closePlayer() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Starts playback for VIEW/SEND intents. Returns false when there is nothing to play. */
    private fun handleIntent(intent: Intent?): Boolean {
        intent ?: return false
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let { runCatching { Uri.parse(it) }.getOrNull() }
            else -> null
        }
        if (uri == null) return intent.action == ACTION_RESUME || container.playback.state.value.item != null
        if (uri.scheme == "content") runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val item = queueItemForUri(this, uri, intent.type)
        Diagnostics.log("Player", "Opened from ${intent.action} (${uri.scheme})")
        container.playback.playQueue(listOf(item))
        return true
    }

    private fun openAudioUi() {
        startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_NOW_PLAYING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    override fun onStart() {
        super.onStart()
        attachSurface()
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        val keepAudio = container.settings.current.backgroundVideoAudio
        if (!keepAudio) container.playback.pause()
        detachSurface(keepAudio)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(pipReceiver) }
        if (::videoLayout.isInitialized) detachSurface(keepAudio = container.settings.current.backgroundVideoAudio)
    }

    private fun attachSurface() {
        if (!::videoLayout.isInitialized || surfaceAttached) return
        container.playback.attachVideo(videoLayout, container.settings.current.useTextureView)
        surfaceAttached = true
    }

    private fun detachSurface(keepAudio: Boolean) {
        if (!surfaceAttached) return
        container.playback.detachVideo(videoLayout, keepAudio)
        surfaceAttached = false
    }

    private fun closePlayer() {
        container.playback.stop()
        finish()
    }

    /** Whether the user switched to full screen (system bars hidden). Off by default. */
    var fullscreen by mutableStateOf(false)
        private set

    fun toggleFullscreen() {
        fullscreen = !fullscreen
        applySystemBars()
    }

    private fun applySystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_FULLSCREEN, fullscreen)
    }

    private var userOrientationOverride: Int? = null

    /** Toggles between landscape and portrait from the player controls. */
    fun toggleOrientation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        userOrientationOverride = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        requestedOrientation = userOrientationOverride!!
    }

    private fun applyOrientation(w: Int, h: Int) {
        userOrientationOverride?.let { requestedOrientation = it; return }
        requestedOrientation = when (container.settings.current.orientation) {
            OrientationPref.Sensor -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            OrientationPref.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationPref.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationPref.Auto -> when {
                w <= 0 || h <= 0 -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                w >= h -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }

    // ---------------------------------------------------------------- picture-in-picture

    private fun pipSupported(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(s: PlayerState): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (s.videoWidth > 0 && s.videoHeight > 0) {
            val ratio = s.videoWidth.toFloat() / s.videoHeight
            val clamped = ratio.coerceIn(1 / 2.39f, 2.39f)
            builder.setAspectRatio(if (clamped == ratio) Rational(s.videoWidth, s.videoHeight) else Rational((clamped * 1000).toInt(), 1000))
        }
        val playing = s.playWhenReady
        builder.setActions(listOf(
            remoteAction(CONTROL_BACK, android.R.drawable.ic_media_rew, "Back"),
            remoteAction(CONTROL_TOGGLE, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (playing) "Pause" else "Play"),
            remoteAction(CONTROL_FORWARD, android.R.drawable.ic_media_ff, "Forward"),
        ))
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(container.settings.current.autoPip && playing)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun remoteAction(control: Int, icon: Int, title: String): RemoteAction {
        val pi = PendingIntent.getBroadcast(this, control,
            Intent(ACTION_PIP_CONTROL).setPackage(packageName).putExtra(EXTRA_CONTROL, control),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pi)
    }

    private fun updatePipParams(s: PlayerState) {
        if (!pipSupported()) return
        runCatching { setPictureInPictureParams(pipParams(s)) }
    }

    private fun enterPip() {
        if (!pipSupported()) return
        runCatching { enterPictureInPictureMode(pipParams(container.playback.state.value)) }
            .onFailure { Diagnostics.log("Player", "Picture-in-picture unavailable", it) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters PiP automatically via setAutoEnterEnabled.
        val s = container.playback.state.value
        if (Build.VERSION.SDK_INT < 31 && container.settings.current.autoPip && s.playWhenReady && s.hasVideo) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    companion object {
        private const val KEY_FULLSCREEN = "fullscreen"
        const val ACTION_RESUME = "com.imadlabs.iplayer.RESUME_PLAYER"
        private const val ACTION_PIP_CONTROL = "com.imadlabs.iplayer.PIP_CONTROL"
        private const val EXTRA_CONTROL = "control"
        private const val CONTROL_TOGGLE = 1
        private const val CONTROL_BACK = 2
        private const val CONTROL_FORWARD = 3
    }
}
