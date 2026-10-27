package com.imadlabs.iplayer.util

import android.app.ActivityManager
import android.content.Context

/**
 * Rough device class used to scale visual effects. Low-end devices (Android's low-RAM flag,
 * under 3 GB of memory, or four cores or fewer) get shorter fade-only transitions and no image
 * cross-fades, so scrolling and navigation stay responsive. Playback is not affected.
 */
object DeviceTier {
    @Volatile private var lowEnd: Boolean? = null

    fun isLowEnd(context: Context): Boolean = lowEnd ?: compute(context.applicationContext).also { lowEnd = it }

    private fun compute(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return am.isLowRamDevice || mem.totalMem < 3L * 1024 * 1024 * 1024 || Runtime.getRuntime().availableProcessors() <= 4
    }
}
