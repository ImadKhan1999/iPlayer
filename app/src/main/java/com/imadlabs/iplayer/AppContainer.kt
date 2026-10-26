package com.imadlabs.iplayer

import android.content.Context
import com.imadlabs.iplayer.data.db.AppDatabase
import com.imadlabs.iplayer.data.media.LibraryRepository
import com.imadlabs.iplayer.data.media.MediaStoreSource
import com.imadlabs.iplayer.data.network.NetworkRepository
import com.imadlabs.iplayer.data.settings.AppStateRepository
import com.imadlabs.iplayer.data.settings.SettingsRepository
import com.imadlabs.iplayer.playback.PlaybackManager
import com.imadlabs.iplayer.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

/** Manual dependency container; one instance per process, created by [IPlayerApp]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext, appScope) }
    val appState: AppStateRepository by lazy { AppStateRepository(appContext) }
    val library: LibraryRepository by lazy { LibraryRepository(MediaStoreSource(appContext), settings.state.map { it.hideVoiceNotes }, database, appScope) }
    val network: NetworkRepository by lazy { NetworkRepository(database) }
    val playback: PlaybackManager by lazy { PlaybackManager(appContext, settings, database, appScope, network) }
    val updates: UpdateRepository by lazy { UpdateRepository(appContext, appState, settings, appScope) }
}
