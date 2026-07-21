package com.zilv.clock.domain

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.zilv.clock.data.CheckInEntity
import com.zilv.clock.data.CheckInKind
import com.zilv.clock.data.ClockDao
import com.zilv.clock.data.GoalEntity
import com.zilv.clock.data.GoalPeriod
import com.zilv.clock.data.SessionStatus
import com.zilv.clock.data.TaskDuration
import com.zilv.clock.data.TaskEntity
import com.zilv.clock.data.TimerMode
import com.zilv.clock.data.TimerSessionEntity
import com.zilv.clock.data.WalletEntity
import com.zilv.clock.data.WalletEventEntity
import com.zilv.clock.data.BackupCodec
import com.zilv.clock.data.ClockBackup
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate

class ClockRepository(private val database: RoomDatabase, private val dao: ClockDao) {
    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()
    val sessions: Flow<List<TimerSessionEntity>> = dao.observeSessions()
    val goals: Flow<List<GoalEntity>> = dao.observeGoals()
    val checkIns: Flow<List<CheckInEntity>> = dao.observeCheckIns()
    val wallet: Flow<WalletEntity?> = dao.observeWallet()
    val activeSession: Flow<TimerSessionEntity?> = dao.observeActiveSession()

    suspend fun saveTask(task: TaskEntity) {
        if (task.id == 0L) dao.insertTask(task) else dao.updateTask(task)
    }

    suspend fun archiveTask(task: TaskEntity) = dao.updateTask(task.copy(enabled = false, deleted = true))

    suspend fun saveGoal(goal: GoalEntity) {
        if (goal.id == 0L) dao.insertGoal(goal) else dao.updateGoal(goal)
        refreshCheckIns()
    }

    suspend fun deleteGoal(goal: GoalEntity) { dao.deleteGoal(goal); refreshCheckIns() }

    suspend fun startSession(task: TaskEntity, mode: TimerMode, plannedMs: Long?, note: String): Boolean {
        if (dao.activeSession() != null) return false
        dao.insertSession(
            TimerSessionEntity(
                taskId = task.id,
                taskNameSnapshot = task.name,
                taskColorSnapshot = task.color,
                startedAt = System.currentTimeMillis(),
                mode = mode,
                plannedDurationMs = plannedMs,
                note = note,
            ),
        )
        return true
    }

    suspend fun pauseSession() {
        val session = dao.activeSession() ?: return
        if (session.status == SessionStatus.RUNNING) dao.updateSession(session.copy(status = SessionStatus.PAUSED, pausedAt = System.currentTimeMillis()))
    }

    suspend fun resumeSession() {
        val session = dao.activeSession() ?: return
        val pausedAt = session.pausedAt ?: return
        dao.updateSession(session.copy(status = SessionStatus.RUNNING, pausedAt = null, pausedDurationMs = session.pausedDurationMs + System.currentTimeMillis() - pausedAt))
    }

    suspend fun finishSession(autoFinished: Boolean = false) {
        val session = dao.activeSession() ?: return
        val now = System.currentTimeMillis()
        val pausedExtra = session.pausedAt?.let { now - it } ?: 0L
        dao.updateSession(session.copy(endedAt = now, pausedAt = null, pausedDurationMs = session.pausedDurationMs + pausedExtra, status = SessionStatus.FINISHED, autoFinished = autoFinished))
        refreshCheckIns()
    }

    suspend fun saveSession(session: TimerSessionEntity) { dao.updateSession(session.copy(status = SessionStatus.FINISHED, pausedAt = null)); refreshCheckIns() }
    suspend fun deleteSession(session: TimerSessionEntity) { dao.deleteSession(session); refreshCheckIns() }

    suspend fun exportBackup(): String = BackupCodec.encode(ClockBackup(dao.allTasks(), dao.allSessions(), dao.allGoals(), dao.allCheckIns(), dao.wallet(), dao.allWalletEvents()))

    suspend fun importBackup(raw: String) = database.withTransaction {
        val backup = BackupCodec.decode(raw)
        dao.clearWalletEvents(); dao.clearWallet(); dao.clearCheckIns(); dao.clearSessions(); dao.clearGoals(); dao.clearTasks()
        dao.restoreTasks(backup.tasks); dao.restoreSessions(backup.sessions); dao.restoreGoals(backup.goals); dao.restoreCheckIns(backup.checkIns)
        backup.wallet?.let { dao.saveWallet(it) }; dao.restoreWalletEvents(backup.walletEvents)
        refreshCheckIns()
    }

