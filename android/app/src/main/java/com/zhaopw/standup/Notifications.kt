package com.zhaopw.standup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.SystemClock

class Notifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun allowed() = manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun remindersAllowed(): Boolean = allowed() &&
        (manager.getNotificationChannel(DUE_CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_NONE) != NotificationManager.IMPORTANCE_NONE
    fun createChannels() {
        manager.createNotificationChannel(NotificationChannel(TIMER_CHANNEL, "计时状态", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        manager.createNotificationChannel(NotificationChannel(DUE_CHANNEL, "起身提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "计时结束时播放一次提示音；声音仍遵循系统静音与勿扰设置"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(Uri.parse("android.resource://${context.packageName}/raw/gentle_chime"),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        })
    }
    private fun open() = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun builder(channel: String) = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification).setContentIntent(open()).setVisibility(Notification.VISIBILITY_PUBLIC)
    fun running(state: TimerState) {
        createChannels()
        manager.cancel(DUE_ID)
        if (state.phase != Phase.RUNNING && state.phase != Phase.PAUSED) { cancelRunning(); return }
        val notification = builder(TIMER_CHANNEL).setContentTitle(if (state.phase == Phase.RUNNING) "stand up · 计时中" else "stand up · 已暂停")
            .setContentText("距离起身 ${formatRemaining(state.remaining(SystemClock.elapsedRealtime()))}")
            .setOnlyAlertOnce(true).setOngoing(state.phase == Phase.RUNNING)
        if (state.phase == Phase.RUNNING) notification.setWhen(System.currentTimeMillis() + state.remaining(SystemClock.elapsedRealtime()))
            .setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
        if (allowed()) manager.notify(TIMER_ID, notification.build())
    }
    fun due() {
        createChannels()
        cancelRunning()
        val ack = PendingIntent.getBroadcast(context, 2, Intent(context, ReminderReceiver::class.java).setAction(ACK), PendingIntent.FLAG_IMMUTABLE)
        if (allowed()) manager.notify(DUE_ID, builder(DUE_CHANNEL).setContentTitle("该起身活动一下了")
            .setContentText("站起来，走几步，让身体休息一下。音乐已暂停。")
            .setAutoCancel(true).addAction(Notification.Action.Builder(null, "知道了", ack).build()).build())
    }
    fun acknowledge() = manager.cancel(DUE_ID)
    fun cancelRunning() = manager.cancel(TIMER_ID)
    fun cancelAll() { cancelRunning(); acknowledge() }
    companion object {
        const val TIMER_CHANNEL = "timer_status"
        const val DUE_CHANNEL = "standup_reminder_v1"
        const val ACK = "com.zhaopw.standup.ACK"
        private const val TIMER_ID = 10
        private const val DUE_ID = 11
    }
}
