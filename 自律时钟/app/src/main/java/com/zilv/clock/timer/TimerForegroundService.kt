package com.zilv.clock.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zilv.clock.ClockApplication
import com.zilv.clock.MainActivity
import com.zilv.clock.R
import com.zilv.clock.data.SessionStatus
import com.zilv.clock.domain.TimeMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var tickerJob: Job? = null
    private val repository get() = (application as ClockApplication).repository

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        when (intent?.action) {
            ACTION_PAUSE -> scope.launch { repository.pauseSession() }
            ACTION_RESUME -> scope.launch { repository.resumeSession() }
            ACTION_FINISH -> scope.launch { repository.finishSession(); stopSelf() }
        }
        tickerJob?.cancel()
        tickerJob = scope.launch {
            val session = repository.activeSession.first()
            if (session == null) { stopSelf(); return@launch }
            startForeground(NOTIFICATION_ID, notification(session.taskNameSnapshot, "正在恢复计时"))
            runTicker()
        }
        return START_STICKY
    }

    private suspend fun runTicker() {
        while (true) {
            val session = repository.activeSession.first() ?: break
            if (TimeMath.isCountdownComplete(session)) {
                repository.finishSession(autoFinished = true)
                notificationManager().notify(NOTIFICATION_ID, notification(session.taskNameSnapshot, "倒计时已完成"))
                break
            }
            val text = if (session.status == SessionStatus.PAUSED) "已暂停" else when (session.mode) {
                com.zilv.clock.data.TimerMode.COUNT_UP -> "已专注 ${TimeMath.formatDuration(TimeMath.elapsedMs(session))}"
                com.zilv.clock.data.TimerMode.COUNT_DOWN -> "剩余 ${TimeMath.formatDuration((session.plannedDurationMs ?: 0L) - TimeMath.elapsedMs(session))}"
            }
            notificationManager().notify(NOTIFICATION_ID, notification(session.taskNameSnapshot, text))
            delay(1_000)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(taskName: String, content: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(taskName)
        .setContentText(content)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(if (content == "已暂停") android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause, if (content == "已暂停") "继续" else "暂停", actionPendingIntent(if (content == "已暂停") ACTION_RESUME else ACTION_PAUSE, 1))
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "结束", actionPendingIntent(ACTION_FINISH, 2))
        .build()

    private fun actionPendingIntent(action: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode, Intent(this, TimerForegroundService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notificationManager() = getSystemService(NotificationManager::class.java)
    private fun createChannel() {
        notificationManager().createNotificationChannel(NotificationChannel(CHANNEL_ID, "专注计时", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tickerJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 73
        const val ACTION_PAUSE = "com.zilv.clock.PAUSE"
        const val ACTION_RESUME = "com.zilv.clock.RESUME"
        const val ACTION_FINISH = "com.zilv.clock.FINISH"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, TimerForegroundService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, TimerForegroundService::class.java))
    }
}
