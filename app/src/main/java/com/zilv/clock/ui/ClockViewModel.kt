package com.zilv.clock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zilv.clock.data.CheckInEntity
import com.zilv.clock.data.GoalEntity
import com.zilv.clock.data.TaskEntity
import com.zilv.clock.data.TimerMode
import com.zilv.clock.data.TimerSessionEntity
import com.zilv.clock.data.WalletEntity
import com.zilv.clock.domain.ClockRepository
import com.zilv.clock.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ClockUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val sessions: List<TimerSessionEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val checkIns: List<CheckInEntity> = emptyList(),
    val wallet: WalletEntity = WalletEntity(),
    val active: TimerSessionEntity? = null,
    val isDarkMode: Boolean = false,
)

class ClockViewModel(private val repository: ClockRepository, private val settings: SettingsRepository) : ViewModel() {
    private val core = combine(repository.tasks, repository.sessions, repository.goals) { tasks, sessions, goals ->
        Triple(tasks, sessions, goals)
    }
    val state: StateFlow<ClockUiState> = combine(core, repository.checkIns, repository.wallet, repository.activeSession, settings.isDarkMode) { core, checkIns, wallet, active, isDark ->
        ClockUiState(core.first, core.second, core.third, checkIns, wallet ?: WalletEntity(), active, isDark)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClockUiState())

    fun saveTask(task: TaskEntity) = viewModelScope.launch { repository.saveTask(task) }
    fun archiveTask(task: TaskEntity) = viewModelScope.launch { repository.archiveTask(task) }
    fun saveGoal(goal: GoalEntity) = viewModelScope.launch { repository.saveGoal(goal) }
    fun deleteGoal(goal: GoalEntity) = viewModelScope.launch { repository.deleteGoal(goal) }
    fun start(task: TaskEntity, mode: TimerMode, durationMs: Long?, note: String, onStarted: (Boolean) -> Unit) = viewModelScope.launch { onStarted(repository.startSession(task, mode, durationMs, note)) }
    fun pause() = viewModelScope.launch { repository.pauseSession() }
    fun resume() = viewModelScope.launch { repository.resumeSession() }
    fun finish(auto: Boolean = false) = viewModelScope.launch { repository.finishSession(auto) }
    fun saveSession(session: TimerSessionEntity) = viewModelScope.launch { repository.saveSession(session) }
    fun deleteSession(session: TimerSessionEntity) = viewModelScope.launch { repository.deleteSession(session) }
    fun exportBackup(onComplete: (String) -> Unit) = viewModelScope.launch { onComplete(repository.exportBackup()) }
    fun importBackup(raw: String, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch { onResult(runCatching { repository.importBackup(raw) }) }
    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { settings.setDarkMode(enabled) }
    fun useMakeup(onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(repository.useMakeupCard()) }
    fun durations(start: Long, end: Long) = repository.durationsByTask(state.value.sessions, start, end)

    companion object {
        fun factory(repository: ClockRepository, settings: SettingsRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ClockViewModel(repository, settings) as T
        }
    }
}
