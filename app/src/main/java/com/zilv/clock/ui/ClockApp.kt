@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zilv.clock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.zilv.clock.data.CheckInKind
import com.zilv.clock.data.GoalEntity
import com.zilv.clock.data.GoalPeriod
import com.zilv.clock.data.SessionStatus
import com.zilv.clock.data.TaskDuration
import com.zilv.clock.data.TaskEntity
import com.zilv.clock.data.TimerMode
import com.zilv.clock.data.TimerSessionEntity
import com.zilv.clock.domain.TimeMath
import com.zilv.clock.timer.TimerForegroundService
import java.time.LocalDate
import java.time.YearMonth

private enum class Tab(val label: String) { TASKS("任务"), GOALS("目标"), STATS("统计") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockApp(model: ClockViewModel, state: ClockUiState) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.TASKS) }
    var taskDialog by remember { mutableStateOf<TaskEntity?>(null) }
    var goalDialog by remember { mutableStateOf<GoalEntity?>(null) }
    var startDialog by remember { mutableStateOf(false) }
    var sessionDialog by remember { mutableStateOf<TimerSessionEntity?>(null) }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("自律时钟", fontWeight = FontWeight.Bold) }, actions = { IconButton(onClick = { model.setDarkMode(!state.isDarkMode) }) { Icon(if (state.isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, "切换主题") } }) },
        bottomBar = { NavigationBar { Tab.entries.forEach { item -> NavigationBarItem(selected = item == tab, onClick = { tab = item }, icon = { Icon(if (item == Tab.TASKS) Icons.Default.TaskAlt else if (item == Tab.GOALS) Icons.Default.Flag else Icons.Default.Analytics, item.label) }, label = { Text(item.label) }) } } },
        floatingActionButton = { if (tab == Tab.TASKS) FloatingActionButton(onClick = { if (state.active == null) startDialog = true }) { Icon(Icons.Default.Add, "开始任务") } else if (tab == Tab.GOALS) FloatingActionButton(onClick = { goalDialog = GoalEntity(period = GoalPeriod.DAILY, name = "") }) { Icon(Icons.Default.Add, "新建目标") } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TASKS -> TasksScreen(state, model, { taskDialog = it }, { startDialog = true }, { sessionDialog = it })
                Tab.GOALS -> GoalsScreen(state, model, { goalDialog = it })
                Tab.STATS -> StatsScreen(state, model)
            }
        }
    }
    taskDialog?.let { TaskDialog(it, onDismiss = { taskDialog = null }, onSave = { model.saveTask(it); taskDialog = null }, onDelete = { model.archiveTask(it); taskDialog = null }) }
    goalDialog?.let { GoalDialog(it, state.tasks, onDismiss = { goalDialog = null }, onSave = { model.saveGoal(it); goalDialog = null }, onDelete = { model.deleteGoal(it); goalDialog = null }) }
    if (startDialog) StartDialog(state.tasks, onDismiss = { startDialog = false }, onStart = { task, mode, duration, note -> model.start(task, mode, duration, note) { started -> if (started) TimerForegroundService.start(context) }; startDialog = false })
    sessionDialog?.let { SessionDialog(it, state.tasks, onDismiss = { sessionDialog = null }, onSave = { model.saveSession(it); sessionDialog = null }, onDelete = { model.deleteSession(it); sessionDialog = null }) }
}

