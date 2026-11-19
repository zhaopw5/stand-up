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
import android.view.View
import android.widget.RemoteViews

class Notifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun allowed() = manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun remindersAllowed(): Boolean = allowed() &&
        (manager.getNotificationChannel(DUE_CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_NONE) != NotificationManager.IMPORTANCE_NONE
    fun timerCardsAllowed(): Boolean = allowed() &&
        (manager.getNotificationChannel(TIMER_CHANNEL)?.importance ?: NotificationManager.IMPORTANCE_NONE) != NotificationManager.IMPORTANCE_NONE
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
    private fun action(name: String, generation: Long) = PendingIntent.getBroadcast(context, 2,
        Intent(context, ReminderReceiver::class.java).setAction(name)
            .setData(Uri.parse("standup://timer/${name.substringAfterLast('.')}/$generation"))
            .putExtra("generation", generation), PendingIntent.FLAG_IMMUTABLE)
    private fun card(state: TimerState, expanded: Boolean): RemoteViews {
        val running = state.phase == Phase.RUNNING
        val due = state.phase == Phase.DUE
        return RemoteViews(context.packageName, if (expanded) R.layout.notification_expanded else R.layout.notification_compact).apply {
            // Keep the compact label short: a 999-minute clock must fit at large font sizes.
            setTextViewText(R.id.card_status, if (expanded) when { due -> "stand up · 起身时间"; running -> "stand up · 距离起身"; else -> "stand up · 已暂停" }
                else if (running) "起身" else "暂停")
            setViewVisibility(R.id.card_status, if (!expanded && due) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.card_clock, if (running) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.card_time, if (running) View.GONE else View.VISIBLE)
            if (running) {
                // SystemUI owns the ticks; the app does not repost every second.
                setChronometerCountDown(R.id.card_clock, true)
                setChronometer(R.id.card_clock, state.deadline, null, true)
            } else setTextViewText(R.id.card_time, if (due) "该起身了" else formatRemaining(state.savedRemaining))
            if (expanded) setTextViewText(R.id.card_hint, when {
                due -> "站起来，走几步，让身体休息一下。"
                running -> "专注一会儿，也记得照顾身体。"
                else -> "当前轮剩余 · 继续后接着计时"
            })
        }
    }
    private fun custom(builder: Notification.Builder, state: TimerState) = builder
        .setStyle(Notification.DecoratedCustomViewStyle())
        .setCustomContentView(card(state, false)).setCustomBigContentView(card(state, true))
    fun running(state: TimerState) {
        createChannels()
        manager.cancel(DUE_ID)
        if (state.phase != Phase.RUNNING && state.phase != Phase.PAUSED) { cancelRunning(); return }
        val running = state.phase == Phase.RUNNING
        val notification = custom(builder(TIMER_CHANNEL), state).setContentTitle(if (running) "stand up · 计时中" else "stand up · 已暂停")
            .setContentText(if (running) "距离起身 · 展开可暂停" else "当前轮剩余 ${formatRemaining(state.savedRemaining)} · 展开可继续")
            .setOnlyAlertOnce(true).setOngoing(true).setShowWhen(false)
            .addAction(Notification.Action.Builder(null, if (running) "暂停" else "继续", action(if (running) PAUSE else RESUME, state.generation)).build())
        if (timerCardsAllowed()) manager.notify(TIMER_ID, notification.build())
    }
    fun due(generation: Long = TimerRepository(context).read().generation) {
        createChannels()
        cancelRunning()
        val ack = action(ACK, generation)
        if (remindersAllowed()) manager.notify(DUE_ID, custom(builder(DUE_CHANNEL), TimerState(phase = Phase.DUE, generation = generation)).setContentTitle("该起身活动一下了")
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
        const val PAUSE = "com.zhaopw.standup.PAUSE"
        const val RESUME = "com.zhaopw.standup.RESUME"
        private const val TIMER_ID = 10
        private const val DUE_ID = 11
    }
}
