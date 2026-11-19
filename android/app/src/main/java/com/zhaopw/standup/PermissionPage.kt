package com.zhaopw.standup

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView

internal class PermissionPage(private val activity:Activity):Dialog(activity,android.R.style.Theme_Material_Light_NoActionBar) {
 private val ui=Ui(activity)
 private val notification=ui.text("")
 private val exact=ui.text("")
 private val timerCard=ui.text("")
 init {
  val body=ui.column().apply { setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24)); setBackgroundColor(Palette.bg) }
  body.addView(ui.text("后台提醒设置",26f,Palette.ink)); body.addView(ui.text("各项设置各自独立，你可以随时返回计时。"))
  body.addView(ui.card().apply {
   addView(ui.text("通知",20f,Palette.ink)); addView(notification)
   addView(ui.button("打开通知设置",true,"notification_settings") {
    val intent=if(!Notifications(activity).allowed()) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)
    else Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,Notifications.DUE_CHANNEL)
    runCatching { activity.startActivity(intent) }
   })
  })
  body.addView(ui.card().apply {
   addView(ui.text("锁屏倒计时卡片",20f,Palette.ink)); addView(timerCard)
   addView(ui.text("计时或暂停时显示；展开通知可暂停或继续。请允许“计时状态”通知及锁屏显示。锁屏展示、折叠和隐私由系统决定。"))
   addView(ui.button("打开卡片通知设置",true,"timer_card_settings") {
    val intent=if(!Notifications(activity).allowed()) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)
    else Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,Notifications.TIMER_CHANNEL)
    runCatching { activity.startActivity(intent) }
   })
  })
  body.addView(ui.card().apply {
   addView(ui.text("准确提醒",20f,Palette.ink)); addView(exact)
   if(Build.VERSION.SDK_INT>=31) addView(ui.button("打开准确提醒设置",true,"exact_settings") { runCatching { activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${activity.packageName}"))) } })
  })
  body.addView(ui.text("提醒声音遵循系统静音、勿扰和锁屏设置。部分手机还需在电池设置中允许本应用后台运行。"))
  body.addView(ui.button("返回计时",false,"permission_close") { dismiss() },LinearLayout.LayoutParams(-1,-2))
  val scroll=ScrollView(activity).apply { isFillViewport=true; setBackgroundColor(Palette.bg); addView(body) }; ui.insets(scroll); setContentView(scroll)
 }
 override fun show() {
  super.show(); window?.apply {
   setLayout(-1,-1); setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Palette.bg))
   androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this,false)
   androidx.core.view.WindowCompat.getInsetsController(this,decorView).apply { isAppearanceLightStatusBars=true; isAppearanceLightNavigationBars=true }
  }; refresh()
 }
 fun refresh() {
  val n=Notifications(activity); val channel=activity.getSystemService(NotificationManager::class.java).getNotificationChannel(Notifications.DUE_CHANNEL)
  notification.text=when { !n.allowed()->"应用通知未允许。后台到时无法显示或播放提醒。"; !n.remindersAllowed()->"起身提醒类别已关闭。请打开该通知类别。"; channel?.sound==null->"通知已允许 · 提醒类别为静音，到时不会播放提示音。"; else->"通知与起身提醒类别已允许。声音仍遵循系统设置。" }
  val timerChannel=activity.getSystemService(NotificationManager::class.java).getNotificationChannel(Notifications.TIMER_CHANNEL)
  timerCard.text=when { !n.allowed()->"应用通知未允许，卡片无法显示。"; !n.timerCardsAllowed()->"计时状态类别已关闭，请打开该类别。"; timerChannel?.lockscreenVisibility==android.app.Notification.VISIBILITY_SECRET->"计时状态已允许，但此类别的锁屏内容被隐藏。"; else->"计时状态类别已允许；锁屏显示仍需系统允许。" }
  exact.text=if(AlarmScheduler(activity).exactAllowed()) "准确提醒已允许。" else "准确提醒尚未允许，后台提醒可能延迟。"
 }
}
