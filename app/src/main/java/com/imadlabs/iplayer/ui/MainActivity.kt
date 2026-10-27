package com.imadlabs.iplayer.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imadlabs.iplayer.IPlayerApp
import com.imadlabs.iplayer.ui.theme.IPlayerTheme

class MainActivity : ComponentActivity() {
    /** Navigation request coming from an intent (e.g. the media notification). */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        val container = (application as IPlayerApp).container
        handleIntent(intent)
        container.playback.restoreQueue()
        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalContainer provides container) {
                IPlayerTheme(settings.themeMode, settings.accent, animations = settings.animations) {
                    AppShell(pendingRoute.value) { pendingRoute.value = null }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as IPlayerApp).container.updates.checkOnOpen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Some OEM builds cap apps at 60 Hz unless they ask for more. Request the fastest mode at the
     * current resolution so scrolling and transitions use the panel's full refresh rate.
     */
    private fun requestHighestRefreshRate() {
        val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else null)
            ?: @Suppress("DEPRECATION") windowManager.defaultDisplay
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        // Some builds only honour the refresh-rate hint, so set both.
        window.attributes = window.attributes.also {
            it.preferredDisplayModeId = best.modeId
            it.preferredRefreshRate = best.refreshRate
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_NOW_PLAYING) pendingRoute.value = Routes.NOW_PLAYING
    }

    companion object {
        const val ACTION_NOW_PLAYING = "com.imadlabs.iplayer.NOW_PLAYING"
    }
}
