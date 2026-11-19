package com.zhaopw.standup

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*

internal class TimerScreen(private val ui:Ui,settings:()->Unit,primary:()->Unit,end:()->Unit,adjust:()->Unit,select:(Int)->Unit,custom:()->Unit,music:()->Unit,toggle:()->Unit,dismissBoot:()->Unit) {
 private var lastRender:List<Any>?=null
 private var lastSelection:Int?=null
 val content=ui.column().apply { setPadding(ui.dp(24),ui.dp(18),ui.dp(24),ui.dp(24)) }
 private val timer=ui.card()
 private val status=ui.text("",14f,Palette.brand).apply { gravity=Gravity.CENTER }
 val countdown=ui.text("30:00",60f,Palette.ink).apply { gravity=Gravity.CENTER; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); fontFeatureSettings="tnum"; maxLines=1; tag="countdown" }
 private val roundTotal=ui.text("",14f).apply { gravity=Gravity.CENTER }
 private val timerProgress=ProgressBar(ui.context,null,android.R.attr.progressBarStyleHorizontal).apply { max=1000; progressTintList=android.content.res.ColorStateList.valueOf(Palette.brand); progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Palette.soft); contentDescription="本轮进度" }
 private val detail=ui.text("到时提醒你离开屏幕，活动一下。",14f).apply { gravity=Gravity.CENTER }
 private val durations=ui.column()
 private val presetButtons=mutableMapOf<Int,Button>()
 private val primaryButton=ui.button("开始专注",true,"timer_primary",primary)
 private val secondary=ui.row()
 private val endButton=ui.button("结束本轮",false,"timer_end",end)
 private val adjustButton=ui.button("调整时长",false,"timer_adjust",adjust)
 private val breakIcon=ui.icon(R.drawable.ic_break,"起身休息") {}.apply { isClickable=false; isFocusable=false }
 private val musicCard=ui.card()
 private lateinit var musicHeading:LinearLayout
 val musicTitle=ui.text("慢步 · 内置音乐",15f,Palette.ink)
 val musicPlay=ui.icon(R.drawable.ic_play,"播放音乐","music_play",toggle)
 val permissionBanner=ui.button("后台提醒尚未就绪 · 查看",false,"permission_banner",settings)
 private val reboot=ui.card().apply { background=ui.shape(Palette.warm) }
 init {
  content.addView(ui.row().apply {
   addView(ui.column().apply { addView(ui.text("stand up.",30f,Palette.ink).apply { typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }); addView(ui.text("留一点时间，起来走走。",13f)) },LinearLayout.LayoutParams(0,-2,1f))
   addView(ui.icon(R.drawable.ic_settings,"提醒设置","settings",settings))
  },LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=ui.dp(20) })
  timer.addView(breakIcon,LinearLayout.LayoutParams(ui.dp(64),ui.dp(64)).apply { gravity=Gravity.CENTER; bottomMargin=ui.dp(16) })
  timer.addView(status); timer.addView(countdown); countdown.setAutoSizeTextTypeUniformWithConfiguration((48/ui.context.resources.configuration.fontScale).toInt().coerceAtMost(48),60,2,android.util.TypedValue.COMPLEX_UNIT_SP); timer.addView(roundTotal); timer.addView(timerProgress,LinearLayout.LayoutParams(-1,ui.dp(4)).apply { topMargin=ui.dp(12); bottomMargin=ui.dp(12) }); timer.addView(detail)
  val groups=if(ui.context.resources.configuration.fontScale>1.2f || ui.context.resources.configuration.screenWidthDp<360) listOf(listOf(15,30),listOf(45,60)) else listOf(listOf(15,30,45,60))
  groups.forEach { group-> durations.addView(ui.row().apply { group.forEach { n -> ui.equal(this,ui.button("$n 分",false,"preset_$n") { select(n) }.also { presetButtons[n]=it }) } }) }
  durations.addView(ui.button("自定义时长",false,"duration_custom",custom),LinearLayout.LayoutParams(-1,-2))
  timer.addView(durations,LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(12); bottomMargin=ui.dp(12) })
  timer.addView(primaryButton,LinearLayout.LayoutParams(-1,-2).apply { topMargin=ui.dp(14) })
  if(ui.context.resources.configuration.fontScale>1.2f) { secondary.orientation=LinearLayout.VERTICAL; secondary.addView(endButton,LinearLayout.LayoutParams(-1,-2)); secondary.addView(adjustButton,LinearLayout.LayoutParams(-1,-2)) } else { ui.equal(secondary,endButton); ui.equal(secondary,adjustButton) }; timer.addView(secondary)
  content.addView(timer)
  musicHeading=ui.row().apply { addView(ui.text("背景音乐",15f,Palette.ink),LinearLayout.LayoutParams(0,-2,1f)); addView(ui.button("音乐库",false,"music_library",music)) }; content.addView(musicHeading)
  musicCard.addView(ui.row().apply {
   addView(ui.icon(R.drawable.ic_music,"打开音乐库","music_art",music))
   addView(ui.column().apply { setPadding(ui.dp(12),0,ui.dp(6),0); addView(musicTitle); addView(ui.text("为专注留一点背景声",12f)) },LinearLayout.LayoutParams(0,-2,1f))
   addView(musicPlay)
  }); content.addView(musicCard)
  content.addView(permissionBanner,LinearLayout.LayoutParams(-1,-2))
  reboot.addView(ui.text("手机已重启，之前的计时已取消。",13f,Palette.ink)); reboot.addView(ui.button("知道了",false,"reboot_dismiss",dismissBoot)); content.addView(reboot)
 }
 fun render(state:TimerState,selected:Int,now:Long,ready:Boolean,rebooted:Boolean) {
  val model=TimerUiState.from(state,selected,now); val due=state.phase==Phase.DUE
  val key=listOf(state.phase,state.duration,selected,formatRemaining(model.remaining),ready,rebooted)
  if(lastRender==key) return
  lastRender=key
  if(status.text.toString()!=model.title) {
   status.text=model.title; status.textSize=if(due) 22f else 14f
   status.typeface=if(due) Typeface.create("sans-serif-medium",Typeface.NORMAL) else Typeface.DEFAULT
  }; countdown.text=formatRemaining(model.remaining); countdown.contentDescription="剩余时间 ${countdown.text}"
  primaryButton.text=model.primary; countdown.visibility=if(due) View.GONE else View.VISIBLE; breakIcon.visibility=if(due) View.VISIBLE else View.GONE
  detail.text=when(state.phase) { Phase.DUE->"离开屏幕一会儿，站起来走几步。\n让眼睛和身体都放松一下。\n\n背景音乐已暂停"; Phase.PAUSED->"继续会保留剩余时间 · 所选时长用于下一轮"; else->"到时提醒你离开屏幕，活动一下。" }
  durations.visibility=if(model.showDurations) View.VISIBLE else View.GONE
  secondary.visibility=if(state.phase==Phase.RUNNING || state.phase==Phase.PAUSED) View.VISIBLE else View.GONE
  endButton.text=if(state.phase==Phase.PAUSED) "重新开始" else "结束本轮"
  adjustButton.text=if(state.phase==Phase.PAUSED) "下次时长" else "调整时长"
  if(lastSelection!=selected) {
   presetButtons.forEach { (n,b) -> b.background=ui.shape(if(n==selected) Palette.soft else android.graphics.Color.TRANSPARENT,12); b.isSelected=n==selected }
   lastSelection=selected
  }
  permissionBanner.visibility=if(ready || due) View.GONE else View.VISIBLE
  reboot.visibility=if(rebooted) View.VISIBLE else View.GONE
  musicCard.visibility=if(due) View.GONE else View.VISIBLE; musicHeading.visibility=musicCard.visibility
  roundTotal.text=if(state.phase==Phase.IDLE) "准备专注 $selected 分钟" else "本轮 ${state.duration/60000} 分钟"; roundTotal.visibility=if(due) View.GONE else View.VISIBLE
  timerProgress.visibility=if(state.phase==Phase.RUNNING || state.phase==Phase.PAUSED) View.VISIBLE else View.GONE
  timerProgress.progress=((1.0-model.remaining.toDouble()/state.duration)*1000).toInt().coerceIn(0,1000)
 }
}
