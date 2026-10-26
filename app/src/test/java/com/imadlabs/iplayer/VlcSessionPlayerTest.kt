package com.imadlabs.iplayer

import android.app.Application
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.imadlabs.iplayer.data.db.AppDatabase
import com.imadlabs.iplayer.data.settings.SettingsRepository
import com.imadlabs.iplayer.playback.CredentialsProvider
import com.imadlabs.iplayer.playback.PlaybackManager
import com.imadlabs.iplayer.playback.VlcSessionPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Builds the session player the way PlaybackService does (Main.immediate scope). */
@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class VlcSessionPlayerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test fun constructsWithIdleStateAndCommands() {
        val credentials = object : CredentialsProvider {
            override suspend fun mediaOptionsFor(uri: Uri): List<String> = emptyList()
            override suspend fun credentialsFor(uri: Uri): Pair<String, String>? = null
        }
        val manager = PlaybackManager(context, SettingsRepository(context, scope), db, scope, credentials)
        val player = VlcSessionPlayer(context, manager, scope)

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertTrue(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        player.release()
    }
}