@Composable
private fun TasksScreen(state: ClockUiState, model: ClockViewModel, editTask: (TaskEntity) -> Unit, openStart: () -> Unit, editSession: (TimerSessionEntity) -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val todayDuration = model.durations(TimeMath.dayStart(today), TimeMath.dayEnd(today)).sumOf { it.durationMs }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)); TodayCard(todayDuration, state) }
        state.active?.let { active -> item { ActiveSessionCard(active, onPause = { model.pause() }, onResume = { model.resume() }, onFinish = { model.finish(); TimerForegroundService.stop(context) }) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("我的任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { editTask(TaskEntity(name = "", color = 0xFF2563EBL)) }) { Text("新建") }; FilledTonalButton(onClick = openStart, enabled = state.active == null) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("开始专注") } } } }
        if (state.tasks.isEmpty()) item { EmptyState("还没有任务", "创建一个任务，开始记录今天的专注。") }
        items(state.tasks, key = { it.id }) { task -> TaskRow(task, { openStart() }, { editTask(task) }) }
        item { Spacer(Modifier.height(8.dp)); Text("最近记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (state.sessions.filter { it.status == SessionStatus.FINISHED }.isEmpty()) item { EmptyState("暂无已完成记录", "结束一次专注后会出现在这里。") }
        items(state.sessions.filter { it.status == SessionStatus.FINISHED }.take(10), key = { it.id }) { session -> SessionRow(session, { editSession(session) }) }
        item { Spacer(Modifier.height(92.dp)) }
    }
}

@Composable
private fun TodayCard(duration: Long, state: ClockUiState) {
    val dailyGoals = state.goals.filter { it.enabled && it.period == GoalPeriod.DAILY && LocalDate.now().dayOfWeek.value in it.weekdays.split(',').mapNotNull(String::toIntOrNull) }
    val dayStart = TimeMath.dayStart(LocalDate.now())
    val dayEnd = TimeMath.dayEnd(LocalDate.now())
    val progress = dailyGoals.maxOfOrNull { goal ->
        val goalDuration = state.sessions.flatMap { TimeMath.slices(it) }.sumOf { slice ->
            if ((goal.taskId == null || goal.taskId == slice.taskId) && slice.start < dayEnd && slice.end > dayStart) minOf(slice.end, dayEnd) - maxOf(slice.start, dayStart) else 0L
        }
        (goalDuration / (goal.targetMinutes * 60_000f)).coerceIn(0f, 1f)
    } ?: 0f
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("今天的专注", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(TimeMath.formatDuration(duration), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (dailyGoals.isNotEmpty()) { LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth()); Text(if (progress >= 1f) "今日目标已达成，打卡成功" else "距离任一今日目标还差一点", style = MaterialTheme.typography.bodySmall) } else Text("设置每日目标后，这里会显示进度。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActiveSessionCard(session: TimerSessionEntity, onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) { while (true) { withFrameNanos { now = System.currentTimeMillis() } } }
    val elapsed = TimeMath.elapsedMs(session, now)
    val text = if (session.mode == TimerMode.COUNT_UP) TimeMath.formatDuration(elapsed) else TimeMath.formatDuration((session.plannedDurationMs ?: 0) - elapsed)
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("正在专注：${session.taskNameSnapshot}", fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = if (session.status == SessionStatus.PAUSED) onResume else onPause) { Icon(if (session.status == SessionStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, null); Spacer(Modifier.width(4.dp)); Text(if (session.status == SessionStatus.PAUSED) "继续" else "暂停") }
                Button(onClick = onFinish) { Text("结束") }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onStart: () -> Unit, onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(task.color)))
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(task.name, fontWeight = FontWeight.SemiBold); Text(if (task.enabled) "可开始专注" else "已暂停使用", style = MaterialTheme.typography.bodySmall) }
            IconButton(onClick = onStart, enabled = task.enabled) { Icon(Icons.Default.PlayArrow, "开始") }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
        }
    }
}

@Composable
private fun SessionRow(session: TimerSessionEntity, onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(session.taskColorSnapshot))); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(session.taskNameSnapshot, fontWeight = FontWeight.SemiBold); Text(TimeMath.dateAt(session.startedAt).toString() + if (session.note.isBlank()) "" else "  ${session.note}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(TimeMath.formatDuration(TimeMath.completedMs(session)), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun EmptyState(title: String, message: String) = Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(message, style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun GoalsScreen(state: ClockUiState, model: ClockViewModel, editGoal: (GoalEntity) -> Unit) {
    var period by remember { mutableStateOf(GoalPeriod.DAILY) }
    val currentGoals = state.goals.filter { it.period == period }
    val today = LocalDate.now()
    val dailyKeys = state.checkIns.filter { it.period == GoalPeriod.DAILY }.associateBy { it.periodKey }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)); StreakCard(state, model) }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(GoalPeriod.DAILY to "每日目标", GoalPeriod.WEEKLY to "每周目标").forEachIndexed { index, (value, label) ->
                    SegmentedButton(selected = period == value, onClick = { period = value }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                }
            }
        }
        item { Text(if (period == GoalPeriod.DAILY) "当天达成任意一个生效目标，即自动打卡。" else "每周从周一开始累计，达成任意一个目标即可完成本周打卡。", style = MaterialTheme.typography.bodySmall) }
        if (currentGoals.isEmpty()) item { EmptyState("还没有${if (period == GoalPeriod.DAILY) "每日" else "每周"}目标", "点击右下角创建你的第一个目标。") }
        items(currentGoals, key = { it.id }) { goal -> GoalRow(goal, state, model, { editGoal(goal) }) }
        item { Text("本月打卡", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); CheckInCalendar(today, dailyKeys) }
        item { Spacer(Modifier.height(92.dp)) }
    }
}

