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
import android.view.WindowInsetsController
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
    @Test fun lightScreenUsesDarkSystemBarIconsAfterRecreation() {
        if (Build.VERSION.SDK_INT < 30) return
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun assertDarkBars(activity: MainActivity) {
                val appearance = activity.window.insetsController!!.systemBarsAppearance
                assertTrue(appearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0)
                assertTrue(appearance and WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS != 0)
            }
            scenario.onActivity { assertDarkBars(it) }
            scenario.recreate()
            scenario.onActivity { assertDarkBars(it) }
        }
    }
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
        if (Build.VERSION.SDK_INT < 29) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java))).buildAsync()
            val player = future.get(20, TimeUnit.SECONDS)
            try {
                lateinit var seekControl: SeekBar
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
                    activity.findViewById<ViewGroup>(android.R.id.content).findViewWithTag<View>("music_library").performClick()
                    val seek = android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { descendants(it) }.filterIsInstance<SeekBar>().single()
                    seekControl = seek
                    assertTrue(seek.isEnabled)

                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    seekControl.requestFocus()
                    seekControl.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    seekControl.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                    assertTrue("Native keyboard must advance range",seekControl.progress>0)
                }
                instrumentation.waitForIdleSync()
                SystemClock.sleep(1000)
                instrumentation.runOnMainSync {
                    assertTrue("Keyboard progress must seek the actual session", player.currentPosition > 0)
                    assertTrue("Seek touch target must be at least 48dp", seekControl.height >= (48 * context.resources.displayMetrics.density).toInt())
                }
            } finally { instrumentation.runOnMainSync { player.release() } }
        }
    }
    private fun control(activity: MainActivity, tag: String): View = activity.findViewById<ViewGroup>(android.R.id.content).findViewWithTag(tag)
    private fun prepareUi() {
        TimerRepository(context).reset(30)
        context.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().clear().putBoolean("firstStartExplained",true).commit()
    }
    @Test fun presetsUpdateIdleAndPausedSelectionPreservesRemaining() {
        prepareUi()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                control(a,"preset_15").performClick()
                assertEquals("15:00",(control(a,"countdown") as TextView).text.toString())
                control(a,"timer_primary").performClick()
                assertEquals(Phase.RUNNING,TimerRepository(context).read().phase)
                control(a,"timer_primary").performClick()
                val remaining=TimerRepository(context).read().savedRemaining
                control(a,"preset_60").performClick()
                assertEquals(remaining,TimerRepository(context).read().savedRemaining)
                assertEquals("当前轮剩余 ${formatRemaining(remaining)}",(control(a,"paused_remaining") as TextView).text.toString())
                assertEquals("下一轮时长 60 分钟",(control(a,"next_duration") as TextView).text.toString())
                control(a,"timer_primary").performClick()
                assertEquals(remaining,TimerRepository(context).read().savedRemaining)
            }
            val deadline=TimerRepository(context).read().deadline
            scenario.recreate()
            assertEquals(deadline,TimerRepository(context).read().deadline)
            assertEquals(60,context.getSharedPreferences("ui",Context.MODE_PRIVATE).getInt("selectedMinutes",0))
        }
    }
    @Test fun dueAcknowledgementAndStaleNotificationAreSafe() {
        prepareUi()
        val repo=TimerRepository(context)
        val run=repo.start(1)
        context.getSharedPreferences("timer",Context.MODE_PRIVATE).edit().putLong("deadline",SystemClock.elapsedRealtime()-1).commit()
        assertTrue(repo.deliver(run.generation))
        val dueGeneration=repo.read().generation
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a -> control(a,"timer_primary").performClick() }
            assertEquals(Phase.IDLE,repo.read().phase)
            repo.start(30)
            val deadline=repo.read().deadline
            assertFalse(repo.acknowledge(dueGeneration,30))
            assertEquals(deadline,repo.read().deadline)
        }
    }
    @Test fun rebootBannerDismissalSurvivesRecreation() {
        prepareUi()
        context.getSharedPreferences("timer",Context.MODE_PRIVATE).edit().putBoolean("rebooted",true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a -> control(a,"reboot_dismiss").performClick() }
            assertFalse(TimerRepository(context).rebootNotice())
            scenario.recreate()
            assertFalse(TimerRepository(context).rebootNotice())
        }
    }
    private fun dialogRoot(): View = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.findViewById<View>(android.R.id.button1) != null }
    private fun step(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        scenario.onActivity(action)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    @Test fun cancellingEndAndAdjustKeepsDeadlineAndExpiredConfirmationCannotResetDue() {
        if(Build.VERSION.SDK_INT<29) return
        prepareUi()
        val repo=TimerRepository(context); val run=repo.start(30)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            step(scenario) { control(it,"timer_end").performClick() }
            step(scenario) { dialogRoot().findViewById<View>(android.R.id.button2).performClick() }
            assertEquals(run.deadline,repo.read().deadline)
            step(scenario) { control(it,"timer_adjust").performClick() }
            step(scenario) {
                dialogRoot().findViewWithTag<android.widget.EditText>("duration_input").setText("60")
                dialogRoot().findViewById<View>(android.R.id.button1).performClick()
            }
            step(scenario) { dialogRoot().findViewById<View>(android.R.id.button2).performClick() }
            assertEquals(run.deadline,repo.read().deadline)
            assertEquals(30,context.getSharedPreferences("ui",Context.MODE_PRIVATE).getInt("selectedMinutes",30))
            step(scenario) { control(it,"timer_end").performClick() }
            step(scenario) {
                context.getSharedPreferences("timer",Context.MODE_PRIVATE).edit().putLong("deadline",SystemClock.elapsedRealtime()-1).commit()
                dialogRoot().findViewById<View>(android.R.id.button1).performClick()
            }
            assertEquals(Phase.DUE,repo.read().phase)
        }
    }
    @Test fun customDurationRejectsOutOfBoundsAndAcceptsBothLimits() {
        if(Build.VERSION.SDK_INT<29) return
        prepareUi()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            step(scenario) { control(it,"duration_custom").performClick() }
            listOf("0","1000").forEach { bad ->
                step(scenario) {
                    val input=dialogRoot().findViewWithTag<android.widget.EditText>("duration_input")
                    input.setText(bad); dialogRoot().findViewById<View>(android.R.id.button1).performClick()
                    assertNotNull(input.error); assertEquals(Phase.IDLE,TimerRepository(context).read().phase)
                }
            }
            step(scenario) {
                dialogRoot().findViewWithTag<android.widget.EditText>("duration_input").setText("1")
                dialogRoot().findViewById<View>(android.R.id.button1).performClick()
            }
            step(scenario) { assertEquals("01:00",(control(it,"countdown") as TextView).text.toString()); control(it,"duration_custom").performClick() }
            step(scenario) {
                dialogRoot().findViewWithTag<android.widget.EditText>("duration_input").setText("999")
                dialogRoot().findViewById<View>(android.R.id.button1).performClick()
            }
            step(scenario) { assertEquals("999:00",(control(it,"countdown") as TextView).text.toString()) }
        }
    }
    @Test fun firstStartCanSkipPermissionsWithoutChainedPopup() {
        if(Build.VERSION.SDK_INT<33) return
        prepareUi()
        context.getSharedPreferences("ui",Context.MODE_PRIVATE).edit().putBoolean("firstStartExplained",false).commit()
        Notifications(context).createChannels()
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(Notifications.DUE_CHANNEL,"起身提醒",NotificationManager.IMPORTANCE_NONE))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            step(scenario) { control(it,"timer_primary").performClick() }
            assertEquals(Phase.IDLE,TimerRepository(context).read().phase)
            step(scenario) { dialogRoot().findViewById<View>(android.R.id.button2).performClick() }
            assertEquals(Phase.RUNNING,TimerRepository(context).read().phase)
            assertTrue(context.getSharedPreferences("ui",Context.MODE_PRIVATE).getBoolean("firstStartExplained",false))
        }
    }
    @Test fun sameUriRecoveryRevalidatesGrantAndFailurePreservesLibrary() {
        val prefs=context.getSharedPreferences("music",Context.MODE_PRIVATE)
        val original=prefs.getString("tracks","[]")
        val missing="content://com.zhaopw.missing/audio"
        prefs.edit().remove("playlistSchema").putString("tracks","[{\"uri\":\"$missing\",\"title\":\"Lost audio\"}]").commit()
        try {
            val library=MusicLibrary(context); val before=library.tracks()
            assertThrows(Exception::class.java) { library.import(android.net.Uri.parse(missing)) }
            assertEquals(before,library.tracks())
            assertThrows(Exception::class.java) { library.replace(missing,android.net.Uri.parse("content://com.zhaopw.missing/replacement")) }
            assertEquals(before,library.tracks())
        } finally { prefs.edit().remove("playlistSchema").putString("tracks",original).commit() }
    }
    @Test fun cancelledReselectSurvivesRecreationWithoutMutatingTimerOrLibrary() {
        prepareUi()
        val repo=TimerRepository(context); val deadline=repo.start(30).deadline
        val before=MusicLibrary(context).tracks()
        val prefs=context.getSharedPreferences("ui",Context.MODE_PRIVATE)
        prefs.edit().putString("reselectUri","content://missing/old-track").commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            assertEquals("content://missing/old-track",prefs.getString("reselectUri",null))
            scenario.onActivity { a ->
                MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,android.content.Intent::class.java).apply { isAccessible=true }
                    .invoke(a,41,android.app.Activity.RESULT_CANCELED,null)
            }
            assertNull(prefs.getString("reselectUri",null))
            assertEquals(before,MusicLibrary(context).tracks())
            assertEquals(deadline,repo.read().deadline)
        }
    }
    @Test fun deferredReplacementIsAppliedWhenActivityConnectsToExistingSession() {
        prepareUi()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val library=MusicLibrary(context)
        val target=library.tracks().first().uri
        val old="legacy-track-before-reselection"
        val future=MediaController.Builder(context,SessionToken(context,ComponentName(context,MusicService::class.java))).buildAsync()
        val player=future.get(20,TimeUnit.SECONDS)
        val ready=CountDownLatch(1)
        val replaced=CountDownLatch(1)
        try {
            instrumentation.runOnMainSync {
                player.addListener(object:Player.Listener {
                    override fun onPlaybackStateChanged(state:Int) { if(state==Player.STATE_READY) ready.countDown() }
                    override fun onMediaItemTransition(item:androidx.media3.common.MediaItem?,reason:Int) { if(item?.mediaId==target) replaced.countDown() }
                })
                player.pause()
                player.setMediaItems(listOf(androidx.media3.common.MediaItem.Builder().setMediaId(old).setUri(target).build()),0,9000)
                player.prepare()
            }
            assertTrue(ready.await(20,TimeUnit.SECONDS))
            // This is the persisted successful-picker boundary, before the Activity/controller exists.
            context.getSharedPreferences("music",Context.MODE_PRIVATE).edit().remove("playlistSchema").putString("tracks","[]")
                .putString("pendingReplacements",org.json.JSONObject().put(old,target).toString())
                .putBoolean("sessionSyncPending",true).putString("selected",old).putLong("position",9000).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue("Connecting Activity must apply the deferred replacement",replaced.await(10,TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(target,player.currentMediaItem?.mediaId)
                    assertEquals(0L,player.currentPosition)
                    assertFalse(player.playWhenReady)
                }
                assertFalse(library.needsSessionSync())
                assertTrue(library.pendingReplacements().isEmpty())
                scenario.recreate()
                instrumentation.runOnMainSync { assertEquals(target,player.currentMediaItem?.mediaId); assertFalse(player.playWhenReady) }
            }
        } finally { instrumentation.runOnMainSync { player.release() } }
    }
    @Test fun pendingReplacementMapSurvivesAdditionalLibraryMutationAndRecreation() {
        val prefs=context.getSharedPreferences("music",Context.MODE_PRIVATE)
        val original=prefs.getString("tracks","[]")
        try {
            prefs.edit().remove("playlistSchema").putString("tracks","[{\"uri\":\"content://missing/other\",\"title\":\"Other\"}]")
                .putString("pendingReplacements","{\"old\":\"middle\",\"middle\":\"new\"}").putBoolean("sessionSyncPending",true).commit()
            val library=MusicLibrary(context)
            library.remove(library.tracks().last())
            assertEquals(mapOf("old" to "middle","middle" to "new"),MusicLibrary(context).pendingReplacements())
            assertTrue(MusicLibrary(context).needsSessionSync())
            library.markSessionSynchronized()
            assertFalse(MusicLibrary(context).needsSessionSync())
            assertTrue(MusicLibrary(context).pendingReplacements().isEmpty())
        } finally { prefs.edit().remove("playlistSchema").putString("tracks",original).remove("pendingReplacements").putBoolean("sessionSyncPending",false).commit() }
    }
    @Test fun visibleMusicModesControlSessionAndSurviveRecreation() {
        if(Build.VERSION.SDK_INT<29) return
        prepareUi()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val future=MediaController.Builder(context,SessionToken(context,ComponentName(context,MusicService::class.java))).buildAsync()
        val player=future.get(20,TimeUnit.SECONDS)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun modeButtons(): List<View> {
                    fun descendants(v:View):List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
                    val views=android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap { descendants(it) }
                    return (0..2).map { mode -> views.single { it.tag=="music_mode_$mode" } }
                }
                step(scenario) { control(it,"music_library").performClick() }
                listOf(1,2,0,2).forEach { mode ->
                    val changed=CountDownLatch(1)
                    instrumentation.runOnMainSync {
                        player.addListener(object:Player.Listener {
                            override fun onRepeatModeChanged(repeatMode:Int) { if(repeatMode==mode) changed.countDown() }
                        })
                        modeButtons()[mode].performClick()
                    }
                    assertTrue("Visible mode must change actual session",changed.await(5,TimeUnit.SECONDS))
                    step(scenario) {
                        assertEquals(mode,player.repeatMode)
                        assertEquals(mode,MusicLibrary(context).mode)
                        modeButtons().forEachIndexed { index,button ->
                            assertEquals(index==mode,button.isSelected)
                            assertTrue(button.height >= (48*context.resources.displayMetrics.density).toInt())
                            assertTrue(button.contentDescription.toString().isNotBlank())
                        }
                    }
                }
                scenario.recreate()
                step(scenario) { control(it,"music_library").performClick() }
                step(scenario) { assertTrue(modeButtons()[2].isSelected); assertEquals(2,player.repeatMode) }
            }
        } finally { instrumentation.runOnMainSync { player.repeatMode=0; MusicLibrary(context).mode=0; player.release() } }
    }

}
