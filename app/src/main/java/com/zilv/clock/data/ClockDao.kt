package com.zilv.clock.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClockDao {
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY createdAt DESC") fun observeTasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC") suspend fun allTasks(): List<TaskEntity>
    @Insert suspend fun insertTask(task: TaskEntity): Long
    @Update suspend fun updateTask(task: TaskEntity)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") fun observeSessions(): Flow<List<TimerSessionEntity>>
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") suspend fun allSessions(): List<TimerSessionEntity>
    @Query("SELECT * FROM sessions WHERE status != 'FINISHED' LIMIT 1") fun observeActiveSession(): Flow<TimerSessionEntity?>
    @Query("SELECT * FROM sessions WHERE status != 'FINISHED' LIMIT 1") suspend fun activeSession(): TimerSessionEntity?
    @Insert suspend fun insertSession(session: TimerSessionEntity): Long
    @Update suspend fun updateSession(session: TimerSessionEntity)
    @Delete suspend fun deleteSession(session: TimerSessionEntity)

    @Query("SELECT * FROM goals ORDER BY period, createdAt DESC") fun observeGoals(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals ORDER BY period, createdAt DESC") suspend fun allGoals(): List<GoalEntity>
    @Insert suspend fun insertGoal(goal: GoalEntity): Long
    @Update suspend fun updateGoal(goal: GoalEntity)
    @Delete suspend fun deleteGoal(goal: GoalEntity)

    @Query("SELECT * FROM check_ins") fun observeCheckIns(): Flow<List<CheckInEntity>>
    @Query("SELECT * FROM check_ins") suspend fun allCheckIns(): List<CheckInEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCheckIn(checkIn: CheckInEntity)
    @Query("DELETE FROM check_ins WHERE kind = 'NORMAL'") suspend fun deleteNormalCheckIns()

    @Query("SELECT * FROM wallet WHERE id = 0") fun observeWallet(): Flow<WalletEntity?>
    @Query("SELECT * FROM wallet WHERE id = 0") suspend fun wallet(): WalletEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveWallet(wallet: WalletEntity)
    @Insert suspend fun insertWalletEvent(event: WalletEventEntity)
    @Query("SELECT * FROM wallet_events ORDER BY createdAt") suspend fun allWalletEvents(): List<WalletEventEntity>

    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM check_ins") suspend fun clearCheckIns()
    @Query("DELETE FROM wallet") suspend fun clearWallet()
    @Query("DELETE FROM wallet_events") suspend fun clearWalletEvents()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTasks(items: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSessions(items: List<TimerSessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreGoals(items: List<GoalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreCheckIns(items: List<CheckInEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreWalletEvents(items: List<WalletEventEntity>)
}
