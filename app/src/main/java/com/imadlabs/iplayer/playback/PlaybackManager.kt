package com.imadlabs.iplayer.playback

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.imadlabs.iplayer.data.db.AppDatabase
import com.imadlabs.iplayer.data.db.PlaybackRecord
import com.imadlabs.iplayer.data.db.QueueEntryRow
import com.imadlabs.iplayer.data.db.QueueStateRow
import com.imadlabs.iplayer.data.media.MediaFormats
import com.imadlabs.iplayer.data.media.SubtitleMatcher
import com.imadlabs.iplayer.data.settings.AppSettings
import com.imadlabs.iplayer.data.settings.AspectMode
import com.imadlabs.iplayer.data.settings.DecoderMode
import com.imadlabs.iplayer.data.settings.EndAction
import com.imadlabs.iplayer.data.settings.EqualizerSettings
import com.imadlabs.iplayer.data.settings.SettingsRepository
import com.imadlabs.iplayer.util.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.FileNotFoundException

data class TrackOption(val id: Int, val name: String)

data class EqualizerCatalog(val bandFrequencies: List<Float>, val presets: List<Preset>) {
    data class Preset(val name: String, val preamp: Float, val bands: List<Float>)
}

data class ChapterInfo(val name: String, val startMs: Long)

enum class PlayerStatus { Idle, Opening, Buffering, Ready, Ended, Error }

data class PlayerState(
    val item: QueueItem? = null,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = -1,
    val status: PlayerStatus = PlayerStatus.Idle,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val bufferingPercent: Float = 100f,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val seekable: Boolean = true,
    val speed: Float = 1f,
    val repeat: RepeatMode = RepeatMode.Off,
    val shuffle: Boolean = false,
    val abStartMs: Long? = null,
    val abEndMs: Long? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val selectedAudioTrack: Int = -1,
    val subtitleTracks: List<TrackOption> = emptyList(),
    val selectedSubtitleTrack: Int = -1,
    val hasVideo: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val frameRate: Float = 0f,
    val subtitleDelayMs: Long = 0,
    val audioDelayMs: Long = 0,
    val aspect: AspectMode = AspectMode.Fit,
    val sleepAtElapsed: Long? = null,
    val sleepAtEndOfItem: Boolean = false,
    val decoder: DecoderMode = DecoderMode.Auto,
    val error: PlaybackError? = null,
    val chapters: List<ChapterInfo> = emptyList(),
    val engineGeneration: Int = 0,
)

/** One-off messages for the UI (snackbars/toasts). */
sealed interface PlayerEvent {
    data class Notice(val text: String) : PlayerEvent
    data object CloseRequested : PlayerEvent
}

/** Supplies saved credentials for network URIs; never logged. */
interface CredentialsProvider {
    suspend fun mediaOptionsFor(uri: Uri): List<String>
    suspend fun credentialsFor(uri: Uri): Pair<String, String>?
}

/**
 * Owns the LibVLC engine and the play queue for the whole app (video and audio). All public
 * methods must be called on the main thread; LibVLC events are delivered there as well.
 */
class PlaybackManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    private val credentials: CredentialsProvider,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val queue = QueueModel()
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var engineKey: List<String> = emptyList()
    private var openFd: ParcelFileDescriptor? = null
    private var attachedLayout: VLCVideoLayout? = null
    private var attachedTexture = true

    private var loadJob: Job? = null
    private var sleepJob: Job? = null
    private var lastSavedAt = 0L
    private var softwareFallbackTried = false
    private var networkRetries = 0
    private var pendingTrackRestore: PlaybackRecord? = null
    private var pausedByFocusLoss = false
    private var lastKnownPosition = 0L

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    init {
        // Re-create the engine when instance-wide options change; apply EQ and delay live.
        settings.state.map { EngineOptions.build(it) }.distinctUntilChanged().drop(1)
            .onEach { if (libVlc != null && it != engineKey) recreateEngine() }.launchIn(scope)
        settings.state.map { it.equalizer }.distinctUntilChanged()
            .onEach { applyEqualizer(it) }.launchIn(scope)
        settings.state.map { it.audioDelayMs }.distinctUntilChanged()
            .onEach { ms -> player?.setAudioDelay(ms * 1000); _state.update { s -> s.copy(audioDelayMs = ms) } }
            .launchIn(scope)
    }

    // ---------------------------------------------------------------- engine lifecycle

    private fun ensureEngine(s: AppSettings): MediaPlayer {
        val opts = EngineOptions.build(s)
        player?.let { if (opts == engineKey) return it }
        releaseEngine()
        val vlc = LibVLC(context, ArrayList(opts))
        vlc.setUserAgent("iPlayer", "iPlayer/ImadLabs")
        Dialog.setCallbacks(vlc, dialogCallbacks)
        engineKey = opts
        libVlc = vlc
        val mp = MediaPlayer(vlc)
        mp.setEventListener(::onVlcEvent)
        player = mp
        applyEqualizer(s.equalizer)
        mp.setAudioDelay(s.audioDelayMs * 1000)
        mp.setAudioDigitalOutputEnabled(s.digitalPassthrough)
        attachedLayout?.let { mp.attachViews(it, null, true, attachedTexture) }
        _state.update { it.copy(engineGeneration = it.engineGeneration + 1) }
        Diagnostics.log("Engine", "LibVLC ${LibVLC.version()} started with ${opts.size} options")
        return mp
    }

    private fun releaseEngine() {
        player?.let {
            it.setEventListener(null)
            if (attachedLayout != null) it.detachViews()
            it.stop()
            it.release()
        }
        player = null
        libVlc?.release()
        libVlc = null
        closeFd()
    }

    private fun recreateEngine() {
        val s = _state.value
        val item = s.item
        val resumeAt = lastKnownPosition
        val wasPlaying = s.playWhenReady
        Diagnostics.log("Engine", "Applying new engine options")
        releaseEngine()
        if (item != null && s.status != PlayerStatus.Idle && s.status != PlayerStatus.Error) {
            load(item, resumeAt, s.decoder, playWhenReady = wasPlaying, keepSpeed = true)
        }
    }

    /** Answers LibVLC login prompts (SMB, HTTP auth, ...) from saved sources, once per item. */
    private var loginAttemptedFor: Long? = null
    private val dialogCallbacks = object : Dialog.Callbacks {
        override fun onDisplay(dialog: Dialog.ErrorMessage) {
            Diagnostics.log("Engine", "VLC error dialog: ${dialog.title}: ${dialog.text}")
        }
        override fun onDisplay(dialog: Dialog.LoginDialog) {
            val item = _state.value.item
            if (item == null || loginAttemptedFor == item.key) {
                dialog.dismiss()
                fail(PlaybackErrors.auth(), null)
                return
            }
            loginAttemptedFor = item.key
            scope.launch {
                val creds = credentials.credentialsFor(Uri.parse(item.uri))
                if (creds != null) dialog.postLogin(creds.first, creds.second, false)
                else { dialog.dismiss(); fail(PlaybackErrors.auth(), null) }
            }
        }
        override fun onDisplay(dialog: Dialog.QuestionDialog) {
            // e.g. untrusted certificates or broken-index prompts: choose the safe default (cancel).
            Diagnostics.log("Engine", "VLC question dialog dismissed: ${dialog.title}")
            dialog.dismiss()
        }
        override fun onDisplay(dialog: Dialog.ProgressDialog) = Unit
        override fun onCanceled(dialog: Dialog?) = Unit
        override fun onProgressUpdate(dialog: Dialog.ProgressDialog) = Unit
    }

    // ---------------------------------------------------------------- queue API

    fun playQueue(items: List<QueueItem>, startIndex: Int = 0, startPositionMs: Long? = null) {
        if (items.isEmpty()) return
        queue.set(items, startIndex)
        publishQueue()
        loadCurrent(startPositionMs)
        persistQueue()
    }

    fun playNext(items: List<QueueItem>) {
        if (queue.isEmpty) return playQueue(items)
        queue.addNext(items); publishQueue(); persistQueue()
        emit("Playing next: ${items.size} item${if (items.size == 1) "" else "s"}")
    }

    fun addToQueue(items: List<QueueItem>) {
        if (queue.isEmpty) return playQueue(items)
        queue.addLast(items); publishQueue(); persistQueue()
        emit("Added ${items.size} item${if (items.size == 1) "" else "s"} to queue")
    }

    fun removeFromQueue(index: Int) {
        val wasCurrent = queue.removeAt(index)
        publishQueue()
        if (queue.isEmpty) stop()
        else if (wasCurrent) loadCurrent(null)
        persistQueue()
    }

    fun moveInQueue(from: Int, to: Int) {
        queue.move(from, to); publishQueue(); persistQueue()
    }

    fun clearQueue() {
        stop()
        queue.clear(); publishQueue(); persistQueue()
    }

    fun skipTo(index: Int) {
        if (queue.jumpTo(index)) { publishQueue(); loadCurrent(null); persistQueue() }
    }

    fun next() {
        val n = queue.nextIndex(auto = false) ?: run { emit("End of queue"); return }
        skipTo(n)
    }

    /** Restarts the current item when more than 3 s in, otherwise goes to the previous one. */
    fun previous() {
        if (lastKnownPosition > 3000) { seekTo(0); return }
        val p = queue.previousIndex() ?: run { seekTo(0); return }
        skipTo(p)
    }

    fun setRepeat(mode: RepeatMode) {
        queue.repeat = mode
        _state.update { it.copy(repeat = mode) }
        persistQueue()
    }

    fun cycleRepeat() = setRepeat(RepeatMode.entries[(queue.repeat.ordinal + 1) % RepeatMode.entries.size])

    fun setShuffle(enabled: Boolean) {
        queue.setShuffle(enabled)
        _state.update { it.copy(shuffle = enabled) }
        persistQueue()
    }

    private fun publishQueue() {
        _state.update {
            it.copy(queue = queue.items, index = queue.index, item = queue.current ?: it.item,
                repeat = queue.repeat, shuffle = queue.shuffle)
        }
    }

    // ---------------------------------------------------------------- transport

    fun play() {
        val s = _state.value
        when {
            s.item == null -> return
            s.status == PlayerStatus.Ended || s.status == PlayerStatus.Error || player?.hasMedia() != true ->
                load(s.item, if (s.status == PlayerStatus.Ended) 0 else lastKnownPosition, s.decoder, true, keepSpeed = true)
            else -> {
                if (!requestFocus()) return
                player?.play()
                _state.update { it.copy(playWhenReady = true) }
            }
        }
    }

    fun pause() {
        player?.pause()
        _state.update { it.copy(playWhenReady = false) }
        saveProgress(force = true)
    }

    fun togglePlayPause() = if (_state.value.playWhenReady && _state.value.status != PlayerStatus.Ended) pause() else play()

    fun stop() {
        saveProgress(force = true)
        loadJob?.cancel()
        player?.stop()
        closeFd()
        abandonFocus()
        releaseLocks()
        _state.update { it.copy(status = PlayerStatus.Idle, isPlaying = false, playWhenReady = false) }
        disconnectSession()
    }

    fun seekTo(ms: Long, fast: Boolean = false) {
        val p = player ?: return
        val dur = _state.value.durationMs
        val target = if (dur > 0) ms.coerceIn(0, dur) else ms.coerceAtLeast(0)
        p.setTime(target, fast)
        lastKnownPosition = target
        _state.update { it.copy(positionMs = target) }
    }

    fun seekBy(deltaMs: Long) = seekTo(lastKnownPosition + deltaMs)

    /** Moves one frame using a precise seek while paused (LibVLC 3 has no native frame-step API). */
    fun frameStep(forward: Boolean) {
        val fps = _state.value.frameRate.takeIf { it > 1f } ?: 30f
        if (_state.value.playWhenReady) pause()
        seekTo(lastKnownPosition + ((if (forward) 1 else -1) * 1000f / fps).toLong(), fast = false)
    }

    fun setSpeed(rate: Float) {
        val r = rate.coerceIn(0.25f, 4f)
        player?.setRate(r)
        _state.update { it.copy(speed = r) }
    }

    // ---------------------------------------------------------------- A-B, sleep

    fun setAbPoint() {
        val s = _state.value
        val pos = lastKnownPosition
        _state.update {
            when {
                s.abStartMs == null -> it.copy(abStartMs = pos, abEndMs = null)
                s.abEndMs == null && pos > s.abStartMs + 500 -> it.copy(abEndMs = pos)
                else -> it.copy(abStartMs = null, abEndMs = null)
            }
        }
        val n = _state.value
        emit(when {
            n.abStartMs != null && n.abEndMs == null -> "A-B repeat: point A set"
            n.abEndMs != null -> "A-B repeat on"
            else -> "A-B repeat off"
        })
    }

    fun clearAb() = _state.update { it.copy(abStartMs = null, abEndMs = null) }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepAtElapsed = null, sleepAtEndOfItem = false) }
            return
        }
        val at = SystemClock.elapsedRealtime() + minutes * 60_000L
        _state.update { it.copy(sleepAtElapsed = at, sleepAtEndOfItem = false) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            pause()
            _state.update { it.copy(sleepAtElapsed = null) }
            emit("Sleep timer: playback paused")
        }
    }

    fun setSleepAtEndOfItem() {
        sleepJob?.cancel()
        _state.update { it.copy(sleepAtElapsed = null, sleepAtEndOfItem = true) }
    }

    // ---------------------------------------------------------------- tracks, sync, video

    fun selectAudioTrack(id: Int) {
        player?.setAudioTrack(id)
        refreshTracks()
        persistTrackChoice()
    }

    fun selectSubtitleTrack(id: Int) {
        player?.setSpuTrack(id)
        refreshTracks()
        persistTrackChoice()
    }

    /** Adds an external subtitle (file, content or http URI) and selects it. */
    fun addSubtitle(uri: Uri): Boolean {
        val p = player ?: return false
        val ok = p.addSlave(IMedia.Slave.Type.Subtitle, uri, true)
        if (ok) {
            val item = _state.value.item
            if (item != null && settings.current.historyEnabled) scope.launch {
                db.playbackRecords().get(item.uri)?.let { db.playbackRecords().upsert(it.copy(externalSubtitleUri = uri.toString())) }
            }
            emit("Subtitle loaded")
        } else emit("This subtitle file couldn't be loaded")
        return ok
    }

    fun setSubtitleDelay(ms: Long) {
        player?.setSpuDelay(ms * 1000)
        _state.update { it.copy(subtitleDelayMs = ms) }
        val item = _state.value.item ?: return
        if (settings.current.historyEnabled) scope.launch {
            db.playbackRecords().get(item.uri)?.let { db.playbackRecords().upsert(it.copy(subtitleDelayUs = ms * 1000)) }
        }
    }

    /** Session-only audio delay on top of the global sync setting. */
    fun setAudioDelay(ms: Long) {
        player?.setAudioDelay(ms * 1000)
        _state.update { it.copy(audioDelayMs = ms) }
    }

    fun setAspect(mode: AspectMode) {
        val p = player ?: return
        when (mode) {
            AspectMode.Fit -> { p.setAspectRatio(null); p.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT) }
            AspectMode.Crop -> { p.setAspectRatio(null); p.setVideoScale(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN) }
            AspectMode.Stretch -> { p.setAspectRatio(null); p.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL) }
            AspectMode.Original -> { p.setAspectRatio(null); p.setVideoScale(MediaPlayer.ScaleType.SURFACE_ORIGINAL) }
            AspectMode.Ratio16x9 -> p.setVideoScale(MediaPlayer.ScaleType.SURFACE_16_9)
            AspectMode.Ratio4x3 -> p.setVideoScale(MediaPlayer.ScaleType.SURFACE_4_3)
            AspectMode.Ratio235 -> p.setVideoScale(MediaPlayer.ScaleType.SURFACE_235_1)
        }
        _state.update { it.copy(aspect = mode) }
    }

    fun attachVideo(layout: VLCVideoLayout, useTextureView: Boolean) {
        val p = player ?: ensureEngine(settings.current)
        if (attachedLayout === layout) return
        if (attachedLayout != null) p.detachViews()
        attachedLayout = layout
        attachedTexture = useTextureView
        p.attachViews(layout, null, true, useTextureView)
        p.setVideoTrackEnabled(true)
        setAspect(_state.value.aspect)
        updateLocks()
    }

    /** Detaches the surface. When [keepAudio] is true, video decoding is disabled and audio continues. */
    fun detachVideo(layout: VLCVideoLayout, keepAudio: Boolean) {
        if (attachedLayout !== layout) return
        val p = player
        if (p != null) {
            if (keepAudio) p.setVideoTrackEnabled(false)
            p.detachViews()
        }
        attachedLayout = null
        updateLocks()
    }

    fun mediaInfo(): MediaInfo? {
        val p = player ?: return null
        val media = p.media ?: return null
        return MediaInfo.from(context, _state.value, media, p)
    }

    fun setDecoderAndReload(mode: DecoderMode) {
        val item = _state.value.item ?: return
        softwareFallbackTried = mode == DecoderMode.Software
        load(item, lastKnownPosition, mode, playWhenReady = true, keepSpeed = true)
    }

    fun retry() {
        loginAttemptedFor = null
        val s = _state.value
        val item = s.item ?: return
        load(item, lastKnownPosition, s.decoder, true, keepSpeed = true)
    }

    // ---------------------------------------------------------------- loading

    private fun loadCurrent(startPositionMs: Long?) {
        val item = queue.current ?: return
        clearAb()
        load(item, startPositionMs, null, playWhenReady = true, keepSpeed = false)
    }

    private fun load(item: QueueItem, startPositionMs: Long?, decoderOverride: DecoderMode?, playWhenReady: Boolean, keepSpeed: Boolean) {
        saveProgress(force = true)
        loadJob?.cancel()
        val previousItem = _state.value.item
        if (previousItem?.key != item.key) { softwareFallbackTried = false; networkRetries = 0 }
        loadJob = scope.launch {
            val s = settings.snapshot()
            val decoder = decoderOverride ?: s.decoderMode
            val record = if (s.historyEnabled) db.playbackRecords().get(item.uri) else null
            val start = startPositionMs ?: if (s.resumePlayback && record != null && !record.completed &&
                record.positionMs > 5000 && (record.durationMs <= 0 || record.positionMs < record.durationMs - 5000)
            ) record.positionMs else 0L
            val speed = if (keepSpeed || !s.resetSpeedOnNewMedia) _state.value.speed else s.defaultSpeed
            _state.update {
                it.copy(item = item, status = PlayerStatus.Opening, error = null, positionMs = start,
                    durationMs = item.durationMs, isPlaying = false, playWhenReady = playWhenReady,
                    audioTracks = emptyList(), subtitleTracks = emptyList(), hasVideo = false,
                    subtitleDelayMs = (record?.subtitleDelayUs ?: 0) / 1000, decoder = decoder, speed = speed,
                    aspect = if (previousItem?.key == item.key) it.aspect else s.defaultAspect,
                    chapters = emptyList(), bufferingPercent = 0f)
            }
            lastKnownPosition = start
            pendingTrackRestore = record
            connectSession()

            val mp = ensureEngine(s)
            val media = try {
                createMedia(item, decoder, start, s)
            } catch (e: FileNotFoundException) {
                fail(PlaybackErrors.notFound(), e); return@launch
            } catch (e: SecurityException) {
                fail(PlaybackErrors.permission(), e); return@launch
            } catch (e: Exception) {
                fail(PlaybackError(PlaybackError.Kind.Unknown, "This item couldn't be opened.", e.message), e); return@launch
            }
            mp.media = media
            media.release()
            mp.setRate(speed)
            if (record?.externalSubtitleUri != null) mp.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(record.externalSubtitleUri), false)
            if (s.subtitleAutoDetect) discoverSubtitles(item, s).forEach { mp.addSlave(IMedia.Slave.Type.Subtitle, it, false) }
            if (playWhenReady) {
                if (!requestFocus()) {
                    _state.update { it.copy(playWhenReady = false, status = PlayerStatus.Ready) }
                    emit("Another app is using audio")
                    return@launch
                }
                mp.play()
            }
            Diagnostics.log("Player", "Opening ${item.uri} decoder=$decoder start=$start")
            recordHistoryStart(item)
        }
    }

    private suspend fun createMedia(item: QueueItem, decoder: DecoderMode, startMs: Long, s: AppSettings): Media {
        val vlc = libVlc ?: error("engine not ready")
        val uri = Uri.parse(item.uri)
        closeFd()
        val media: Media = when (uri.scheme?.lowercase()) {
            "content" -> {
                val path = localPathFor(uri)
                if (path != null) Media(vlc, path) else {
                    val pfd = withContext(Dispatchers.IO) {
                        context.contentResolver.openFileDescriptor(uri, "r")
                    } ?: throw FileNotFoundException(item.uri)
                    openFd = pfd
                    Media(vlc, pfd.fileDescriptor)
                }
            }
            "file", null -> {
                val path = uri.path ?: item.uri
                if (!withContext(Dispatchers.IO) { File(path).exists() }) throw FileNotFoundException(path)
                Media(vlc, path)
            }
            else -> Media(vlc, uri)
        }
        when (decoder) {
            DecoderMode.Auto -> media.setHWDecoderEnabled(true, false)
            DecoderMode.Hardware -> media.setHWDecoderEnabled(true, true)
            DecoderMode.Software -> media.setHWDecoderEnabled(false, false)
        }
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000.0}")
        if (isNetworkUri(item.uri)) {
            media.addOption(":network-caching=${s.networkCachingMs}")
            credentials.mediaOptionsFor(uri).forEach(media::addOption)
        }
        return media
    }

    /** Readable file path behind a MediaStore URI, so VLC can also find sibling subtitle files. */
    private suspend fun localPathFor(uri: Uri): String? = withContext(Dispatchers.IO) {
        if (uri.authority != "media") return@withContext null
        runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()?.takeIf { File(it).canRead() }
    }

    /**
     * Same-name subtitle files next to the video. Needs directory access: with scoped storage this
     * works when the folder is readable (all-files access); otherwise users pick subtitles manually.
     */
    private suspend fun discoverSubtitles(item: QueueItem, s: AppSettings): List<Uri> = withContext(Dispatchers.IO) {
        if (!item.isVideo) return@withContext emptyList()
        val uri = Uri.parse(item.uri)
        val path = when (uri.scheme) {
            "file" -> uri.path
            "content" -> localPathFor(uri)
            else -> null
        } ?: return@withContext emptyList()
        // VLC's own autodetection already covers files it can open by path.
        if (uri.scheme == "file" || uri.authority == "media") return@withContext emptyList()
        val file = File(path)
        val siblings = file.parentFile?.list()?.toList() ?: return@withContext emptyList()
        SubtitleMatcher.match(file.name, siblings, s.preferredSubtitleLanguage).take(4)
            .map { Uri.fromFile(File(file.parentFile, it.fileName)) }
    }

    private fun closeFd() {
        runCatching { openFd?.close() }
        openFd = null
    }

    private fun fail(error: PlaybackError, e: Throwable?) {
        Diagnostics.log("Player", "Playback failed: ${error.kind} ${error.technical.orEmpty()}", e)
        _state.update { it.copy(status = PlayerStatus.Error, error = error, isPlaying = false, playWhenReady = false) }
        abandonFocus()
        releaseLocks()
    }

    // ---------------------------------------------------------------- VLC events

    private fun onVlcEvent(ev: MediaPlayer.Event) {
        when (ev.type) {
            MediaPlayer.Event.Opening -> _state.update { it.copy(status = PlayerStatus.Opening) }
            MediaPlayer.Event.Buffering -> {
                val pct = ev.buffering
                _state.update {
                    it.copy(bufferingPercent = pct,
                        status = if (pct < 100f) PlayerStatus.Buffering else if (it.status == PlayerStatus.Buffering) PlayerStatus.Ready else it.status)
                }
            }
            MediaPlayer.Event.Playing -> {
                networkRetries = 0
                _state.update { it.copy(isPlaying = true, playWhenReady = true, status = PlayerStatus.Ready, error = null) }
                refreshTracks()
                restoreTrackChoices()
                registerNoisy()
                updateLocks()
            }
            MediaPlayer.Event.Paused -> {
                _state.update { it.copy(isPlaying = false, playWhenReady = false) }
                updateLocks()
            }
            MediaPlayer.Event.Stopped -> {
                _state.update { it.copy(isPlaying = false) }
                updateLocks()
            }
            MediaPlayer.Event.EndReached -> onEnded()
            MediaPlayer.Event.EncounteredError -> onError()
            MediaPlayer.Event.TimeChanged -> {
                val t = ev.timeChanged
                lastKnownPosition = t
                val s = _state.value
                if (s.abStartMs != null && s.abEndMs != null && t >= s.abEndMs) { seekTo(s.abStartMs); return }
                _state.update { it.copy(positionMs = t) }
                saveProgress(force = false)
            }
            MediaPlayer.Event.LengthChanged -> _state.update { it.copy(durationMs = ev.lengthChanged) }
            MediaPlayer.Event.SeekableChanged -> _state.update { it.copy(seekable = ev.seekable) }
            MediaPlayer.Event.Vout -> {
                _state.update { it.copy(hasVideo = ev.voutCount > 0) }
                refreshVideoInfo()
            }
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> refreshTracks()
        }
    }

    private fun onEnded() {
        val s = settings.current
        val item = _state.value.item
        markCompleted(item)
        _state.update { it.copy(status = PlayerStatus.Ended, isPlaying = false, positionMs = it.durationMs) }
        if (_state.value.sleepAtEndOfItem) {
            _state.update { it.copy(sleepAtEndOfItem = false, playWhenReady = false) }
            emit("Sleep timer: playback stopped")
            releaseLocks(); abandonFocus()
            return
        }
        val autoNext = if (item?.isVideo == true) s.autoPlayNext else true
        when {
            queue.repeat == RepeatMode.One || (item?.isVideo == true && s.endAction == EndAction.RepeatOne) ->
                item?.let { load(it, 0, _state.value.decoder, true, keepSpeed = true) }
            item?.isVideo == true && s.endAction == EndAction.Stop -> { releaseLocks(); abandonFocus() }
            item?.isVideo == true && s.endAction == EndAction.Close -> { releaseLocks(); abandonFocus(); _events.tryEmit(PlayerEvent.CloseRequested) }
            autoNext -> {
                val n = queue.nextIndex(auto = true)
                if (n != null) skipTo(n) else {
                    _state.update { it.copy(playWhenReady = false) }
                    releaseLocks(); abandonFocus()
                    if (item?.isVideo == true && s.endAction == EndAction.Next) _events.tryEmit(PlayerEvent.CloseRequested)
                }
            }
            else -> { _state.update { it.copy(playWhenReady = false) }; releaseLocks(); abandonFocus() }
        }
    }

    private fun onError() {
        val s = _state.value
        val item = s.item ?: return
        val cfg = settings.current
        if (isNetworkUri(item.uri)) {
            if (networkRetries < 2) {
                networkRetries++
                val wait = 1500L * networkRetries
                Diagnostics.log("Player", "Network error, retry $networkRetries in ${wait}ms")
                emit("Connection problem, retrying…")
                scope.launch { delay(wait); if (_state.value.item?.key == item.key) load(item, lastKnownPosition, s.decoder, true, true) }
                return
            }
            fail(PlaybackErrors.network(item.uri), null)
            return
        }
        if (s.decoder != DecoderMode.Software && cfg.decoderFallback && !softwareFallbackTried) {
            softwareFallbackTried = true
            Diagnostics.log("Player", "Decoder error with ${s.decoder}; falling back to software at ${lastKnownPosition}ms")
            emit("Switching to software decoding")
            load(item, lastKnownPosition, DecoderMode.Software, true, keepSpeed = true)
            return
        }
        fail(PlaybackErrors.decoder(s.decoder, "LibVLC EncounteredError"), null)
    }

    private fun refreshTracks() {
        val p = player ?: return
        val audio = p.audioTracks?.map { TrackOption(it.id, it.name ?: "Track ${it.id}") }.orEmpty()
        val subs = p.spuTracks?.map { TrackOption(it.id, it.name ?: "Track ${it.id}") }.orEmpty()
        val chapters = runCatching { p.getChapters(-1)?.map { ChapterInfo(it.name ?: "", it.timeOffset) } }.getOrNull().orEmpty()
        _state.update {
            it.copy(audioTracks = audio, selectedAudioTrack = p.audioTrack,
                subtitleTracks = subs, selectedSubtitleTrack = p.spuTrack, chapters = chapters)
        }
    }

    private fun refreshVideoInfo() {
        val vt = player?.currentVideoTrack ?: return
        val fps = if (vt.frameRateDen > 0) vt.frameRateNum.toFloat() / vt.frameRateDen else 0f
        var w = vt.width; var h = vt.height
        if (vt.sarNum > 0 && vt.sarDen > 0 && vt.sarNum != vt.sarDen) w = w * vt.sarNum / vt.sarDen
        if (vt.orientation in 4..7) { val t = w; w = h; h = t }
        _state.update { it.copy(videoWidth = w, videoHeight = h, frameRate = fps) }
    }

    private fun restoreTrackChoices() {
        val record = pendingTrackRestore ?: run { applyLanguagePreferences(); return }
        pendingTrackRestore = null
        val p = player ?: return
        record.audioTrackId?.let { id -> if (p.audioTracks?.any { it.id == id } == true) p.setAudioTrack(id) }
        record.subtitleTrackId?.let { id -> if (id == -1 || p.spuTracks?.any { it.id == id } == true) p.setSpuTrack(id) }
        if (record.audioTrackId == null && record.subtitleTrackId == null) applyLanguagePreferences()
        if (record.subtitleDelayUs != 0L) p.setSpuDelay(record.subtitleDelayUs)
        refreshTracks()
    }

    private fun applyLanguagePreferences() {
        val p = player ?: return
        val s = settings.current
        if (!s.subtitlesEnabled) p.setSpuTrack(-1)
        else if (s.preferredSubtitleLanguage.isNotBlank()) {
            p.spuTracks?.firstOrNull { it.id != -1 && nameHasLanguage(it.name, s.preferredSubtitleLanguage) }?.let { p.setSpuTrack(it.id) }
        }
        if (s.preferredAudioLanguage.isNotBlank()) {
            p.audioTracks?.firstOrNull { it.id != -1 && nameHasLanguage(it.name, s.preferredAudioLanguage) }?.let { p.setAudioTrack(it.id) }
        }
        refreshTracks()
    }

    private fun nameHasLanguage(name: String?, lang: String): Boolean {
        if (name == null) return false
        val tokens = name.lowercase().split(' ', '[', ']', '-', '(', ')', ',').filter { it.isNotBlank() }
        return tokens.any { SubtitleMatcher.languageMatches(it, lang) }
    }

    private fun persistTrackChoice() {
        val s = _state.value
        val item = s.item ?: return
        if (!settings.current.historyEnabled) return
        scope.launch {
            db.playbackRecords().get(item.uri)?.let {
                db.playbackRecords().upsert(it.copy(audioTrackId = s.selectedAudioTrack, subtitleTrackId = s.selectedSubtitleTrack))
            }
        }
    }

    /** LibVLC's built-in equalizer presets and band layout (loads the engine's native libraries). */
    fun equalizerCatalog(): EqualizerCatalog {
        if (libVlc == null) ensureEngine(settings.current)
        val bandCount = MediaPlayer.Equalizer.getBandCount()
        val presets = (0 until MediaPlayer.Equalizer.getPresetCount()).map { i ->
            val e = MediaPlayer.Equalizer.createFromPreset(i)
            EqualizerCatalog.Preset(MediaPlayer.Equalizer.getPresetName(i), e.preAmp, (0 until bandCount).map(e::getAmp))
        }
        return EqualizerCatalog((0 until bandCount).map(MediaPlayer.Equalizer::getBandFrequency), presets)
    }

    private fun applyEqualizer(eq: EqualizerSettings) {
        val p = player ?: return
        if (!eq.enabled) { p.setEqualizer(null); return }
        val e = MediaPlayer.Equalizer.createFromPreset(eq.presetIndex.coerceIn(0, MediaPlayer.Equalizer.getPresetCount() - 1))
        if (eq.bands.size == MediaPlayer.Equalizer.getBandCount()) eq.bands.forEachIndexed { i, v -> e.setAmp(i, v) }
        e.setPreAmp(eq.preamp)
        p.setEqualizer(e)
    }

    // ---------------------------------------------------------------- persistence

    private fun recordHistoryStart(item: QueueItem) {
        if (!settings.current.historyEnabled) return
        scope.launch {
            val dao = db.playbackRecords()
            val old = dao.get(item.uri)
            dao.upsert(
                (old ?: PlaybackRecord(item.uri, item.title, item.isVideo, 0, item.durationMs, 0, 0, false)).copy(
                    title = item.title, lastPlayedAt = System.currentTimeMillis(), playCount = (old?.playCount ?: 0) + 1,
                    completed = false,
                )
            )
        }
    }

    private fun saveProgress(force: Boolean) {
        val s = _state.value
        val item = s.item ?: return
        if (!settings.current.historyEnabled || s.status == PlayerStatus.Idle || s.status == PlayerStatus.Error) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSavedAt < 5000) return
        lastSavedAt = now
        val pos = lastKnownPosition
        val dur = s.durationMs
        scope.launch {
            val dao = db.playbackRecords()
            val old = dao.get(item.uri) ?: return@launch
            val completed = dur > 0 && pos >= dur * 0.95
            dao.upsert(old.copy(positionMs = if (completed) 0 else pos, durationMs = dur, completed = completed,
                lastPlayedAt = System.currentTimeMillis()))
        }
        persistQueuePosition(pos)
    }

    private fun markCompleted(item: QueueItem?) {
        item ?: return
        if (!settings.current.historyEnabled) return
        scope.launch { db.playbackRecords().get(item.uri)?.let { db.playbackRecords().upsert(it.copy(completed = true, positionMs = 0)) } }
    }

    private fun persistQueue() {
        val items = queue.items
        val state = QueueStateRow(0, queue.index, lastKnownPosition, queue.shuffle, queue.repeat.ordinal, queue.shuffleOrder.joinToString(","))
        scope.launch {
            db.queue().replace(items.mapIndexed { i, q ->
                QueueEntryRow(i, q.uri, q.title, q.artist, q.album, q.artworkUri, q.durationMs, q.isVideo)
            }, state)
        }
    }

    private fun persistQueuePosition(pos: Long) {
        val state = QueueStateRow(0, queue.index, pos, queue.shuffle, queue.repeat.ordinal, queue.shuffleOrder.joinToString(","))
        scope.launch { db.queue().saveState(state) }
    }

    /** Restores the last audio queue (paused) after process death. Video queues are not restored. */
    fun restoreQueue() {
        if (!queue.isEmpty) return
        scope.launch {
            val rows = db.queue().entries()
            val st = db.queue().state() ?: return@launch
            if (rows.isEmpty() || rows.any { it.isVideo }) return@launch
            val items = rows.map { QueueItem(it.uri, it.title, it.artist, it.album, it.artworkUri, it.durationMs, it.isVideo) }
            queue.restore(items, st.currentIndex, st.shuffle,
                st.shuffleOrder.split(',').mapNotNull { it.toIntOrNull() },
                RepeatMode.entries.getOrElse(st.repeatMode) { RepeatMode.Off })
            lastKnownPosition = st.positionMs
            _state.update {
                it.copy(queue = queue.items, index = queue.index, item = queue.current, positionMs = st.positionMs,
                    durationMs = queue.current?.durationMs ?: 0, repeat = queue.repeat, shuffle = queue.shuffle,
                    status = PlayerStatus.Idle)
            }
        }
    }

    // ---------------------------------------------------------------- audio focus, noisy, locks

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { pausedByFocusLoss = false; if (_state.value.playWhenReady) pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (_state.value.playWhenReady) { pause(); pausedByFocusLoss = true }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(30)
            AudioManager.AUDIOFOCUS_GAIN -> {
                player?.setVolume(100)
                if (pausedByFocusLoss && settings.current.resumeOnFocusGain) play()
                pausedByFocusLoss = false
            }
        }
    }

    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(if (_state.value.item?.isVideo == true) AudioAttributes.CONTENT_TYPE_MOVIE else AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(false)
            .build().also { focusRequest = it }
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        unregisterNoisy()
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && settings.current.pauseOnHeadsetDisconnect &&
                _state.value.playWhenReady) pause()
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(context, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    /** Partial wake lock only while audio plays without a visible surface; Wi-Fi lock for streams. */
    @SuppressLint("WakelockTimeout")
    private fun updateLocks() {
        val s = _state.value
        val needCpu = s.isPlaying && attachedLayout == null
        val needWifi = s.isPlaying && s.item?.let { isNetworkUri(it.uri) } == true
        if (needCpu) {
            val wl = wakeLock ?: context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iPlayer:playback").also { it.setReferenceCounted(false); wakeLock = it }
            if (!wl.isHeld) wl.acquire()
        } else wakeLock?.takeIf { it.isHeld }?.release()
        if (needWifi) {
            val wl = wifiLock ?: context.applicationContext.getSystemService(WifiManager::class.java)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "iPlayer:stream")?.also { it.setReferenceCounted(false); wifiLock = it }
            if (wl != null && !wl.isHeld) wl.acquire()
        } else wifiLock?.takeIf { it.isHeld }?.release()
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    // ---------------------------------------------------------------- media session service

    /** Binding a controller starts [PlaybackService], which handles notification and foreground state. */
    private fun connectSession() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
    }

    private fun disconnectSession() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun emit(text: String) { _events.tryEmit(PlayerEvent.Notice(text)) }

    /** Video file extension hint for intents with unknown MIME type. */
    fun looksLikeVideo(name: String): Boolean = MediaFormats.isVideo(name) || !MediaFormats.isAudio(name)

    val engine: MediaPlayer? get() = player
}