@Composable
private fun StreakCard(state: ClockUiState, model: ClockViewModel) {
    var notice by remember { mutableStateOf<String?>(null) }
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("连续打卡", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) { Text("${state.wallet.currentStreak}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Spacer(Modifier.width(4.dp)); Text("天", Modifier.padding(bottom = 8.dp)); Spacer(Modifier.weight(1f)); AssistChip(onClick = { model.useMakeup { notice = if (it) "已使用消除卡补上昨天的打卡" else "暂时无法补卡：需要有消除卡，且只能补最近一个缺卡日" } }, label = { Text("消除卡 ${state.wallet.cards}/3") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }) }
            Text("最长连续 ${state.wallet.bestStreak} 天；每连续 7 天奖励 1 张消除卡。", style = MaterialTheme.typography.bodySmall)
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun GoalRow(goal: GoalEntity, state: ClockUiState, model: ClockViewModel, onEdit: () -> Unit) {
    val date = LocalDate.now()
    val (start, end) = if (goal.period == GoalPeriod.DAILY) TimeMath.dayStart(date) to TimeMath.dayEnd(date) else TimeMath.dayStart(TimeMath.weekStart(date)) to TimeMath.dayEnd(TimeMath.weekStart(date).plusDays(6))
    val current = model.durations(start, end).filter { goal.taskId == null || it.taskId == goal.taskId }.sumOf { it.durationMs }
    val target = goal.targetMinutes * 60_000L
    val progress = (current / target.toFloat()).coerceIn(0f, 1f)
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(goal.name, fontWeight = FontWeight.SemiBold); Text(if (goal.taskId == null) "全部任务" else state.tasks.firstOrNull { it.id == goal.taskId }?.name ?: "已删除任务", style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.Edit, "编辑") }
            LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${TimeMath.formatDuration(current)} / ${goal.targetMinutes} 分钟", style = MaterialTheme.typography.bodySmall); Text(if (progress >= 1f) "已达成" else "进行中", style = MaterialTheme.typography.bodySmall, color = if (progress >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CheckInCalendar(today: LocalDate, entries: Map<String, com.zilv.clock.data.CheckInEntity>) {
    val month = YearMonth.from(today)
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val days = (1..month.lengthOfMonth()).toList()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall) } }
        val slots = List(firstOffset) { null } + days.map { month.atDay(it) }
        slots.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { week.forEach { day -> if (day == null) Spacer(Modifier.size(32.dp)) else { val entry = entries[TimeMath.dateKey(day)]; val color = when (entry?.kind) { CheckInKind.NORMAL -> MaterialTheme.colorScheme.primary; CheckInKind.MAKEUP -> MaterialTheme.colorScheme.tertiary; null -> Color.Transparent }; Box(Modifier.size(32.dp).padding(3.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = if (entry == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary) } } } } }
    }
}

