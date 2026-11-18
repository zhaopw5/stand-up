package com.zhaopw.standup

import org.junit.Assert.*
import org.junit.Test

class TimerStateTest {
    @Test fun pauseAndResumeRetainsRemainingAndIgnoresEditedDuration() {
        val running = TimerState().start(2, 1000)
        val paused = running.pause(31000)
        assertEquals(90000L, paused.remaining(999999))
        val resumed = paused.start(60, 200000)
        assertEquals(290000L, resumed.deadline)
        assertEquals(89999L, resumed.remaining(200001))
    }
    @Test fun delayedCallbackFinishesOnce() {
        val running = TimerState().start(1, 1000)
        assertNull(running.finishIfDue(60000, running.generation))
        val finished = running.finishIfDue(90000, running.generation)!!
        assertEquals(Phase.DUE, finished.phase)
        assertNull(finished.finishIfDue(100000, running.generation))
    }
    @Test fun resetInvalidatesOldAlarmAndUsesNewInput() {
        val old = TimerState().start(1, 0)
        val reset = old.reset(45)
        assertEquals(2700000L, reset.remaining(999999))
        val next = reset.start(45, 100)
        assertNull(next.finishIfDue(99999999, old.generation))
    }
    @Test fun durationAcceptsOnlyIntegerOneThrough999() {
        listOf("", "0", "1000", "-1", "1.5", "abc", "999999999999999999").forEach { assertNull(parseMinutes(it)) }
        assertEquals(1, parseMinutes(" 1 "))
        assertEquals(999, parseMinutes("999"))
    }
    @Test fun remainingUsesCeilingAndNeverGoesNegative() {
        val timer = TimerState().start(1, 1000)
        assertEquals("01:00", formatRemaining(timer.remaining(1001)))
        assertEquals("00:01", formatRemaining(timer.remaining(60999)))
        assertEquals("00:00", formatRemaining(timer.remaining(999999)))
    }
    @Test fun bootChangeClearsOldMonotonicDeadline() {
        val running = TimerState().start(1, 1000)
        val restored = running.afterBoot(3, 4)
        assertEquals(Phase.IDLE, restored.phase)
        assertEquals(60000L, restored.remaining(0))
        assertTrue(restored.generation > running.generation)
        assertEquals(running, running.afterBoot(3, 3))
    }
}