    suspend fun refreshCheckIns(today: LocalDate = LocalDate.now()) = database.withTransaction {
        val allGoals = dao.allGoals()
        val allSessions = dao.allSessions()
        dao.deleteNormalCheckIns()
        val existing = dao.allCheckIns().associateBy { it.period to it.periodKey }
        val firstDay = allSessions.minOfOrNull { TimeMath.dateAt(it.startedAt) } ?: today
        TimeMath.rangeDays(firstDay, today).forEach { date ->
            val dailyGoals = activeDailyGoals(allGoals, date)
            val key = TimeMath.dateKey(date)
            if (dailyGoals.isNotEmpty() && dailyGoals.any { completed(it, allSessions, TimeMath.dayStart(date), TimeMath.dayEnd(date)) }) {
                val old = existing[GoalPeriod.DAILY to key]
                if (old == null) dao.upsertCheckIn(CheckInEntity(key, GoalPeriod.DAILY, CheckInKind.NORMAL))
            }
        }
        val weeks = TimeMath.rangeDays(firstDay, today).map(TimeMath::weekStart).distinct()
        weeks.forEach { monday ->
            val weeklyGoals = allGoals.filter { it.enabled && it.period == GoalPeriod.WEEKLY }
            val key = TimeMath.weekKey(monday)
            if (weeklyGoals.any { completed(it, allSessions, TimeMath.dayStart(monday), TimeMath.dayEnd(monday.plusDays(6))) }) {
                if (existing[GoalPeriod.WEEKLY to key] == null) dao.upsertCheckIn(CheckInEntity(key, GoalPeriod.WEEKLY, CheckInKind.NORMAL))
            }
        }
        recalculateWallet(today)
    }

    suspend fun useMakeupCard(today: LocalDate = LocalDate.now()): Boolean = database.withTransaction {
        val wallet = dao.wallet() ?: WalletEntity()
        val allCheckins = dao.allCheckIns()
        val goals = dao.allGoals()
        val yesterday = today.minusDays(1)
        val yesterdayKey = TimeMath.dateKey(yesterday)
        val missedYesterday = allCheckins.none { it.period == GoalPeriod.DAILY && it.periodKey == yesterdayKey }
        val dayBefore = today.minusDays(2)
        val missedBefore = allCheckins.none { it.period == GoalPeriod.DAILY && it.periodKey == TimeMath.dateKey(dayBefore) }
        if (wallet.cards <= 0 || activeDailyGoals(goals, yesterday).isEmpty() || !missedYesterday || (activeDailyGoals(goals, dayBefore).isNotEmpty() && missedBefore)) return@withTransaction false
        dao.upsertCheckIn(CheckInEntity(yesterdayKey, GoalPeriod.DAILY, CheckInKind.MAKEUP))
        dao.saveWallet(wallet.copy(cards = wallet.cards - 1))
        dao.insertWalletEvent(WalletEventEntity(delta = -1, reason = "补打卡"))
        recalculateWallet(today)
        true
    }

    private suspend fun recalculateWallet(today: LocalDate) {
        val current = dao.wallet() ?: WalletEntity()
        val daily = dao.allCheckIns().filter { it.period == GoalPeriod.DAILY }.map { LocalDate.parse(it.periodKey) }.toSet()
        val goals = dao.allGoals()
        fun scheduled(date: LocalDate) = activeDailyGoals(goals, date).isNotEmpty()
        var streak = 0
        var cursor = today
        while (!scheduled(cursor) && cursor > today.minusDays(14)) cursor = cursor.minusDays(1)
        if (cursor !in daily) cursor = cursor.minusDays(1)
        while (cursor > today.minusDays(730)) {
            if (scheduled(cursor)) {
                if (cursor !in daily) break
                streak++
            }
            cursor = cursor.minusDays(1)
        }
        val best = maxOf(current.bestStreak, streak)
        val blocks = streak / 7
        val priorBlocks = if (streak < current.currentStreak) 0 else current.rewardedBlocks
        val newBlocks = maxOf(0, blocks - priorBlocks)
        val reward = minOf(newBlocks, 3 - current.cards)
        if (reward > 0) dao.insertWalletEvent(WalletEventEntity(delta = reward, reason = "连续打卡奖励"))
        dao.saveWallet(current.copy(cards = current.cards + reward, currentStreak = streak, bestStreak = best, rewardedBlocks = blocks))
    }

    fun durationsByTask(sessions: List<TimerSessionEntity>, start: Long, end: Long): List<TaskDuration> =
        sessions.flatMap { TimeMath.slices(it) }
            .mapNotNull { slice ->
                val overlapStart = maxOf(slice.start, start)
                val overlapEnd = minOf(slice.end, end)
                if (overlapEnd > overlapStart) slice.copy(start = overlapStart, end = overlapEnd) else null
            }
            .groupBy { Triple(it.taskId, it.taskName, it.color) }
            .map { (key, slices) -> TaskDuration(key.first, key.second, key.third, slices.sumOf { it.end - it.start }) }
            .sortedByDescending { it.durationMs }

    private fun activeDailyGoals(goals: List<GoalEntity>, date: LocalDate): List<GoalEntity> = goals.filter {
        it.enabled && it.period == GoalPeriod.DAILY && date.dayOfWeek.value in it.weekdays.split(',').mapNotNull(String::toIntOrNull)
    }

    private fun completed(goal: GoalEntity, sessions: List<TimerSessionEntity>, start: Long, end: Long): Boolean {
        val ms = durationsByTask(sessions, start, end).filter { goal.taskId == null || goal.taskId == it.taskId }.sumOf { it.durationMs }
        return ms >= goal.targetMinutes * 60_000L
    }
}
