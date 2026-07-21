package com.zilv.clock.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {
    @Test fun `backup round trip retains core fields`() {
        val input = ClockBackup(
            tasks = listOf(TaskEntity(1, "英语", 0xFF2563EBL)),
            sessions = listOf(TimerSessionEntity(1, 1, "英语", 0xFF2563EBL, 100, 200, mode = TimerMode.COUNT_UP, status = SessionStatus.FINISHED)),
            goals = listOf(GoalEntity(1, "工作日", GoalPeriod.DAILY, 120)),
            checkIns = listOf(CheckInEntity("2026-07-21", GoalPeriod.DAILY, CheckInKind.NORMAL)),
            wallet = WalletEntity(cards = 2, currentStreak = 7),
            walletEvents = emptyList(),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(input))
        assertEquals("英语", restored.tasks.single().name)
        assertEquals(120, restored.goals.single().targetMinutes)
        assertEquals(2, restored.wallet?.cards)
    }
}