@Composable
private fun StatsScreen(state: ClockUiState, model: ClockViewModel) {
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.exportBackup { content -> context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }; backupMessage = "备份已导出" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pendingImport = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }.getOrNull(); if (pendingImport == null) backupMessage = "无法读取备份文件" }
    }
    var range by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val (start, end, label) = when (range) { 0 -> Triple(TimeMath.dayStart(today), TimeMath.dayEnd(today), "今日"); 1 -> Triple(TimeMath.dayStart(TimeMath.weekStart(today)), TimeMath.dayEnd(TimeMath.weekStart(today).plusDays(6)), "本周"); 2 -> Triple(TimeMath.dayStart(today.withDayOfMonth(1)), TimeMath.dayEnd(today.withDayOfMonth(today.lengthOfMonth())), "本月"); else -> Triple(0L, System.currentTimeMillis() + 1, "总历史") }
    val durations = model.durations(start, end)
    val total = durations.sumOf { it.durationMs }
    val sessions = state.sessions.count { it.status == SessionStatus.FINISHED && it.startedAt in start until end }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(4.dp)); SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf("每日", "每周", "每月", "历史").forEachIndexed { index, text -> SegmentedButton(selected = range == index, onClick = { range = index }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, 4)) { Text(text) } } } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { exportLauncher.launch("self-discipline-clock-backup.json") }, modifier = Modifier.weight(1f)) { Text("导出备份") }; OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("导入备份") } } }
        backupMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
        item { StatisticsOverview(label, total, sessions, durations) }
        if (durations.isEmpty()) item { EmptyState("这个周期还没有学习记录", "完成一次任务后，这里会生成时间趋势和任务占比。") }
        else { item { Text("学习趋势", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); TrendChart(state, model, range) }; item { Text("任务时间占比", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); PieChart(durations) }; item { Text("任务排行", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; items(durations) { DurationRow(it, total) } }
        item { Spacer(Modifier.height(92.dp)) }
    }
    pendingImport?.let { raw -> AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text("恢复本地备份？") }, text = { Text("导入会覆盖当前手机中的任务、记录、目标、打卡和消除卡数据。") }, confirmButton = { Button(onClick = { model.importBackup(raw) { result -> backupMessage = result.fold(onSuccess = { "备份已恢复" }, onFailure = { "备份无效：${it.message ?: "无法恢复"}" }); pendingImport = null } }) { Text("确认覆盖") } }, dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } }) }
}

@Composable
private fun TrendChart(state: ClockUiState, model: ClockViewModel, range: Int) {
    val today = LocalDate.now()
    val dayCount = when (range) { 0 -> 1; 1 -> 7; 2 -> minOf(today.lengthOfMonth(), 14); else -> 7 }
    val first = if (range == 0) today else today.minusDays((dayCount - 1).toLong())
    val values = (0 until dayCount).map { offset ->
        val date = first.plusDays(offset.toLong())
        model.durations(TimeMath.dayStart(date), TimeMath.dayEnd(date)).sumOf { it.durationMs }
    }
    val top = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.secondary
    Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val gap = size.width / (values.size * 2f)
                val barWidth = gap.coerceAtLeast(8f)
                values.forEachIndexed { index, value ->
                    val height = (value.toFloat() / top * (size.height - 8.dp.toPx())).coerceAtLeast(if (value > 0) 3.dp.toPx() else 0f)
                    val left = gap * (index * 2 + 0.5f)
                    drawRoundRect(barColor, Offset(left, size.height - height), Size(barWidth, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(first.toString().substring(5), style = MaterialTheme.typography.labelSmall); Text("最高 ${TimeMath.formatDuration(top)}", style = MaterialTheme.typography.labelSmall); Text(today.toString().substring(5), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable private fun StatisticsOverview(label: String, total: Long, sessions: Int, durations: List<TaskDuration>) = Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(label + "学习概览", fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("总时长", TimeMath.formatDuration(total)); Metric("完成次数", sessions.toString()); Metric("最长任务", TimeMath.formatDuration(durations.maxOfOrNull { it.durationMs } ?: 0)) } } }
@Composable private fun Metric(title: String, value: String) = Column { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(title, style = MaterialTheme.typography.labelSmall) }
@Composable private fun DurationRow(item: TaskDuration, total: Long) = Card(shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(14.dp)) { Row { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(item.color))); Spacer(Modifier.width(8.dp)); Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(TimeMath.formatDuration(item.durationMs)) }; LinearProgressIndicator(progress = { item.durationMs / total.toFloat() }, Modifier.fillMaxWidth().padding(top = 8.dp)) } }

