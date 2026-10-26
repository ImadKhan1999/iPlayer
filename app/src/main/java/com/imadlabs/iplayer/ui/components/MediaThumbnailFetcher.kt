package com.imadlabs.iplayer.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Request model for video frames and embedded album art. */
data class MediaThumb(val uri: Uri, val isVideo: Boolean, val albumId: Long = -1)

/**
 * Loads thumbnails through MediaStore's thumbnail cache (API 29+) or a single decoded frame /
 * embedded picture on older versions. Coil limits concurrency and cancels off-screen requests.
 */
class MediaThumbnailFetcher(private val data: MediaThumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val w = options.size.width.pxOrElse { 384 }.coerceIn(96, 1024)
        val h = options.size.height.pxOrElse { 216 }.coerceIn(96, 1024)
        val ctx = options.context
        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= 29 && data.uri.scheme == "content") {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                val bmp = runCatching { ctx.contentResolver.loadThumbnail(data.uri, Size(w, h), signal) }.getOrNull()
                cont.resume(bmp)
            }
        } else legacy(ctx)
        bitmap ?: return null
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    @Suppress("DEPRECATION")
    private fun legacy(ctx: android.content.Context): Bitmap? {
        if (data.isVideo && data.uri.scheme == "content") {
            val id = runCatching { data.uri.lastPathSegment?.toLong() }.getOrNull()
            if (id != null) MediaStore.Video.Thumbnails.getThumbnail(ctx.contentResolver, id,
                MediaStore.Video.Thumbnails.MINI_KIND, null)?.let { return it }
        }
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, data.uri)
            if (data.isVideo) r.getFrameAtTime(-1) else r.embeddedPicture?.let {
                android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    class Factory : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options)
    }
}
