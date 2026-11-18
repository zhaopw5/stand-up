package com.zhaopw.standup

enum class Phase { IDLE, RUNNING, PAUSED, DUE }
data class TimerState(
    val phase: Phase = Phase.IDLE,
    val duration: Long = 30 * 60000L,
    val savedRemaining: Long = duration,
    val deadline: Long = 0,
    val generation: Long = 0,
) {
    fun start(minutes: Int, now: Long): TimerState {
        if (phase == Phase.RUNNING) return this
        require(minutes in 1..999)
        val length = if (phase == Phase.PAUSED) savedRemaining else minutes * 60000L
        return copy(phase = Phase.RUNNING, duration = if (phase == Phase.PAUSED) duration else length,
            savedRemaining = length, deadline = now + length, generation = generation + 1)
    }
    fun pause(now: Long): TimerState = if (phase == Phase.RUNNING)
        copy(phase = Phase.PAUSED, savedRemaining = remaining(now), deadline = 0, generation = generation + 1) else this
    fun reset(minutes: Int): TimerState {
        require(minutes in 1..999)
        return TimerState(duration = minutes * 60000L, generation = generation + 1)
    }
    fun remaining(now: Long): Long = when (phase) {
        Phase.RUNNING -> (deadline - now).coerceAtLeast(0)
        Phase.DUE -> 0
        else -> savedRemaining
    }
    fun finishIfDue(now: Long, alarmGeneration: Long): TimerState? =
        if (phase == Phase.RUNNING && alarmGeneration == generation && now >= deadline)
            copy(phase = Phase.DUE, savedRemaining = 0, deadline = 0, generation = generation + 1) else null
    fun afterBoot(savedBoot: Int, currentBoot: Int): TimerState =
        if (savedBoot != currentBoot) reset((duration / 60000L).toInt().coerceIn(1,999)) else this
}
fun parseMinutes(text: String): Int? = text.trim().takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
    ?.toIntOrNull()?.takeIf { it in 1..999 }
fun formatRemaining(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 999) / 1000
    return "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
