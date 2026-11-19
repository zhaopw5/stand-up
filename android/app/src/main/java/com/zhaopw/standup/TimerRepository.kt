package com.zhaopw.standup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

class TimerRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("timer", Context.MODE_PRIVATE)
    private val scheduler = AlarmScheduler(context)
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun read(): TimerState = synchronized(lock) {
        val state = TimerState(
            runCatching { Phase.valueOf(prefs.getString("phase", "IDLE")!!) }.getOrDefault(Phase.IDLE),
            prefs.getLong("duration", 1800000), prefs.getLong("remaining", 1800000),
            prefs.getLong("deadline", 0), prefs.getLong("generation", 0),
        )
        val restored = state.afterBoot(prefs.getInt("boot", boot()), boot())
        if (restored != state) {
            save(restored)
            prefs.edit().putBoolean("rebooted", true).commit()
            scheduler.cancel()
            Notifications(context).cancelRunning()
        }
        restored
    }
    private fun save(state: TimerState) {
        prefs.edit().putString("phase", state.phase.name).putLong("duration", state.duration)
            .putLong("remaining", state.savedRemaining).putLong("deadline", state.deadline)
            .putLong("generation", state.generation).putInt("boot", boot()).commit()
    }
    fun rebootNotice(): Boolean = prefs.getBoolean("rebooted", false)
    fun clearRebootNotice() { prefs.edit().putBoolean("rebooted", false).apply() }
    fun start(minutes: Int): TimerState = synchronized(lock) {
        val next = read().start(minutes, SystemClock.elapsedRealtime())
        save(next)
        clearRebootNotice()
        scheduler.schedule(next)
        Notifications(context).running(next)
        next
    }
    fun pause(): TimerState = synchronized(lock) {
        val current = read()
        if (deliver(current.generation)) return@synchronized read()
        val next = current.pause(SystemClock.elapsedRealtime())
        save(next)
        scheduler.cancel()
        Notifications(context).running(next)
        next
    }
    // Validate and transition under one lock so an old card cannot affect a newer run.
    fun notificationAction(action: String, generation: Long): Boolean = synchronized(lock) {
        val current = read()
        val expected = when (action) {
            Notifications.PAUSE -> Phase.RUNNING
            Notifications.RESUME -> Phase.PAUSED
            else -> return@synchronized false
        }
        if (current.generation != generation || current.phase != expected) return@synchronized false
        if (deliver(current.generation)) return@synchronized true
        if (expected == Phase.RUNNING) pause() else start((current.duration / 60000L).toInt().coerceIn(1,999))
        true
    }
    fun reset(minutes: Int): TimerState = synchronized(lock) {
        val next = read().reset(minutes)
        save(next)
        clearRebootNotice()
        scheduler.cancel()
        Notifications(context).cancelAll()
        next
    }
    fun acknowledge(generation: Long, minutes: Int): Boolean = synchronized(lock) {
        val current = read()
        if (current.phase != Phase.DUE || (generation >= 0 && current.generation != generation)) return@synchronized false
        reset(minutes)
        true
    }
    fun refreshSchedule() = synchronized(lock) {
        val state = read()
        if (!deliver(state.generation) && state.phase == Phase.RUNNING) {
            scheduler.schedule(state)
            Notifications(context).running(state)
        }
    }
    fun deliver(generation: Long): Boolean = synchronized(lock) {
        val next = read().finishIfDue(SystemClock.elapsedRealtime(), generation) ?: return@synchronized false
        save(next) // Save first: duplicate broadcasts cannot play a second sound.
        scheduler.cancel()
        MusicService.pauseForReminder(context)
        Notifications(context).due(next.generation)
        true
    }
    companion object { private val lock = Any() }
}

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun intent(generation: Long = -1) = PendingIntent.getBroadcast(context, 1,
        Intent(context, ReminderReceiver::class.java).setAction("com.zhaopw.standup.ALARM")
            .putExtra("generation", generation), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel() = manager.cancel(intent())
    fun schedule(state: TimerState) {
        cancel()
        if (state.phase != Phase.RUNNING) return
        try {
            if (exactAllowed()) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, state.deadline, intent(state.generation))
            else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, state.deadline, intent(state.generation))
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, state.deadline, intent(state.generation))
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Notifications.ACK) {
            val repo = TimerRepository(context); val selected = context.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("selectedMinutes", 30).coerceIn(1,999)
            repo.acknowledge(intent.getLongExtra("generation", -1), selected)
        }
        else if (intent.action == Notifications.PAUSE || intent.action == Notifications.RESUME)
            TimerRepository(context).notificationAction(intent.action!!, intent.getLongExtra("generation", -1))
        else if (intent.action == "com.zhaopw.standup.ALARM") TimerRepository(context).deliver(intent.getLongExtra("generation", -1))
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) TimerRepository(context).read()
    }
}
