package com.zhaopw.standup

data class TimerUiState(val remaining:Long,val title:String,val primary:String,val showDurations:Boolean) {
 companion object {
  fun from(state:TimerState,selected:Int,now:Long)=TimerUiState(
   if(state.phase==Phase.IDLE) selected*60000L else state.remaining(now),
   when(state.phase) { Phase.IDLE->"给身体一点喘息时间"; Phase.RUNNING->"专注中"; Phase.PAUSED->"计时已暂停"; Phase.DUE->"该起身活动一下了" },
   when(state.phase) { Phase.IDLE->"开始专注"; Phase.RUNNING->"暂停计时"; Phase.PAUSED->"继续专注"; Phase.DUE->"知道了" },
   state.phase==Phase.IDLE || state.phase==Phase.PAUSED)
 }
}
// A modal is only an intent. A later deadline or generation change invalidates it.
fun canConfirmTimerChange(snapshot:TimerState,current:TimerState,now:Long)=
 snapshot.generation==current.generation && snapshot.phase==current.phase && current.phase!=Phase.DUE &&
 !(current.phase==Phase.RUNNING && current.remaining(now)==0L)
