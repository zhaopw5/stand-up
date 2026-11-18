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
    fun reset(minutes: Int): TimerState = synchronized(lock) {
        val next = read().reset(minutes)
        save(next)
        clearRebootNotice()
        scheduler.cancel()
        Notifications(context).cancelAll()
        next
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
        Notifications(context).due()
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
        if (intent.action == Notifications.ACK) Notifications(context).acknowledge()
        else TimerRepository(context).deliver(intent.getLongExtra("generation", -1))
    }
}
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) TimerRepository(context).read()
    }
}
