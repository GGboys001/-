package com.zilv.clock.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.clockSettings by preferencesDataStore(name = "clock_settings")

class SettingsRepository(private val context: Context) {
    private val darkMode = booleanPreferencesKey("dark_mode")
    val isDarkMode: Flow<Boolean> = context.clockSettings.data.map { it[darkMode] ?: false }
    suspend fun setDarkMode(value: Boolean) { context.clockSettings.edit { it[darkMode] = value } }
}
