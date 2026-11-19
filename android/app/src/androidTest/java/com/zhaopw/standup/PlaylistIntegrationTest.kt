package com.zhaopw.standup
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PlaylistIntegrationTest {
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 @Test fun investigateCustomBounds() {
  TimerRepository(context).reset(30)
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   listOf(false,true).forEach { paused ->
    if(paused) { TimerRepository(context).start(30); TimerRepository(context).pause(); scenario.recreate() }
    scenario.onActivity { a ->
     val root=a.findViewById<ViewGroup>(android.R.id.content)
     listOf("preset_45","preset_60","duration_custom").forEach { tag ->
      val v=root.findViewWithTag<View>(tag); val r=Rect(); v.getGlobalVisibleRect(r)
      val outline=android.graphics.Outline(); v.outlineProvider?.getOutline(v,outline)
      Log.i("PlaylistBounds","paused=$paused font=${a.resources.configuration.fontScale} width=${a.resources.configuration.screenWidthDp} tag=$tag rect=$r measured=${v.width}x${v.height} elevation=${v.elevation} z=${v.translationZ} animator=${v.stateListAnimator} outlineAlpha=${outline.alpha} clip=${(v.parent as ViewGroup).clipChildren}")
     }
    }
   }
  }
 }
 @Test fun builtinDeletionPersistsWithoutImplicitRestoration() {
  val prefs=context.getSharedPreferences("music",Context.MODE_PRIVATE)
  prefs.edit().clear().commit()
  val library=MusicLibrary(context); val builtin=library.tracks().single()
  library.remove(builtin)
  assertTrue(MusicLibrary(context).tracks().isEmpty())
  assertNull(MusicLibrary(context).selected())
  library.restoreBuiltIn(); assertEquals(listOf(builtin),MusicLibrary(context).tracks())
  prefs.edit().clear().commit()
 }
 @Test fun legacyMigrationPreservesOrderSelectionAndMode() {
  context.stopService(android.content.Intent(context,MusicService::class.java)); android.os.SystemClock.sleep(600)
  val prefs=context.getSharedPreferences("music",Context.MODE_PRIVATE)
  prefs.edit().clear().putString("tracks","[{\"uri\":\"content://a\",\"title\":\"A\"},{\"uri\":\"content://b\",\"title\":\"B\"}]")
   .putString("selected","content://b").putLong("position",1234).putInt("mode",1).commit()
  try {
   val library=MusicLibrary(context); val builtin=library.tracks().first()
   assertEquals(listOf(builtin.uri,"content://a","content://b"),library.tracks().map { it.uri })
   assertEquals("content://b",library.selected()); assertEquals(1234L,library.position()); assertEquals(1,library.mode)
   library.move(builtin.uri,1)
   assertEquals(listOf("content://a",builtin.uri,"content://b"),MusicLibrary(context).tracks().map { it.uri })
   library.move("content://a",-1); library.move("content://b",1)
   assertEquals(listOf("content://a",builtin.uri,"content://b"),library.tracks().map { it.uri })
   library.remove(library.tracks().first()); assertEquals("content://b",library.selected()); assertEquals(1234L,library.position())
   library.remove(library.tracks().last()); assertEquals(builtin.uri,library.selected()); assertEquals(0L,library.position())
   library.remove(builtin); assertTrue(MusicLibrary(context).tracks().isEmpty())
  } finally { prefs.edit().clear().commit() }
 }
 @Test fun customHasIndependentFlatTouchTargetAndPresetsRemainSelectable() {
  TimerRepository(context).reset(30)
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   listOf(false,true).forEach { paused ->
    if(paused) { TimerRepository(context).start(30); TimerRepository(context).pause(); scenario.recreate() }
    scenario.onActivity { a ->
     val root=a.findViewById<ViewGroup>(android.R.id.content)
     val preset=root.findViewWithTag<View>("preset_60"); val custom=root.findViewWithTag<View>("duration_custom")
     assertTrue(custom.top-preset.parent.let { it as View }.bottom >= (8*a.resources.displayMetrics.density).toInt())
     assertNull(custom.stateListAnimator); assertEquals(0f,custom.elevation); assertEquals(0f,custom.translationZ)
     assertTrue(custom.height >= (48*a.resources.displayMetrics.density).toInt())
     preset.performClick(); assertTrue(preset.isSelected)
     custom.performClick(); assertEquals(if(paused) Phase.PAUSED else Phase.IDLE,TimerRepository(context).read().phase)
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    scenario.onActivity { android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.findViewById<View>(android.R.id.button2)!=null }.findViewById<View>(android.R.id.button2).performClick() }
   }
  }
 }
 @Test fun liveSessionReorderRemovalAndEmptyRecreation() {
  val prefs=context.getSharedPreferences("music",Context.MODE_PRIVATE); prefs.edit().clear().commit()
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val library=MusicLibrary(context); val builtin=library.tracks().single()
  val files=(1..2).map { n -> java.io.File(context.filesDir,"playlist-test-$n.wav").apply {
   context.resources.openRawResource(R.raw.quiet_steps_loop).use { input -> outputStream().use { input.copyTo(it) } }
  } }
  val custom=files.mapIndexed { n,f -> Track(android.net.Uri.fromFile(f).toString(),"Track $n") }
  val all=listOf(builtin)+custom
  val json=org.json.JSONArray(); all.forEach { json.put(org.json.JSONObject().put("uri",it.uri).put("title",it.title)) }
  prefs.edit().putString("tracks",json.toString()).putInt("playlistSchema",1).putBoolean("sessionSyncPending",true).commit()
  val future=androidx.media3.session.MediaController.Builder(context,androidx.media3.session.SessionToken(context,android.content.ComponentName(context,MusicService::class.java))).buildAsync()
  val player=future.get(20,java.util.concurrent.TimeUnit.SECONDS)
  fun waitUntil(message:String,condition:()->Boolean) {
   val deadline=android.os.SystemClock.elapsedRealtime()+15000
   var ok=false
   while(android.os.SystemClock.elapsedRealtime()<deadline) {
    instrumentation.runOnMainSync { ok=condition() }; if(ok) return; android.os.SystemClock.sleep(100)
   }; fail(message)
  }
  try {
   ActivityScenario.launch(MainActivity::class.java).use { scenario ->
    waitUntil("Initial three tracks") { player.mediaItemCount==3 }
    instrumentation.runOnMainSync { player.seekTo(1,5000); player.prepare(); player.play() }
    waitUntil("Playback ready") { player.isPlaying && player.currentPosition>=5000 }
    library.move(custom[0].uri,1)
    waitUntil("Actual session order changes preserving playing track") { player.getMediaItemAt(2).mediaId==custom[0].uri && player.currentMediaItem?.mediaId==custom[0].uri && player.isPlaying && player.currentPosition>=5000 }
    instrumentation.runOnMainSync { player.pause() }
    library.move(custom[0].uri,-1)
    waitUntil("Reorder must keep pause and progress") { player.getMediaItemAt(1).mediaId==custom[0].uri && !player.playWhenReady && player.currentPosition>=5000 }
    library.remove(builtin)
    waitUntil("Removing another track preserves progress") { player.mediaItemCount==2 && player.currentMediaItem?.mediaId==custom[0].uri && player.currentPosition>=5000 }
    library.savePosition(custom[0].uri,6000); library.remove(custom[0])
    waitUntil("Deleted current selects next paused at zero") { player.mediaItemCount==1 && player.currentMediaItem?.mediaId==custom[1].uri && !player.playWhenReady && player.currentPosition==0L }
    library.remove(custom[1])
    waitUntil("Deleting all clears real player") { player.mediaItemCount==0 && !player.playWhenReady }
    instrumentation.runOnMainSync { toggleMusic(player); assertFalse(player.playWhenReady) }
    scenario.recreate()
    waitUntil("Empty activity recreation remains empty") { player.mediaItemCount==0 }
   }
  } finally { instrumentation.runOnMainSync { player.release() } }
  context.stopService(android.content.Intent(context,MusicService::class.java)); android.os.SystemClock.sleep(500)
  val next=androidx.media3.session.MediaController.Builder(context,androidx.media3.session.SessionToken(context,android.content.ComponentName(context,MusicService::class.java))).buildAsync().get(20,java.util.concurrent.TimeUnit.SECONDS)
  try { instrumentation.runOnMainSync { assertEquals(0,next.mediaItemCount); assertFalse(next.playWhenReady) } }
  finally { instrumentation.runOnMainSync { next.release() }; prefs.edit().clear().commit(); files.forEach { it.delete() } }
 }
 @Test fun realTouchSeparatesPresetAndCustomDialog() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  TimerRepository(context).reset(30)
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   fun tap(tag:String) {
    val r=Rect()
    scenario.onActivity { a ->
     val v=a.findViewById<ViewGroup>(android.R.id.content).findViewWithTag<View>(tag)
     val scroll=a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as android.widget.ScrollView
     val target=Rect(0,0,v.width,v.height); scroll.offsetDescendantRectToMyCoords(v,target)
     scroll.scrollTo(0,target.centerY()-scroll.height/2)
    }
    instrumentation.waitForIdleSync()
    scenario.onActivity { a ->
     val v=a.findViewById<ViewGroup>(android.R.id.content).findViewWithTag<View>(tag)
     assertTrue(v.getGlobalVisibleRect(r)); assertEquals("Full visible target $tag",v.height,r.height())
    }
    val now=android.os.SystemClock.uptimeMillis()
    listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP).forEach { action ->
     val event=android.view.MotionEvent.obtain(now,now+20,action,r.exactCenterX(),r.exactCenterY(),0)
     instrumentation.sendPointerSync(event); event.recycle()
    }
    instrumentation.waitForIdleSync()
   }
   listOf(false,true).forEach { paused ->
    if(paused) { TimerRepository(context).start(30); TimerRepository(context).pause(); scenario.recreate() }
    tap("preset_60")
    scenario.onActivity { a -> assertTrue(a.findViewById<ViewGroup>(android.R.id.content).findViewWithTag<View>("preset_60").isSelected) }
    tap("duration_custom")
    scenario.onActivity {
     val roots=android.view.inspector.WindowInspector.getGlobalWindowViews()
     assertTrue(roots.any { it.findViewWithTag<View>("duration_input")!=null })
     roots.last { it.findViewById<View>(android.R.id.button2)!=null }.findViewById<View>(android.R.id.button2).performClick()
     assertEquals(if(paused) Phase.PAUSED else Phase.IDLE,TimerRepository(context).read().phase)
    }
   }
  }
 }
}

