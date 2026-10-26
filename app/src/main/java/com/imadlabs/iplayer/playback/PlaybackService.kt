package com.imadlabs.iplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.imadlabs.iplayer.IPlayerApp
import com.imadlabs.iplayer.ui.MainActivity
import com.imadlabs.iplayer.ui.player.PlayerActivity

/**
 * Foreground media service. Media3 posts the playback notification and promotes the service to
 * the foreground only while something is playing, so no service lingers when playback stops.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as IPlayerApp).container
        val player = VlcSessionPlayer(this, container.playback, container.appScope)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity(container.playback.state.value.item?.isVideo == true))
            .build()
    }

    private fun sessionActivity(video: Boolean): PendingIntent {
        val intent = if (video) Intent(this, PlayerActivity::class.java).setAction(PlayerActivity.ACTION_RESUME)
        else Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_NOW_PLAYING)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, if (video) 1 else 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val s = (application as IPlayerApp).container.playback.state.value
        // Video stops with the task; audio keeps playing in the background like a music app.
        if (!s.playWhenReady || s.item?.isVideo == true) {
            (application as IPlayerApp).container.playback.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
