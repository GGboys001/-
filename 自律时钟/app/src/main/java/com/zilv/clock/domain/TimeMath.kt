package com.zilv.clock.domain

import com.zilv.clock.data.TimeSlice
import com.zilv.clock.data.TimerMode
import com.zilv.clock.data.TimerSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object TimeMath {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun elapsedMs(session: TimerSessionEntity, now: Long = System.currentTimeMillis()): Long {
        val end = session.endedAt ?: if (session.pausedAt != null) session.pausedAt else now
        return (end - session.startedAt - session.pausedDurationMs).coerceAtLeast(0)
    }

    fun completedMs(session: TimerSessionEntity, now: Long = System.currentTimeMillis()): Long = when (session.mode) {
        TimerMode.COUNT_UP -> elapsedMs(session, now)
        TimerMode.COUNT_DOWN -> minOf(elapsedMs(session, now), session.plannedDurationMs ?: 0L)
    }

    fun isCountdownComplete(session: TimerSessionEntity, now: Long = System.currentTimeMillis()) =
        session.mode == TimerMode.COUNT_DOWN && elapsedMs(session, now) >= (session.plannedDurationMs ?: Long.MAX_VALUE)

    fun dateAt(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    fun dayStart(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dayEnd(date: LocalDate): Long = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun dateKey(date: LocalDate): String = date.toString()
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    fun weekKey(date: LocalDate): String = weekStart(date).toString()
    fun monthKey(date: LocalDate): String = "${date.year}-${date.monthValue.toString().padStart(2, '0')}"

    fun slices(session: TimerSessionEntity, now: Long = System.currentTimeMillis()): List<TimeSlice> {
        val end = (session.endedAt ?: now).coerceAtLeast(session.startedAt)
        val activeEnd = minOf(end, session.startedAt + session.pausedDurationMs + completedMs(session, now))
        if (activeEnd <= session.startedAt) return emptyList()
        val result = mutableListOf<TimeSlice>()
        var cursor = session.startedAt
        while (cursor < activeEnd) {
            val boundary = dayEnd(dateAt(cursor))
            val sliceEnd = minOf(boundary, activeEnd)
            result += TimeSlice(session.taskId, session.taskNameSnapshot, session.taskColorSnapshot, cursor, sliceEnd)
            cursor = sliceEnd
        }
        return result
    }

    fun rangeDays(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { if (it < to) it.plusDays(1) else null }.toList()

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }
}
