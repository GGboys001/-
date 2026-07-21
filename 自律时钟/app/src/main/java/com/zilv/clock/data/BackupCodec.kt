package com.zilv.clock.data

import org.json.JSONArray
import org.json.JSONObject

data class ClockBackup(
    val tasks: List<TaskEntity>,
    val sessions: List<TimerSessionEntity>,
    val goals: List<GoalEntity>,
    val checkIns: List<CheckInEntity>,
    val wallet: WalletEntity?,
    val walletEvents: List<WalletEventEntity>,
)

object BackupCodec {
    const val VERSION = 1

    fun encode(backup: ClockBackup): String = JSONObject().apply {
        put("version", VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("tasks", JSONArray().apply { backup.tasks.forEach { put(JSONObject().apply { put("id", it.id); put("name", it.name); put("color", it.color); put("icon", it.icon); put("enabled", it.enabled); put("createdAt", it.createdAt); put("deleted", it.deleted) }) }) })
        put("sessions", JSONArray().apply { backup.sessions.forEach { put(JSONObject().apply { put("id", it.id); put("taskId", it.taskId); put("taskNameSnapshot", it.taskNameSnapshot); put("taskColorSnapshot", it.taskColorSnapshot); put("startedAt", it.startedAt); put("endedAt", it.endedAt); put("pausedAt", it.pausedAt); put("pausedDurationMs", it.pausedDurationMs); put("mode", it.mode.name); put("plannedDurationMs", it.plannedDurationMs); put("status", it.status.name); put("note", it.note); put("autoFinished", it.autoFinished) }) }) })
        put("goals", JSONArray().apply { backup.goals.forEach { put(JSONObject().apply { put("id", it.id); put("name", it.name); put("period", it.period.name); put("targetMinutes", it.targetMinutes); put("taskId", it.taskId); put("weekdays", it.weekdays); put("enabled", it.enabled); put("createdAt", it.createdAt) }) }) })
        put("checkIns", JSONArray().apply { backup.checkIns.forEach { put(JSONObject().apply { put("periodKey", it.periodKey); put("period", it.period.name); put("kind", it.kind.name); put("createdAt", it.createdAt) }) }) })
        backup.wallet?.let { put("wallet", JSONObject().apply { put("cards", it.cards); put("currentStreak", it.currentStreak); put("bestStreak", it.bestStreak); put("rewardedBlocks", it.rewardedBlocks) }) }
        put("walletEvents", JSONArray().apply { backup.walletEvents.forEach { put(JSONObject().apply { put("id", it.id); put("delta", it.delta); put("reason", it.reason); put("createdAt", it.createdAt) }) }) })
    }.toString(2)

    fun decode(raw: String): ClockBackup {
        val root = JSONObject(raw)
        require(root.getInt("version") == VERSION) { "不支持的备份版本" }
        fun array(name: String) = root.optJSONArray(name) ?: JSONArray()
        val tasks = array("tasks").map { TaskEntity(it.getLong("id"), it.getString("name"), it.getLong("color"), it.optString("icon", "book"), it.optBoolean("enabled", true), it.getLong("createdAt"), it.optBoolean("deleted")) }
        val sessions = array("sessions").map { TimerSessionEntity(it.getLong("id"), it.getLong("taskId"), it.getString("taskNameSnapshot"), it.getLong("taskColorSnapshot"), it.getLong("startedAt"), it.nullableLong("endedAt"), it.nullableLong("pausedAt"), it.optLong("pausedDurationMs"), TimerMode.valueOf(it.getString("mode")), it.nullableLong("plannedDurationMs"), SessionStatus.valueOf(it.optString("status", "FINISHED")), it.optString("note"), it.optBoolean("autoFinished")) }
        val goals = array("goals").map { GoalEntity(it.getLong("id"), it.getString("name"), GoalPeriod.valueOf(it.getString("period")), it.getInt("targetMinutes"), it.nullableLong("taskId"), it.optString("weekdays", "1,2,3,4,5,6,7"), it.optBoolean("enabled", true), it.getLong("createdAt")) }
        val checkIns = array("checkIns").map { CheckInEntity(it.getString("periodKey"), GoalPeriod.valueOf(it.getString("period")), CheckInKind.valueOf(it.getString("kind")), it.getLong("createdAt")) }
        val walletJson = root.optJSONObject("wallet")
        val wallet = walletJson?.let { WalletEntity(cards = it.optInt("cards"), currentStreak = it.optInt("currentStreak"), bestStreak = it.optInt("bestStreak"), rewardedBlocks = it.optInt("rewardedBlocks")) }
        val events = array("walletEvents").map { WalletEventEntity(it.getLong("id"), it.getInt("delta"), it.getString("reason"), it.getLong("createdAt")) }
        return ClockBackup(tasks, sessions, goals, checkIns, wallet, events)
    }

    private fun <T> JSONArray.map(block: (JSONObject) -> T): List<T> = (0 until length()).map { block(getJSONObject(it)) }
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else optLong(key)
}
