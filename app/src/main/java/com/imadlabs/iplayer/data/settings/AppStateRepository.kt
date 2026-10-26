package com.imadlabs.iplayer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appStateStore: DataStore<Preferences> by preferencesDataStore(name = "app_state")

data class WelcomeState(val dontShowAgain: Boolean, val timesShown: Int, val lastShownAt: Long)

data class CachedUpdateCheck(val checkedAt: Long, val manifestJson: String?)

/** Non-setting state: first-run welcome bookkeeping and the cached update check. */
class AppStateRepository(context: Context) {
    private val store = context.applicationContext.appStateStore

    private val welcomeDontShow = booleanPreferencesKey("welcome_dont_show")
    private val welcomeCount = intPreferencesKey("welcome_count")
    private val welcomeLast = longPreferencesKey("welcome_last")
    private val updateCheckedAt = longPreferencesKey("update_checked_at")
    private val updateManifest = stringPreferencesKey("update_manifest")
    private val updateSkippedCode = longPreferencesKey("update_skipped_code")
    private val permissionAsked = booleanPreferencesKey("media_permission_asked")

    val welcome: Flow<WelcomeState> = store.data.map {
        WelcomeState(it[welcomeDontShow] ?: false, it[welcomeCount] ?: 0, it[welcomeLast] ?: 0L)
    }

    suspend fun welcomeState(): WelcomeState = welcome.first()

    suspend fun recordWelcomeShown(now: Long) = store.edit {
        it[welcomeCount] = (it[welcomeCount] ?: 0) + 1
        it[welcomeLast] = now
    }

    suspend fun setWelcomeDontShowAgain(value: Boolean) = store.edit { it[welcomeDontShow] = value }

    val lastUpdateCheck: Flow<CachedUpdateCheck> = store.data.map {
        CachedUpdateCheck(it[updateCheckedAt] ?: 0L, it[updateManifest])
    }

    suspend fun saveUpdateCheck(at: Long, manifestJson: String) = store.edit {
        it[updateCheckedAt] = at
        it[updateManifest] = manifestJson
    }

    val skippedVersionCode: Flow<Long> = store.data.map { it[updateSkippedCode] ?: 0L }

    suspend fun skipVersion(code: Long) = store.edit { it[updateSkippedCode] = code }

    val mediaPermissionAsked: Flow<Boolean> = store.data.map { it[permissionAsked] ?: false }

    suspend fun setMediaPermissionAsked() = store.edit { it[permissionAsked] = true }
}