@Composable
private fun PieChart(items: List<TaskDuration>) {
    val total = items.sumOf { it.durationMs }.toFloat()
    val surface = MaterialTheme.colorScheme.surface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(150.dp)) { var angle = -90f; items.forEach { val sweep = 360f * it.durationMs / total; drawArc(Color(it.color), angle, sweep, true, Offset.Zero, Size(size.width, size.height)); angle += sweep }; drawCircle(surface, radius = size.minDimension * .27f) }
        Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { items.take(4).forEach { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(Color(it.color))); Spacer(Modifier.width(6.dp)); Text("${it.name} ${(it.durationMs * 100 / total).toInt()}%", style = MaterialTheme.typography.bodySmall) } } }
    }
}

@Composable
private fun TaskDialog(task: TaskEntity, onDismiss: () -> Unit, onSave: (TaskEntity) -> Unit, onDelete: () -> Unit) {
    var name by remember(task.id) { mutableStateOf(task.name) }
    var color by remember(task.id) { mutableStateOf(task.color) }
    val colors = listOf(0xFF2563EBL, 0xFF16A34AL, 0xFFEA580CL, 0xFF9333EAL, 0xFFDB2777L, 0xFF0891B2L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task.id == 0L) "新建任务" else "编辑任务") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(name, { name = it }, label = { Text("任务名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Text("任务颜色", style = MaterialTheme.typography.labelLarge); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { colors.forEach { value -> Box(Modifier.size(28.dp).clip(CircleShape).background(Color(value)).clickable { color = value }.then(if (color == value) Modifier.padding(3.dp) else Modifier)) } } } },
        confirmButton = { Button(onClick = { if (name.trim().isNotEmpty()) onSave(task.copy(name = name.trim(), color = color)) }) { Text("保存") } },
        dismissButton = { Row { if (task.id != 0L) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除任务") }; TextButton(onClick = onDismiss) { Text("取消") } } },
    )
}

@Composable
private fun StartDialog(tasks: List<TaskEntity>, onDismiss: () -> Unit, onStart: (TaskEntity, TimerMode, Long?, String) -> Unit) {
    var selectedId by remember { mutableStateOf(tasks.firstOrNull()?.id) }
    var mode by remember { mutableStateOf(TimerMode.COUNT_UP) }
    var minutesText by remember { mutableStateOf("25") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始专注") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tasks.isEmpty()) Text("请先创建一个任务。") else {
                    Text("选择任务", style = MaterialTheme.typography.labelLarge)
                    tasks.forEach { task -> AssistChip(onClick = { selectedId = task.id }, label = { Text(task.name) }, leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Color(task.color))) }) }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf(TimerMode.COUNT_UP to "正计时", TimerMode.COUNT_DOWN to "倒计时").forEachIndexed { index, (value, text) -> SegmentedButton(selected = mode == value, onClick = { mode = value }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, 2)) { Text(text) } } }
                    if (mode == TimerMode.COUNT_DOWN) OutlinedTextField(minutesText, { minutesText = it.filter(Char::isDigit) }, label = { Text("倒计时分钟") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                }
            }
        },
        confirmButton = { Button(enabled = selectedId != null && (mode == TimerMode.COUNT_UP || (minutesText.toLongOrNull() ?: 0) > 0), onClick = { tasks.firstOrNull { it.id == selectedId }?.let { onStart(it, mode, if (mode == TimerMode.COUNT_DOWN) (minutesText.toLongOrNull() ?: 0L) * 60_000 else null, note.trim()) } }) { Text("开始") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GoalDialog(goal: GoalEntity, tasks: List<TaskEntity>, onDismiss: () -> Unit, onSave: (GoalEntity) -> Unit, onDelete: () -> Unit) {
    var name by remember(goal.id) { mutableStateOf(goal.name) }
    var minutes by remember(goal.id) { mutableStateOf(goal.targetMinutes.takeIf { it > 0 }?.toString() ?: "120") }
    var period by remember(goal.id) { mutableStateOf(goal.period) }
    var taskId by remember(goal.id) { mutableStateOf(goal.taskId) }
    var weekdays by remember(goal.id) { mutableStateOf(goal.weekdays.split(',').mapNotNull(String::toIntOrNull).toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal.id == 0L) "新建目标" else "编辑目标") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("目标名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { listOf(GoalPeriod.DAILY to "每日", GoalPeriod.WEEKLY to "每周").forEachIndexed { index, (value, text) -> SegmentedButton(selected = period == value, onClick = { period = value }, shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index, 2)) { Text(text) } } }
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("目标分钟") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("统计范围", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = { taskId = null }, label = { Text("全部任务") }); tasks.forEach { task -> AssistChip(onClick = { taskId = task.id }, label = { Text(task.name) }) } }
            if (period == GoalPeriod.DAILY) { Text("适用星期", style = MaterialTheme.typography.labelLarge); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { (1..7).forEach { day -> AssistChip(onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day }, label = { Text(listOf("一", "二", "三", "四", "五", "六", "日")[day - 1]) }) } } }
        } },
        confirmButton = { Button(onClick = { val m = minutes.toIntOrNull() ?: 0; if (name.trim().isNotBlank() && m > 0 && (period == GoalPeriod.WEEKLY || weekdays.isNotEmpty())) onSave(goal.copy(name = name.trim(), targetMinutes = m, period = period, taskId = taskId, weekdays = weekdays.sorted().joinToString(","))) }) { Text("保存") } },
        dismissButton = { Row { if (goal.id != 0L) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }; TextButton(onClick = onDismiss) { Text("取消") } } },
    )
}

