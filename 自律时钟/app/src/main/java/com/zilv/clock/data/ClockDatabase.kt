package com.zilv.clock.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromMode(value: TimerMode) = value.name
    @TypeConverter fun toMode(value: String) = TimerMode.valueOf(value)
    @TypeConverter fun fromStatus(value: SessionStatus) = value.name
    @TypeConverter fun toStatus(value: String) = SessionStatus.valueOf(value)
    @TypeConverter fun fromGoalPeriod(value: GoalPeriod) = value.name
    @TypeConverter fun toGoalPeriod(value: String) = GoalPeriod.valueOf(value)
    @TypeConverter fun fromCheckInKind(value: CheckInKind) = value.name
    @TypeConverter fun toCheckInKind(value: String) = CheckInKind.valueOf(value)
}

@Database(
    entities = [TaskEntity::class, TimerSessionEntity::class, GoalEntity::class, CheckInEntity::class, WalletEntity::class, WalletEventEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ClockDatabase : RoomDatabase() { abstract fun clockDao(): ClockDao }
