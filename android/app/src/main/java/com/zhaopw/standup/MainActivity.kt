package com.zhaopw.standup

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity:Activity() {
 private lateinit var repository:TimerRepository
 private lateinit var library:MusicLibrary
 private lateinit var ui:Ui
 private lateinit var screen:TimerScreen
 private var sheet:MusicSheet?=null
 private var permissions:PermissionPage?=null
 private var controller:MediaController?=null
 private var future:ListenableFuture<MediaController>?=null
 private val handler=Handler(Looper.getMainLooper())
 private var visible=false
 private var selected=30
 private val prefs by lazy { getSharedPreferences("ui",MODE_PRIVATE) }
 private val tick=object:Runnable { override fun run() { if(visible) { update(); handler.postDelayed(this,500) } } }
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  repository=TimerRepository(this); library=MusicLibrary(this); ui=Ui(this)
  selected=prefs.getInt("selectedMinutes",(repository.read().duration/60000).toInt()).coerceIn(1,999)
  Notifications(this).createChannels(); WindowCompat.setDecorFitsSystemWindows(window,false)
  window.statusBarColor=Color.TRANSPARENT; window.navigationBarColor=Color.TRANSPARENT
  screen=TimerScreen(ui,::showPermissions,::primary,::secondary,::adjust,::select,::custom,::showMusic,{ toggleMusic(controller); update() },{ repository.clearRebootNotice(); update() })
  val scroll=ScrollView(this).apply { isFillViewport=true; setBackgroundColor(Palette.bg); addView(screen.content) }; ui.insets(scroll); setContentView(scroll); bars()
  future=MediaController.Builder(this,SessionToken(this,ComponentName(this,MusicService::class.java))).buildAsync().also { f->f.addListener({
   if(!isDestroyed) runCatching { f.get() }.onSuccess { controller=it; update() }.onFailure { screen.musicTitle.text="播放器连接失败，重新打开应用重试" }
  },ContextCompat.getMainExecutor(this)) }
  update()
 }
 private fun bars() { WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=true; isAppearanceLightNavigationBars=true } }
 override fun onWindowFocusChanged(hasFocus:Boolean) { super.onWindowFocusChanged(hasFocus); if(hasFocus) bars() }
 override fun onResume() { super.onResume(); visible=true; repository.refreshSchedule(); handler.post(tick); permissions?.refresh(); bars() }
 override fun onPause() { visible=false; handler.removeCallbacks(tick); super.onPause() }
 override fun onDestroy() { sheet?.dialog?.dismiss(); permissions?.dismiss(); future?.let { MediaController.releaseFuture(it) }; super.onDestroy() }
 private fun select(value:Int) { selected=value; prefs.edit().putInt("selectedMinutes",value).apply(); update() }
 private fun primary() {
  val state=repository.read()
  when(state.phase) {
   Phase.RUNNING->repository.pause()
   Phase.PAUSED->repository.start(selected)
   Phase.DUE->repository.acknowledge(state.generation,selected)
   Phase.IDLE-> {
    if(!ready() && !prefs.getBoolean("firstStartExplained",false)) {
     prefs.edit().putBoolean("firstStartExplained",true).apply()
     AlertDialog.Builder(this).setTitle("让后台提醒更可靠").setMessage("通知用于到时提醒，准确提醒用于减少后台延迟。你也可以先开始计时，稍后再设置。")
      .setNegativeButton("先开始计时") { _,_-> if(repository.read().phase==Phase.IDLE) { repository.start(selected); update() } }
      .setPositiveButton("了解并设置") { _,_-> showPermissions() }.show()
    } else repository.start(selected)
   }
  }; update()
 }
 private fun secondary() { val snapshot=repository.read(); confirm(snapshot,if(snapshot.phase==Phase.PAUSED) "重新开始这一轮？" else "结束这一轮？",if(snapshot.phase==Phase.PAUSED) "将放弃当前进度，按所选 $selected 分钟重新开始。" else "当前计时进度将被清除。") {
  repository.reset(selected); if(snapshot.phase==Phase.PAUSED) repository.start(selected)
 } }
 private fun confirm(snapshot:TimerState,title:String,detail:String,action:()->Unit) {
  AlertDialog.Builder(this).setTitle(title).setMessage(detail).setNegativeButton("取消",null).setPositiveButton("确认") { _,_->
   repository.deliver(snapshot.generation)
   if(canConfirmTimerChange(snapshot,repository.read(),SystemClock.elapsedRealtime())) action()
   update()
  }.show()
 }
 private fun custom()=durationDialog(false)
 private fun adjust()=durationDialog(repository.read().phase==Phase.RUNNING)
 private fun durationDialog(restart:Boolean) {
  val snapshot=repository.read()
  val input=EditText(this).apply { tag="duration_input"; inputType=InputType.TYPE_CLASS_NUMBER; setSingleLine(); setText(selected.toString()); selectAll(); hint="1–999"; contentDescription="倒计时时长，1到999整数分钟"; minHeight=ui.dp(48) }
  val wrap=ui.column().apply { setPadding(ui.dp(24),ui.dp(8),ui.dp(24),0); addView(input) }
  val dialog=AlertDialog.Builder(this).setTitle(if(restart) "调整本轮时长" else "自定义时长（分钟）").setView(wrap).setNegativeButton("取消",null).setPositiveButton("确定",null).create()
  dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
   val value=parseMinutes(input.text.toString())
   if(value==null) { input.error="请输入 1–999 的整数分钟"; return@setOnClickListener }
   dialog.dismiss()
   if(restart) confirm(snapshot,"重新开始计时？","将放弃当前进度，重新计时 $value 分钟。") { select(value); repository.reset(value); repository.start(value) }
   else if(canConfirmTimerChange(snapshot,repository.read(),SystemClock.elapsedRealtime())) select(value)
  } }; dialog.show()
 }
 private fun ready()=Notifications(this).remindersAllowed() && AlarmScheduler(this).exactAllowed()
 private fun showPermissions() { permissions?.dismiss(); permissions=PermissionPage(this); permissions?.show() }
 private fun showMusic() { sheet?.dialog?.dismiss(); sheet=MusicSheet(this,library,{controller},::importMusic,::reselectMusic); sheet?.show() }
 private fun importMusic() { prefs.edit().remove("reselectUri").apply(); pickMusic(true) }
 private fun reselectMusic(track:Track) { prefs.edit().putString("reselectUri",track.uri).apply(); pickMusic(false) }
 private fun pickMusic(multiple:Boolean) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),41) }
 private fun update() {
  var state=repository.read(); if(repository.deliver(state.generation)) state=repository.read()
  screen.render(state,selected,SystemClock.elapsedRealtime(),ready(),repository.rebootNotice())
  controller?.let { p-> val title=library.trackError(p.currentMediaItem?.mediaId) ?: p.currentMediaItem?.mediaMetadata?.title ?: "选择一首音乐"
   if(screen.musicTitle.text.toString()!=title.toString()) screen.musicTitle.text=title
   screen.musicPlay.setImageResource(if(p.isPlaying) R.drawable.ic_pause else R.drawable.ic_play); screen.musicPlay.contentDescription=if(p.isPlaying) "暂停音乐" else "播放音乐" }
  if(sheet?.dialog?.isShowing==true) sheet?.update()
 }
 @Deprecated("Native document picker")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data); if(requestCode!=41) return
  val replacing=prefs.getString("reselectUri",null); prefs.edit().remove("reselectUri").apply()
  if(resultCode!=RESULT_OK || data==null) return
  val uris=data.clipData?.let { c->(0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
  val errors=mutableListOf<String>(); var imported=false; var replacementUri:String?=null
  (if(replacing!=null) uris.take(1) else uris.distinct()).forEach { uri->runCatching {
   if(replacing!=null) { library.replace(replacing,uri); replacementUri=uri.toString() } else library.import(uri)
   imported=true
  }.onFailure { errors.add("无法读取音频或保留访问权限，请重新选择文件。") } }
  if(imported) controller?.let { p->val selected=p.currentMediaItem?.mediaId; val replacesCurrent=replacing!=null && selected==replacing
   val position=if(replacesCurrent) 0L else p.currentPosition; val playing=p.playWhenReady; val tracks=library.tracks()
   val target=if(replacesCurrent) replacementUri else selected
   p.setMediaItems(tracks.map { it.mediaItem() },tracks.indexOfFirst { it.uri==target }.coerceAtLeast(0),position); p.prepare(); p.playWhenReady=playing
  }
  sheet?.update(); update(); if(errors.isNotEmpty()) AlertDialog.Builder(this).setMessage(errors.joinToString("\n")).setPositiveButton("知道了",null).show()
 }
}
