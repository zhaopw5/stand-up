package com.zhaopw.standup

import android.app.NotificationManager
import android.app.NotificationChannel
import android.Manifest
import android.os.Build
import android.content.Context
import android.content.ComponentName
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class TimerIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun repositorySurvivesRecreationAndRejectsStaleDelivery() {
        val first = TimerRepository(context)
        first.reset(2)
        val running = first.start(2)
        val recreated = TimerRepository(context).read()
        assertEquals(running.deadline, recreated.deadline)
        assertTrue(recreated.remaining(SystemClock.elapsedRealtime()) in 1..120000)
        first.reset(3)
        assertFalse(TimerRepository(context).deliver(running.generation))
        assertEquals(180000L, first.read().remaining(SystemClock.elapsedRealtime()))
    }
    @Test fun reminderChannelIsAudibleAndRunningChannelIsSilent() {
        Notifications(context).createChannels()
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(Notifications.DUE_CHANNEL).sound)
        assertNull(manager.getNotificationChannel(Notifications.TIMER_CHANNEL).sound)
    }
    @Test fun disabledDueChannelIsNotReportedAsReady() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val notifications = Notifications(context)
        notifications.createChannels()
        assertTrue("App permission is enabled independently of channel state", notifications.allowed())
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(Notifications.DUE_CHANNEL, "起身提醒", NotificationManager.IMPORTANCE_NONE))
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(Notifications.DUE_CHANNEL).importance)
        assertFalse(notifications.remindersAllowed())
        // The test intentionally leaves this user-visible state for inspection.
        // Clear test app data before manual validation or a subsequent suite run.
    }
    @Test fun activityRecreationKeepsRunningDeadline() {
        val repo = TimerRepository(context)
        repo.reset(15)
        val started = repo.start(15)
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.recreate()
            assertEquals(started.deadline, TimerRepository(context).read().deadline)
            assertEquals(Phase.RUNNING, TimerRepository(context).read().phase)
        }
        repo.reset(30)
    }
    @Test fun dueTransitionPausesSessionAndRejectsDuplicateAlarm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
        val player = future.get(20, TimeUnit.SECONDS)
        try {
            val playing = CountDownLatch(1)
            val paused = CountDownLatch(1)
            instrumentation.runOnMainSync {
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) playing.countDown() }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { if (!playWhenReady) paused.countDown() }
                })
                player.prepare()
                player.play()
            }
            assertTrue("Built-in track must become playable", playing.await(20, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val repo = TimerRepository(context)
                repo.reset(1)
                val running = repo.start(1)
                context.getSharedPreferences("timer", Context.MODE_PRIVATE).edit().putLong("deadline", SystemClock.elapsedRealtime() - 1).commit()
                assertTrue(repo.deliver(running.generation))
                assertEquals(Phase.DUE, repo.read().phase)
                assertFalse(repo.deliver(running.generation))
                repo.reset(30)
            }
            assertTrue("Due reminder must pause the media session", paused.await(5, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { player.release() }
            scenario.close()
        }
    }
    @Test fun keyboardCanSeekAndAccessibleCountdownContainsRemainingTime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
            val player = future.get(20, TimeUnit.SECONDS)
            try {
                val ready = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    player.pause()
                    player.seekTo(0)
                    player.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
                    })
                    if (player.playbackState == Player.STATE_READY) ready.countDown() else player.prepare()
                }
                assertTrue(ready.await(20, TimeUnit.SECONDS))
                // Allow the screen's 500ms presentation refresh to enable the seek control.
                SystemClock.sleep(600)
                scenario.onActivity { activity ->
                    fun descendants(view: View): List<View> = listOf(view) +
                        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                    val views = descendants(activity.findViewById(android.R.id.content))
                    val countdown = views.filterIsInstance<TextView>().first { it.contentDescription?.startsWith("剩余时间 ") == true }
                    assertEquals("剩余时间 ${countdown.text}", countdown.contentDescription.toString())
                    val seek = views.filterIsInstance<SeekBar>().single()
                    assertTrue(seek.isEnabled)
                    seek.requestFocus()
                    seek.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    seek.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                }
                SystemClock.sleep(300)
                instrumentation.runOnMainSync { assertTrue("Keyboard progress must seek the actual session", player.currentPosition > 0) }
            } finally { instrumentation.runOnMainSync { player.release() } }
        }
    }
}
