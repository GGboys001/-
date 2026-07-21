package com.zilv.clock

import android.app.Application
import androidx.room.Room
import com.zilv.clock.data.ClockDatabase
import com.zilv.clock.domain.ClockRepository
import com.zilv.clock.data.SettingsRepository

class ClockApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, ClockDatabase::class.java, "self-discipline-clock.db").fallbackToDestructiveMigration().build() }
    val repository by lazy { ClockRepository(database, database.clockDao()) }
    val settings by lazy { SettingsRepository(this) }
}
