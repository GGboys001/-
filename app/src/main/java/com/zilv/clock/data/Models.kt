package com.zilv.clock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TimerMode { COUNT_UP, COUNT_DOWN }
enum class SessionStatus { RUNNING, PAUSED, FINISHED }
enum class GoalPeriod { DAILY, WEEKLY }
enum class CheckInKind { NORMAL, MAKEUP }

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val icon: String = "book",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

@Entity(tableName = "sessions")
data class TimerSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val taskNameSnapshot: String,
    val taskColorSnapshot: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val pausedAt: Long? = null,
    val pausedDurationMs: Long = 0,
    val mode: TimerMode,
    val plannedDurationMs: Long? = null,
    val status: SessionStatus = SessionStatus.RUNNING,
    val note: String = "",
    val autoFinished: Boolean = false,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val period: GoalPeriod,
    val targetMinutes: Int,
    val taskId: Long? = null,
    val weekdays: String = "1,2,3,4,5,6,7",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "check_ins", primaryKeys = ["periodKey", "period"])
data class CheckInEntity(
    val periodKey: String,
    val period: GoalPeriod,
    val kind: CheckInKind,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 0,
    val cards: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val rewardedBlocks: Int = 0,
)

@Entity(tableName = "wallet_events")
data class WalletEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val delta: Int,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TimeSlice(val taskId: Long, val taskName: String, val color: Long, val start: Long, val end: Long)
data class TaskDuration(val taskId: Long, val name: String, val color: Long, val durationMs: Long)
