package com.zhaopw.standup

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockCardTest {
 private val instrumentation = InstrumentationRegistry.getInstrumentation()
 private val context = instrumentation.targetContext
 private val manager = context.getSystemService(NotificationManager::class.java)
 private fun posted(): android.app.Notification {
  repeat(40) {
   manager.activeNotifications.firstOrNull { it.id == 10 }?.let { return it.notification }
   SystemClock.sleep(50)
  }
  error("Countdown notification was not posted")
 }
 private fun ready(): TimerRepository {
  instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
  val repo = TimerRepository(context).also { it.reset(2) }
  // Cancellation is asynchronous too. Do not accidentally tap the preceding test's card.
  repeat(40) {
   if (manager.activeNotifications.none { it.id == 10 || it.id == 11 }) return repo
   SystemClock.sleep(50)
  }
  error("Previous timer notification was not cancelled")
 }
 @Test fun pausedCardRestoresWhenReturningWithNotificationsAllowed() {
  val repo = ready()
  try {
   repo.start(2); val paused = repo.pause()
   // An absent card also occurs after permission/category was disabled then enabled.
   Notifications(context).cancelRunning()
   SystemClock.sleep(200)
   assertTrue(manager.activeNotifications.none { it.id == 10 })
   repo.refreshSchedule()
   assertNotNull(posted().contentView)
   assertEquals(paused, repo.read())
   assertEquals("继续", posted().actions[0].title.toString())
  } finally {
   repo.reset(30)
  }
 }
 @Test fun postedLayoutsApplyReapplyAndClockTicksWithoutReposting() {
  val repo = ready(); val running = repo.start(2)
  posted() // NotificationManager posts across a process boundary.
  val initial = manager.activeNotifications.first { it.id == 10 }
  val card = initial.notification
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   lateinit var clock: Chronometer
   var text = ""
   scenario.onActivity { activity ->
    val host = FrameLayout(activity)
    activity.setContentView(host)
    for (remote in listOf(card.contentView, card.bigContentView)) {
     val view = remote.apply(activity, host)
     remote.reapply(activity, view)
     clock = view.findViewById(R.id.card_clock)
     assertEquals(running.deadline, clock.base)
     assertTrue(clock.isCountDown)
     assertEquals(View.GONE, view.findViewById<View>(R.id.card_time).visibility)
     assertEquals(if (remote.layoutId == R.layout.notification_compact) "起身" else "stand up · 距离起身", view.findViewById<TextView>(R.id.card_status).text.toString())
     host.removeAllViews(); host.addView(view)
    }
   }
   SystemClock.sleep(1000) // Allow the Activity's onResume schedule refresh to settle.
   val postedAt = manager.activeNotifications.first { it.id == 10 }.postTime
   SystemClock.sleep(1100)
   scenario.onActivity { text = clock.text.toString() }
   SystemClock.sleep(1200)
   scenario.onActivity { assertNotEquals(text, clock.text.toString()) }
   assertEquals(postedAt, manager.activeNotifications.first { it.id == 10 }.postTime)
   posted().actions[0].actionIntent.send(); SystemClock.sleep(300)
   scenario.onActivity { activity ->
    val host = FrameLayout(activity)
    val paused = posted()
    for (remote in listOf(paused.contentView, paused.bigContentView)) {
     val view = remote.apply(activity, host); remote.reapply(activity, view)
     assertEquals(View.GONE, view.findViewById<View>(R.id.card_clock).visibility)
     assertEquals(formatRemaining(repo.read().savedRemaining), view.findViewById<TextView>(R.id.card_time).text.toString())
    }
   }
  }
  repo.reset(30)
 }
 @Test fun nativeCardPauseResumeAndStaleActions() {
  val repo = ready(); val running = repo.start(2); val card = posted()
  assertNotNull("Countdown must have a custom compact layout", card.contentView)
  assertNotNull(card.bigContentView)
  assertEquals(1, card.actions.size)
  val pause = card.actions[0].actionIntent
  assertTrue(pause.isImmutable)
  pause.send(); SystemClock.sleep(350)
  assertEquals(Phase.PAUSED, repo.read().phase)
  val paused = repo.read()
  pause.send(); SystemClock.sleep(150)
  assertEquals(paused, repo.read())
  val resume = posted().actions[0].actionIntent
  resume.send(); SystemClock.sleep(350)
  assertEquals(Phase.RUNNING, repo.read().phase)
  assertTrue(repo.read().generation > running.generation)
  resume.send(); SystemClock.sleep(150)
  val resumed = repo.read()
  assertEquals(Phase.RUNNING, resumed.phase)
  repo.reset(3); val fresh = repo.start(3)
  pause.send(); resume.send(); SystemClock.sleep(200)
  assertEquals(fresh, repo.read())
  repo.reset(30)
 }
 @Test fun largeFontCardReservesRoomForSystemActionByOmittingDecorativeHint() {
  val repo = ready()
  val config = android.content.res.Configuration(context.resources.configuration).apply { fontScale = 1.5f }
  val scaled = context.createConfigurationContext(config)
  val state = TimerState(phase=Phase.RUNNING, duration=59940000, deadline=SystemClock.elapsedRealtime()+59940000, generation=200)
  Notifications(scaled).running(state)
  val notification = posted()
  assertEquals(1, notification.actions.size)
  instrumentation.runOnMainSync {
   val view = notification.bigContentView.apply(scaled, FrameLayout(scaled))
   val density = scaled.resources.displayMetrics.density
   view.measure(View.MeasureSpec.makeMeasureSpec((220*density).toInt(), View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
   assertTrue("Large-font body must leave height for the system header and 48dp action", view.measuredHeight <= 140*density)
   assertEquals(state.deadline, view.findViewById<Chronometer>(R.id.card_clock).base)
  }
  repo.reset(30)
 }
 @Test fun expiredPauseDeliversDueAndOldAcknowledgementCannotResetNewRun() {
  val repo = ready(); repo.start(2)
  val pause = posted().actions[0].actionIntent
  context.getSharedPreferences("timer", Context.MODE_PRIVATE).edit().putLong("deadline", SystemClock.elapsedRealtime()-1).commit()
  pause.send(); SystemClock.sleep(500)
  assertEquals(Phase.DUE, repo.read().phase)
  val due = manager.activeNotifications.first { it.id == 11 }.notification
  assertNotNull(due.contentView); assertNotNull(due.bigContentView)
  val ack = due.actions[0].actionIntent
  repo.reset(3); val fresh = repo.start(3)
  ack.send(); SystemClock.sleep(200)
  assertEquals(fresh, repo.read())
  repo.reset(30)
 }
}
