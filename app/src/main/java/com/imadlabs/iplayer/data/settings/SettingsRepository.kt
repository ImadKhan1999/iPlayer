package com.imadlabs.iplayer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.imadlabs.iplayer.ui.theme.AccentColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists [AppSettings] in DataStore. Every setting shown in the UI is read by the code that uses it. */
class SettingsRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.settingsStore

    val flow: Flow<AppSettings> = store.data.map { it.toSettings() }

    /** Hot settings snapshot for UI. Code that must not act on defaults calls [snapshot] instead. */
    val state: StateFlow<AppSettings> = flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val current: AppSettings get() = state.value

    /** Reads the persisted settings, suspending until DataStore has loaded them. */
    suspend fun snapshot(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    suspend fun resetGestures() = update {
        val d = AppSettings()
        it.copy(
            brightnessGesture = d.brightnessGesture, volumeGesture = d.volumeGesture,
            seekGesture = d.seekGesture, doubleTapSeek = d.doubleTapSeek, pinchZoom = d.pinchZoom,
            longPressSpeed = d.longPressSpeed, longPressSpeedValue = d.longPressSpeedValue,
            twoFingerLock = d.twoFingerLock,
        )
    }

    suspend fun resetAll() = store.edit { it.clear() }

    private object K {
        val defaultSpeed = floatPreferencesKey("default_speed")
        val resetSpeed = booleanPreferencesKey("reset_speed")
        val seekInterval = intPreferencesKey("seek_interval")
        val autoPlayNext = booleanPreferencesKey("auto_play_next")
        val resume = booleanPreferencesKey("resume")
        val endAction = stringPreferencesKey("end_action")
        val decoder = stringPreferencesKey("decoder")
        val decoderFallback = booleanPreferencesKey("decoder_fallback")
        val autoPip = booleanPreferencesKey("auto_pip")
        val bgVideoAudio = booleanPreferencesKey("bg_video_audio")
        val aspect = stringPreferencesKey("aspect")
        val orientation = stringPreferencesKey("orientation")
        val gBrightness = booleanPreferencesKey("g_brightness")
        val gVolume = booleanPreferencesKey("g_volume")
        val gSeek = booleanPreferencesKey("g_seek")
        val gDoubleTap = booleanPreferencesKey("g_double_tap")
        val gPinch = booleanPreferencesKey("g_pinch")
        val gLongPress = booleanPreferencesKey("g_long_press")
        val gLongPressSpeed = floatPreferencesKey("g_long_press_speed")
        val gTwoFingerLock = booleanPreferencesKey("g_two_finger_lock")
        val controlsTimeout = intPreferencesKey("controls_timeout")
        val remainingTime = booleanPreferencesKey("remaining_time")
        val textureView = booleanPreferencesKey("texture_view")
        val seekPreview = booleanPreferencesKey("seek_preview")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqPreset = intPreferencesKey("eq_preset")
        val eqPreamp = floatPreferencesKey("eq_preamp")
        val eqBands = stringPreferencesKey("eq_bands")
        val eqCustomName = stringPreferencesKey("eq_custom_name")
        val audioDelay = longPreferencesKey("audio_delay")
        val replayGain = stringPreferencesKey("replay_gain")
        val replayGainPreamp = floatPreferencesKey("replay_gain_preamp")
        val normalizer = booleanPreferencesKey("normalizer")
        val stereoWiden = booleanPreferencesKey("stereo_widen")
        val stereoMode = stringPreferencesKey("stereo_mode")
        val pauseOnDisconnect = booleanPreferencesKey("pause_on_disconnect")
        val resumeOnFocus = booleanPreferencesKey("resume_on_focus")
        val passthrough = booleanPreferencesKey("passthrough")
        val subEnabled = booleanPreferencesKey("sub_enabled")
        val subLang = stringPreferencesKey("sub_lang")
        val audioLang = stringPreferencesKey("audio_lang")
        val subSize = stringPreferencesKey("sub_size")
        val subColor = stringPreferencesKey("sub_color")
        val subBold = booleanPreferencesKey("sub_bold")
        val subOutline = stringPreferencesKey("sub_outline")
        val subShadow = booleanPreferencesKey("sub_shadow")
        val subBg = intPreferencesKey("sub_bg")
        val subMargin = intPreferencesKey("sub_margin")
        val subEncoding = stringPreferencesKey("sub_encoding")
        val subAuto = booleanPreferencesKey("sub_auto")
        val videoLayout = stringPreferencesKey("video_layout")
        val videoSort = stringPreferencesKey("video_sort")
        val musicSort = stringPreferencesKey("music_sort")
        val thumbnails = booleanPreferencesKey("thumbnails")
        val groupEpisodes = booleanPreferencesKey("group_episodes")
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val animations = booleanPreferencesKey("animations")
        val density = stringPreferencesKey("density")
        val history = booleanPreferencesKey("history")
        val networkCaching = intPreferencesKey("network_caching")
        val autoUpdate = booleanPreferencesKey("auto_update")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            defaultSpeed = this[K.defaultSpeed] ?: d.defaultSpeed,
            resetSpeedOnNewMedia = this[K.resetSpeed] ?: d.resetSpeedOnNewMedia,
            seekIntervalSec = this[K.seekInterval] ?: d.seekIntervalSec,
            autoPlayNext = this[K.autoPlayNext] ?: d.autoPlayNext,
            resumePlayback = this[K.resume] ?: d.resumePlayback,
            endAction = enum(K.endAction, d.endAction),
            decoderMode = enum(K.decoder, d.decoderMode),
            decoderFallback = this[K.decoderFallback] ?: d.decoderFallback,
            autoPip = this[K.autoPip] ?: d.autoPip,
            backgroundVideoAudio = this[K.bgVideoAudio] ?: d.backgroundVideoAudio,
            defaultAspect = enum(K.aspect, d.defaultAspect),
            orientation = enum(K.orientation, d.orientation),
            brightnessGesture = this[K.gBrightness] ?: d.brightnessGesture,
            volumeGesture = this[K.gVolume] ?: d.volumeGesture,
            seekGesture = this[K.gSeek] ?: d.seekGesture,
            doubleTapSeek = this[K.gDoubleTap] ?: d.doubleTapSeek,
            pinchZoom = this[K.gPinch] ?: d.pinchZoom,
            longPressSpeed = this[K.gLongPress] ?: d.longPressSpeed,
            longPressSpeedValue = this[K.gLongPressSpeed] ?: d.longPressSpeedValue,
            twoFingerLock = this[K.gTwoFingerLock] ?: d.twoFingerLock,
            controlsTimeoutSec = this[K.controlsTimeout] ?: d.controlsTimeoutSec,
            showRemainingTime = this[K.remainingTime] ?: d.showRemainingTime,
            useTextureView = this[K.textureView] ?: d.useTextureView,
            seekPreview = this[K.seekPreview] ?: d.seekPreview,
            equalizer = EqualizerSettings(
                enabled = this[K.eqEnabled] ?: false,
                presetIndex = this[K.eqPreset] ?: 0,
                preamp = this[K.eqPreamp] ?: 12f,
                bands = this[K.eqBands]?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: emptyList(),
                customName = this[K.eqCustomName],
            ),
            audioDelayMs = this[K.audioDelay] ?: d.audioDelayMs,
            replayGain = enum(K.replayGain, d.replayGain),
            replayGainPreamp = this[K.replayGainPreamp] ?: d.replayGainPreamp,
            volumeNormalizer = this[K.normalizer] ?: d.volumeNormalizer,
            stereoWiden = this[K.stereoWiden] ?: d.stereoWiden,
            stereoMode = enum(K.stereoMode, d.stereoMode),
            pauseOnHeadsetDisconnect = this[K.pauseOnDisconnect] ?: d.pauseOnHeadsetDisconnect,
            resumeOnFocusGain = this[K.resumeOnFocus] ?: d.resumeOnFocusGain,
            digitalPassthrough = this[K.passthrough] ?: d.digitalPassthrough,
            subtitlesEnabled = this[K.subEnabled] ?: d.subtitlesEnabled,
            preferredSubtitleLanguage = this[K.subLang] ?: d.preferredSubtitleLanguage,
            preferredAudioLanguage = this[K.audioLang] ?: d.preferredAudioLanguage,
            subtitleSize = enum(K.subSize, d.subtitleSize),
            subtitleColor = enum(K.subColor, d.subtitleColor),
            subtitleBold = this[K.subBold] ?: d.subtitleBold,
            subtitleOutline = enum(K.subOutline, d.subtitleOutline),
            subtitleShadow = this[K.subShadow] ?: d.subtitleShadow,
            subtitleBackgroundOpacity = this[K.subBg] ?: d.subtitleBackgroundOpacity,
            subtitleBottomMargin = this[K.subMargin] ?: d.subtitleBottomMargin,
            subtitleEncoding = this[K.subEncoding] ?: d.subtitleEncoding,
            subtitleAutoDetect = this[K.subAuto] ?: d.subtitleAutoDetect,
            videoLayout = enum(K.videoLayout, d.videoLayout),
            videoSort = enum(K.videoSort, d.videoSort),
            musicSort = enum(K.musicSort, d.musicSort),
            showThumbnails = this[K.thumbnails] ?: d.showThumbnails,
            groupEpisodes = this[K.groupEpisodes] ?: d.groupEpisodes,
            themeMode = enum(K.theme, d.themeMode),
            accent = enum<AccentColor>(K.accent, d.accent),
            animations = this[K.animations] ?: d.animations,
            density = enum(K.density, d.density),
            historyEnabled = this[K.history] ?: d.historyEnabled,
            networkCachingMs = this[K.networkCaching] ?: d.networkCachingMs,
            autoUpdateCheck = this[K.autoUpdate] ?: d.autoUpdateCheck,
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[K.defaultSpeed] = s.defaultSpeed
        this[K.resetSpeed] = s.resetSpeedOnNewMedia
        this[K.seekInterval] = s.seekIntervalSec
        this[K.autoPlayNext] = s.autoPlayNext
        this[K.resume] = s.resumePlayback
        this[K.endAction] = s.endAction.name
        this[K.decoder] = s.decoderMode.name
        this[K.decoderFallback] = s.decoderFallback
        this[K.autoPip] = s.autoPip
        this[K.bgVideoAudio] = s.backgroundVideoAudio
        this[K.aspect] = s.defaultAspect.name
        this[K.orientation] = s.orientation.name
        this[K.gBrightness] = s.brightnessGesture
        this[K.gVolume] = s.volumeGesture
        this[K.gSeek] = s.seekGesture
        this[K.gDoubleTap] = s.doubleTapSeek
        this[K.gPinch] = s.pinchZoom
        this[K.gLongPress] = s.longPressSpeed
        this[K.gLongPressSpeed] = s.longPressSpeedValue
        this[K.gTwoFingerLock] = s.twoFingerLock
        this[K.controlsTimeout] = s.controlsTimeoutSec
        this[K.remainingTime] = s.showRemainingTime
        this[K.textureView] = s.useTextureView
        this[K.seekPreview] = s.seekPreview
        this[K.eqEnabled] = s.equalizer.enabled
        this[K.eqPreset] = s.equalizer.presetIndex
        this[K.eqPreamp] = s.equalizer.preamp
        this[K.eqBands] = s.equalizer.bands.joinToString(",")
        s.equalizer.customName?.let { this[K.eqCustomName] = it } ?: remove(K.eqCustomName)
        this[K.audioDelay] = s.audioDelayMs
        this[K.replayGain] = s.replayGain.name
        this[K.replayGainPreamp] = s.replayGainPreamp
        this[K.normalizer] = s.volumeNormalizer
        this[K.stereoWiden] = s.stereoWiden
        this[K.stereoMode] = s.stereoMode.name
        this[K.pauseOnDisconnect] = s.pauseOnHeadsetDisconnect
        this[K.resumeOnFocus] = s.resumeOnFocusGain
        this[K.passthrough] = s.digitalPassthrough
        this[K.subEnabled] = s.subtitlesEnabled
        this[K.subLang] = s.preferredSubtitleLanguage
        this[K.audioLang] = s.preferredAudioLanguage
        this[K.subSize] = s.subtitleSize.name
        this[K.subColor] = s.subtitleColor.name
        this[K.subBold] = s.subtitleBold
        this[K.subOutline] = s.subtitleOutline.name
        this[K.subShadow] = s.subtitleShadow
        this[K.subBg] = s.subtitleBackgroundOpacity
        this[K.subMargin] = s.subtitleBottomMargin
        this[K.subEncoding] = s.subtitleEncoding
        this[K.subAuto] = s.subtitleAutoDetect
        this[K.videoLayout] = s.videoLayout.name
        this[K.videoSort] = s.videoSort.name
        this[K.musicSort] = s.musicSort.name
        this[K.thumbnails] = s.showThumbnails
        this[K.groupEpisodes] = s.groupEpisodes
        this[K.theme] = s.themeMode.name
        this[K.accent] = s.accent.name
        this[K.animations] = s.animations
        this[K.density] = s.density.name
        this[K.history] = s.historyEnabled
        this[K.networkCaching] = s.networkCachingMs
        this[K.autoUpdate] = s.autoUpdateCheck
    }
}
