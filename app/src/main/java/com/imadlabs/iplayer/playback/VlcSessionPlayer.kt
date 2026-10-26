package com.imadlabs.iplayer.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Exposes [PlaybackManager] (LibVLC) to Media3's session layer, which provides the media
 * notification, lock-screen and Bluetooth/headset controls, and Android Auto/Wear integration.
 */
@UnstableApi
class VlcSessionPlayer(
    private val context: Context,
    private val manager: PlaybackManager,
    private val scope: CoroutineScope,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val artwork = HashMap<String, ByteArray?>()
    private var artworkJob: Job? = null
    // Position is pulled through PositionSupplier, so only structural changes invalidate the state.
    private val collector: Job = manager.state.map { it.copy(positionMs = 0, bufferingPercent = 0f) }
        .distinctUntilChanged()
        .onEach { s ->
            s.item?.let { loadArtwork(it) }
            invalidateState()
        }.launchIn(scope)

    private val commands = Player.Commands.Builder().addAll(
        COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT,
        COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD,
        COMMAND_SET_SPEED_AND_PITCH, COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE,
        COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA, COMMAND_RELEASE,
    ).build()

    override fun getState(): State {
        val s = manager.state.value
        val builder = State.Builder().setAvailableCommands(commands)
        val playlist = s.queue.mapIndexed { i, q -> itemData(q, if (i == s.index) s.durationMs else q.durationMs) }
        if (playlist.isEmpty() || s.index < 0) {
            return builder.setPlaybackState(STATE_IDLE).setPlayWhenReady(false, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
        }
        val playbackState = when (s.status) {
            PlayerStatus.Idle, PlayerStatus.Error -> STATE_IDLE
            PlayerStatus.Opening, PlayerStatus.Buffering -> STATE_BUFFERING
            PlayerStatus.Ready -> STATE_READY
            PlayerStatus.Ended -> STATE_ENDED
        }
        return builder
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(s.index.coerceIn(0, playlist.lastIndex))
            .setPlaybackState(playbackState)
            .setPlayWhenReady(s.playWhenReady && s.status != PlayerStatus.Ended, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(PositionSupplier { manager.state.value.positionMs.coerceAtLeast(0) })
            .setPlaybackParameters(PlaybackParameters(s.speed))
            .setRepeatMode(when (s.repeat) {
                RepeatMode.Off -> REPEAT_MODE_OFF
                RepeatMode.All -> REPEAT_MODE_ALL
                RepeatMode.One -> REPEAT_MODE_ONE
            })
            .setShuffleModeEnabled(s.shuffle)
            .setIsLoading(s.status == PlayerStatus.Buffering)
            .build()
    }

    private fun itemData(q: QueueItem, durationMs: Long): MediaItemData {
        val meta = MediaMetadata.Builder()
            .setTitle(q.title)
            .setArtist(q.artist ?: if (q.isVideo) "iPlayer" else null)
            .setAlbumTitle(q.album)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(if (q.isVideo) MediaMetadata.MEDIA_TYPE_VIDEO else MediaMetadata.MEDIA_TYPE_MUSIC)
        artwork[q.uri]?.let { meta.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        val item = MediaItem.Builder().setMediaId(q.uri).setUri(q.uri).setMediaMetadata(meta.build()).build()
        return MediaItemData.Builder(q.key)
            .setMediaItem(item)
            .setDurationUs(if (durationMs > 0) durationMs * 1000 else androidx.media3.common.C.TIME_UNSET)
            .setIsSeekable(true)
            .build()
    }

    private fun loadArtwork(item: QueueItem) {
        if (item.isVideo || artwork.containsKey(item.uri)) return
        artwork[item.uri] = null
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                val uri = Uri.parse(item.uri)
                if (uri.scheme != "content" && uri.scheme != "file") return@withContext null
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    r.embeddedPicture
                } catch (e: Exception) {
                    null
                } finally {
                    runCatching { r.release() }
                }
            }
            if (artwork.size > 50) artwork.keys.take(25).toList().forEach(artwork::remove)
            artwork[item.uri] = bytes
            if (bytes != null) invalidateState()
        }
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) manager.play() else manager.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        manager.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        collector.cancel()
        artworkJob?.cancel()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> manager.next()
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> manager.previous()
            COMMAND_SEEK_TO_MEDIA_ITEM -> if (mediaItemIndex != manager.state.value.index) manager.skipTo(mediaItemIndex)
                else manager.seekTo(positionMs.coerceAtLeast(0))
            else -> manager.seekTo(positionMs.coerceAtLeast(0))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        manager.setSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        manager.setRepeat(when (repeatMode) {
            REPEAT_MODE_ONE -> RepeatMode.One
            REPEAT_MODE_ALL -> RepeatMode.All
            else -> RepeatMode.Off
        })
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        manager.setShuffle(shuffleModeEnabled)
        return Futures.immediateVoidFuture()
    }
}
