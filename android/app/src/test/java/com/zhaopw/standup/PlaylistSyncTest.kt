package com.zhaopw.standup

import org.junit.Assert.*
import org.junit.Test

class PlaylistSyncTest {
    @Test fun replacementBeforeConnectionSelectsNewUriAtZeroAndKeepsPaused() {
        val plan = planPlaylistSync(listOf("built-in", "new"), "old", 42000, false, mapOf("old" to "new"))
        assertEquals(1, plan.index)
        assertEquals(0L, plan.position)
        assertFalse(plan.playWhenReady)
    }
    @Test fun replacingAnotherTrackPreservesPlaybackAndPosition() {
        val plan = planPlaylistSync(listOf("playing", "new"), "playing", 42000, true, mapOf("old" to "new"))
        assertEquals(0, plan.index)
        assertEquals(42000L, plan.position)
        assertTrue(plan.playWhenReady)
    }
    @Test fun multipleReplacementsWhileDisconnectedFollowOriginalTrack() {
        val plan = planPlaylistSync(listOf("built-in", "final"), "old", 42000, false, mapOf("old" to "middle", "middle" to "final"))
        assertEquals(1, plan.index)
        assertEquals(0L, plan.position)
    }
    @Test fun sameUriRepairRestartsPositionAndKeepsPausedIntent() {
        val plan = planPlaylistSync(listOf("built-in", "same"), "same", 42000, false, mapOf("same" to "same"))
        assertEquals(1, plan.index)
        assertEquals(0L, plan.position)
        assertFalse(plan.playWhenReady)
    }
    @Test fun cyclicReplacementsTerminateDeterministically() {
        assertEquals("old", replacementTarget("old", mapOf("old" to "new", "new" to "old")))
    }
    @Test fun selectingOriginalFileAgainDoesNotCreateReplacementCycle() {
        val replacements=withReplacement(mapOf("old" to "middle"),"middle","old")
        assertEquals("old",replacementTarget("middle",replacements))
        assertEquals("old",replacementTarget("old",replacements))
    }
}
