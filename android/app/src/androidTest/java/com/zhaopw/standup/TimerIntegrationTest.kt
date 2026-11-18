package com.zhaopw.standup

import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.os.SystemClock
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
        }
    }
}
