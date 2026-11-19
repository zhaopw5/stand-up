package com.zhaopw.standup

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.media3.common.C
import androidx.media3.common.Player

internal class MusicSheet(private val context:Context,private val library:MusicLibrary,private val player:()->Player?,private val import:()->Unit,private val reselect:(Track)->Unit) {
 private val ui=Ui(context)
 val dialog=Dialog(context)
 private val title=ui.text("",18f,Palette.ink)
 private val progress=ui.text("",13f)
 private val play=ui.icon(R.drawable.ic_play,"播放音乐","sheet_play") { toggleMusic(player()) }
 private val tracks=ui.column()
 val seek=SeekBar(context).apply { tag="music_seek"; max=1000; progressTintList=android.content.res.ColorStateList.valueOf(Palette.brand); progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Palette.soft); thumbTintList=android.content.res.ColorStateList.valueOf(Palette.dark); minimumHeight=ui.dp(48); contentDescription="音乐播放进度" }
 private val modeButtons=mutableListOf<Button>()
 private var renderedMode:Int?=null
 private var dragging=false
 private var signature=""
 init {
  val container=ui.column().apply { background=ui.shape(Color.WHITE,24); setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(24)) }
  container.addView(ui.row().apply { addView(ui.text("背景音乐",22f,Palette.ink),LinearLayout.LayoutParams(0,-2,1f)); addView(ui.icon(R.drawable.ic_close,"收起音乐面板","sheet_close") { dialog.dismiss() }) })
  val body=ui.column(); body.addView(title)
  body.addView(ui.row().apply { ui.equal(this,ui.icon(R.drawable.ic_previous,"上一首","music_previous") { player()?.seekToPreviousMediaItem() }); ui.equal(this,play); ui.equal(this,ui.icon(R.drawable.ic_next,"下一首","music_next") { player()?.seekToNextMediaItem() }) })
  body.addView(seek,LinearLayout.LayoutParams(-1,ui.dp(48))); body.addView(progress)
  seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
   override fun onStartTrackingTouch(bar:SeekBar) { dragging=true }
   override fun onStopTrackingTouch(bar:SeekBar) { move(bar.progress); dragging=false }
   override fun onProgressChanged(bar:SeekBar,value:Int,fromUser:Boolean) { if(fromUser && !dragging) move(value) }
  })
  body.addView(ui.text("播放方式",14f,Palette.ink))
  val modes=ui.row().apply {
   if(context.resources.configuration.fontScale>1.2f || context.resources.configuration.screenWidthDp<360) orientation=LinearLayout.VERTICAL
  }
  listOf("顺序播放","单曲循环","列表循环").forEachIndexed { mode,label ->
   val button=ui.button(label,false,"music_mode_$mode") {
    library.mode=mode; player()?.repeatMode=mode; updateModes()
   }.apply { contentDescription=label }
   modeButtons.add(button)
   if(modes.orientation==LinearLayout.VERTICAL) modes.addView(button,LinearLayout.LayoutParams(-1,-2)) else ui.equal(modes,button)
  }
  body.addView(modes); updateModes()
  body.addView(ui.text("我的曲库",16f,Palette.ink)); body.addView(tracks)
  body.addView(ui.button("导入手机里的音频",true,"music_import",import),LinearLayout.LayoutParams(-1,-2))
  container.addView(ScrollView(context).apply { addView(body); isFillViewport=false },LinearLayout.LayoutParams(-1,0,1f))
  val safe=ui.column().apply { addView(container,LinearLayout.LayoutParams(-1,-1)) }; ui.insets(safe)
  dialog.setContentView(safe)
  dialog.window?.apply {
   setBackgroundDrawableResource(android.R.color.transparent); setGravity(Gravity.BOTTOM)
   setLayout(-1,(context.resources.displayMetrics.heightPixels*.88).toInt())
   setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
  }
 }
 fun show() { dialog.show(); dialog.window?.setLayout(-1,(context.resources.displayMetrics.heightPixels*.88).toInt()); update() }
 private fun move(value:Int) { player()?.let { if(it.duration>0) it.seekTo(it.duration*value/1000) } }
 private fun updateModes() {
  val mode=player()?.repeatMode ?: library.mode.coerceIn(0,2)
  if(renderedMode==mode) return
  renderedMode=mode
  modeButtons.forEachIndexed { index,button ->
   button.isSelected=index==mode
   button.background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x222D672F),ui.shape(if(button.isSelected) Palette.soft else Color.TRANSPARENT,12),null)
  }
 }
 fun update() {
  updateModes()
  val p=player(); title.text=p?.currentMediaItem?.mediaMetadata?.title ?: "选择一首音乐"
  val duration=p?.duration?.takeIf { it!=C.TIME_UNSET && it>0 } ?: 0L
  progress.text="${formatRemaining(p?.currentPosition ?: 0)} / ${formatRemaining(duration)}"
  seek.isEnabled=duration>0; if(!dragging) seek.progress=if(duration>0) ((p?.currentPosition ?: 0)*1000/duration).toInt() else 0
  play.setImageResource(if(p?.isPlaying==true) R.drawable.ic_pause else R.drawable.ic_play); play.contentDescription=if(p?.isPlaying==true) "暂停音乐" else "播放音乐"
  val signatureNow=library.tracks().joinToString { it.uri+library.trackError(it.uri) }+p?.currentMediaItem?.mediaId
  if(signatureNow==signature) return
  signature=signatureNow; tracks.removeAllViews()
  library.tracks().forEach { track ->
   val card=ui.column().apply { setPadding(0,ui.dp(6),0,ui.dp(10)) }
   card.addView(ui.button(track.title,false,"track_${track.uri}") {
    player()?.let { val index=library.tracks().indexOfFirst { t->t.uri==track.uri }; it.seekTo(index,0); it.prepare(); it.play() }
   }.apply { gravity=Gravity.START; isSelected=p?.currentMediaItem?.mediaId==track.uri; background=ui.shape(if(isSelected) Palette.soft else Color.TRANSPARENT,12) })
   library.trackError(track.uri)?.let { error ->
    card.addView(ui.text(error,13f,Color.rgb(153,62,55)))
    card.addView(ui.row().apply {
     ui.equal(this,ui.button("重试") { player()?.let { p->p.seekTo(library.tracks().indexOfFirst { it.uri==track.uri },0); p.prepare(); p.play() } })
     if(library.removable(track)) ui.equal(this,ui.button("重新选择",false,null) { reselect(track) })
    })
   }
   if(library.removable(track)) card.addView(ui.button("移除",false,null) {
    android.app.AlertDialog.Builder(context).setTitle("移除这首音乐？").setMessage(track.title).setNegativeButton("取消",null).setPositiveButton("移除") { _,_->
     val index=library.tracks().indexOfFirst { it.uri==track.uri }; if(index>=0) player()?.removeMediaItem(index)
     library.remove(track); signature=""; update()
    }.show()
   })
   tracks.addView(card)
  }
 }
}
internal fun toggleMusic(player:Player?) { player?.let { if(it.isPlaying) it.pause() else { if(it.playbackState==Player.STATE_ENDED) it.seekTo(0); it.prepare(); it.play() } } }