@Composable
private fun SessionDialog(session: TimerSessionEntity, tasks: List<TaskEntity>, onDismiss: () -> Unit, onSave: (TimerSessionEntity) -> Unit, onDelete: () -> Unit) {
    var note by remember(session.id) { mutableStateOf(session.note) }
    var taskId by remember(session.id) { mutableStateOf(session.taskId) }
    val formatter = remember { java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    var startText by remember(session.id) { mutableStateOf(java.time.Instant.ofEpochMilli(session.startedAt).atZone(TimeMath.zone).format(formatter)) }
    var endText by remember(session.id) { mutableStateOf(session.endedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(TimeMath.zone).format(formatter) } ?: "") }
    var timeError by remember(session.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(startText, { startText = it; timeError = false }, label = { Text("开始时间（yyyy-MM-dd HH:mm）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = timeError); OutlinedTextField(endText, { endText = it; timeError = false }, label = { Text("结束时间（yyyy-MM-dd HH:mm）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = timeError); if (timeError) Text("时间格式不正确，且结束时间必须晚于开始时间。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall); Text("任务", style = MaterialTheme.typography.labelLarge); tasks.forEach { task -> AssistChip(onClick = { taskId = task.id }, label = { Text(task.name) }) }; OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { val start = runCatching { java.time.LocalDateTime.parse(startText, formatter).atZone(TimeMath.zone).toInstant().toEpochMilli() }.getOrNull(); val end = runCatching { java.time.LocalDateTime.parse(endText, formatter).atZone(TimeMath.zone).toInstant().toEpochMilli() }.getOrNull(); if (start == null || end == null || end <= start) { timeError = true } else { val target = tasks.firstOrNull { it.id == taskId }; onSave(session.copy(taskId = taskId, taskNameSnapshot = target?.name ?: session.taskNameSnapshot, taskColorSnapshot = target?.color ?: session.taskColorSnapshot, startedAt = start, endedAt = end, pausedDurationMs = 0, note = note)) } }) { Text("保存") } },
        dismissButton = { Row { IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }; TextButton(onClick = onDismiss) { Text("取消") } } },
    )
}
