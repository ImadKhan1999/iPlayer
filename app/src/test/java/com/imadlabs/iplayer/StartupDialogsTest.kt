package com.imadlabs.iplayer

import com.imadlabs.iplayer.data.settings.WelcomeState
import com.imadlabs.iplayer.startup.DialogCoordinator
import com.imadlabs.iplayer.startup.StartupDialog
import com.imadlabs.iplayer.startup.WelcomePolicy
import com.imadlabs.iplayer.update.UpdateManifest
import com.imadlabs.iplayer.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupDialogsTest {
    private val day = WelcomePolicy.MIN_INTERVAL_MS

    @Test fun welcomeFirstLaunch() {
        assertTrue(WelcomePolicy.shouldShow(WelcomeState(false, 0, 0), 1000, launchedForPlayback = false))
        assertFalse(WelcomePolicy.shouldShow(WelcomeState(false, 0, 0), 1000, launchedForPlayback = true))
    }

    @Test fun dontShowAgainIsFinal() {
        assertFalse(WelcomePolicy.shouldShow(WelcomeState(true, 1, 0), 10 * day, false))
    }

    @Test fun reminderSpacingAndLimit() {
        assertFalse(WelcomePolicy.shouldShow(WelcomeState(false, 1, 0), day - 1, false))
        assertTrue(WelcomePolicy.shouldShow(WelcomeState(false, 1, 0), day, false))
        assertFalse(WelcomePolicy.shouldShow(WelcomeState(false, WelcomePolicy.MAX_SHOWS, 0), 100 * day, false))
    }

    private val release = UpdateManifest.Release("1.1.0", 2)

    @Test fun onlyOneDialogInPriorityOrder() {
        val mandatory = UpdateState.Available(release, mandatory = true, skipped = false, checkedAt = 0)
        val optional = UpdateState.Available(release, mandatory = false, skipped = false, checkedAt = 0)
        assertEquals(StartupDialog.MandatoryUpdate, DialogCoordinator.next(true, mandatory, emptySet(), false))
        assertEquals(StartupDialog.Welcome, DialogCoordinator.next(true, mandatory, setOf(StartupDialog.MandatoryUpdate), false))
        assertEquals(StartupDialog.Welcome, DialogCoordinator.next(true, optional, emptySet(), false))
        assertEquals(StartupDialog.OptionalUpdate, DialogCoordinator.next(true, optional, setOf(StartupDialog.Welcome), false))
        assertNull(DialogCoordinator.next(false, UpdateState.Idle, emptySet(), false))
    }

    @Test fun nothingDuringPlaybackOrForSkippedVersion() {
        val optional = UpdateState.Available(release, mandatory = false, skipped = false, checkedAt = 0)
        assertNull(DialogCoordinator.next(true, optional, emptySet(), playbackActive = true))
        val skipped = UpdateState.Available(release, mandatory = false, skipped = true, checkedAt = 0)
        assertNull(DialogCoordinator.next(false, skipped, emptySet(), false))
    }
}
