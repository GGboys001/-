package com.zilv.clock.domain

import com.zilv.clock.data.SessionStatus
import com.zilv.clock.data.TimerMode
import com.zilv.clock.data.TimerSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeMathTest {
    @Test fun `countdown duration does not exceed planned value`() {
        val session = TimerSessionEntity(taskId = 1, taskNameSnapshot = "数学", taskColorSnapshot = 0, startedAt = 1_000, endedAt = 100_000, mode = TimerMode.COUNT_DOWN, plannedDurationMs = 60_000, status = SessionStatus.FINISHED)
        assertEquals(60_000, TimeMath.completedMs(session))
        assertTrue(TimeMath.isCountdownComplete(session, 100_000))
    }

    @Test fun `week starts on monday`() {
        assertEquals(LocalDate.of(2026, 7, 20), TimeMath.weekStart(LocalDate.of(2026, 7, 21)))
        assertEquals(LocalDate.of(2026, 7, 20), TimeMath.weekStart(LocalDate.of(2026, 7, 26)))
    }
}
