package com.zhaopw.standup
import org.junit.Assert.*
import org.junit.Test
class TimerUiStateTest {
 @Test fun idleSelectionImmediatelyChangesDisplayedTime() { assertEquals(900000L, TimerUiState.from(TimerState(),15,0).remaining); assertEquals("开始专注",TimerUiState.from(TimerState(),15,0).primary) }
 @Test fun activeTimeIgnoresNextSelectionAndContinueKeepsRemaining() { val p=TimerState().start(30,100).pause(60100); assertEquals(1740000L,TimerUiState.from(p,60,99999).remaining); assertEquals("继续专注",TimerUiState.from(p,60,0).primary); assertEquals(1740000L,p.start(60,99999).savedRemaining) }
 @Test fun dueOnlyOffersAcknowledgement() { val ui=TimerUiState.from(TimerState(phase=Phase.DUE),30,0); assertEquals("知道了",ui.primary); assertFalse(ui.showDurations) }
 @Test fun runningUsesDeadlineAndPauseAction() { val ui=TimerUiState.from(TimerState().start(1,100),999,1100); assertEquals(59000L,ui.remaining); assertEquals("暂停计时",ui.primary) }
 @Test fun staleConfirmationCannotReplaceDueOrNewRun() { val r=TimerState().start(1,100); assertTrue(canConfirmTimerChange(r,r,200)); assertFalse(canConfirmTimerChange(r,r,60100)); assertFalse(canConfirmTimerChange(r,r.reset(2).start(2,200),201)) }
 @Test fun minuteBounds() { assertEquals(1,parseMinutes("1")); assertEquals(999,parseMinutes("999")); assertNull(parseMinutes("0")); assertNull(parseMinutes("1000")) }
}
