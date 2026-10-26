package com.imadlabs.iplayer.ui

import android.content.Intent
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
        val container = (application as IPlayerApp).container
        handleIntent(intent)
        container.updates.checkOnStartup()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_NOW_PLAYING) pendingRoute.value = Routes.NOW_PLAYING
    }

    companion object {
        const val ACTION_NOW_PLAYING = "com.imadlabs.iplayer.NOW_PLAYING"
    }
}
