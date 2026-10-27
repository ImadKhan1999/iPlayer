package com.imadlabs.iplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.imadlabs.iplayer.ui.components.MediaThumbnailFetcher
import com.imadlabs.iplayer.util.DeviceTier

class IPlayerApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Thumbnails are produced lazily for visible rows only, and cached in memory. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val lowEnd = DeviceTier.isLowEnd(context)
        return ImageLoader.Builder(context)
            .components { add(MediaThumbnailFetcher.Factory()) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, if (lowEnd) 0.15 else 0.25).build() }
            .crossfade(if (lowEnd) 0 else 150)
            .build()
    }
}
