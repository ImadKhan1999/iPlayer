package com.imadlabs.iplayer.startup

import com.imadlabs.iplayer.data.settings.WelcomeState
import com.imadlabs.iplayer.update.UpdateState

/**
 * First-run welcome reminder policy: shown on first launch; if "Don't show this again" was not
 * ticked it may return on later launches, at most [MAX_SHOWS] times in total and at least
 * [MIN_INTERVAL_MS] apart. Never shown when the app was opened to play a file.
 */
object WelcomePolicy {
    const val MAX_SHOWS = 3
    const val MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun shouldShow(state: WelcomeState, now: Long, launchedForPlayback: Boolean): Boolean = when {
        launchedForPlayback -> false
        state.dontShowAgain -> false
        state.timesShown == 0 -> true
        state.timesShown >= MAX_SHOWS -> false
        else -> now - state.lastShownAt >= MIN_INTERVAL_MS
    }
}

enum class StartupDialog { MandatoryUpdate, Welcome, OptionalUpdate }

/**
 * Only one startup dialog is visible at a time. Priority: a mandatory update warning first,
 * then the welcome dialog, then an optional update. Nothing is shown during active playback.
 */
object DialogCoordinator {
    fun next(
        welcomePending: Boolean,
        update: UpdateState,
        dismissed: Set<StartupDialog>,
        playbackActive: Boolean,
    ): StartupDialog? {
        if (playbackActive) return null
        val available = update as? UpdateState.Available
        val candidates = buildList {
            if (available != null && available.mandatory) add(StartupDialog.MandatoryUpdate)
            if (welcomePending) add(StartupDialog.Welcome)
            if (available != null && !available.mandatory && !available.skipped) add(StartupDialog.OptionalUpdate)
        }
        return candidates.firstOrNull { it !in dismissed }
    }
}
