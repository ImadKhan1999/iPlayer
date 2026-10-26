package com.imadlabs.iplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.imadlabs.iplayer.ui.components.MediaThumbnailFetcher

class IPlayerApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Thumbnails are produced lazily for visible rows only, and cached in memory. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(MediaThumbnailFetcher.Factory()) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
        .crossfade(true)
        .build()
}
