package com.echo.recall.core.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.echo.recall.core.asr.TranscriptionCoordinator
import com.echo.recall.core.audio.RecorderEngine
import com.echo.recall.core.audio.VadSensitivityPreset
import com.echo.recall.core.data.MemoryRepository
import com.echo.recall.core.data.settings.EchoSettings
import com.echo.recall.core.data.settings.SettingsRepository
import com.echo.recall.core.data.settings.VadSensitivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 前台服务：持有麦克风、维持常驻通知（Android 11+ 要求 while-in-use 语义）。
 */
@AndroidEntryPoint
class RecorderService : Service() {

    @Inject lateinit var engine: RecorderEngine
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var memoryRepository: MemoryRepository
    @Inject lateinit var transcription: TranscriptionCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        RecorderNotifications.ensureChannel(this)
        // 必须立刻进入前台（startForegroundService 有 5 秒限制）
        promoteToForeground(statusText = "正在聆听", paused = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { shutdown() }
            }
            ACTION_PAUSE -> {
                engine.pause()
                refreshNotification()
            }
            ACTION_RESUME -> {
                scope.launch {
                    val settings = settingsRepository.settings.first()
                    engine.resume()
                    refreshNotification(settings)
                }
            }
            ACTION_RECALL -> {
                scope.launch { performRecall() }
            }
            else -> {
                // intent == null：系统在进程被杀后按 START_STICKY 重启服务。
                // 仅当用户此前开启过聆听时才恢复，避免「已手动停止」后被复活。
                val restartedAfterKill = intent == null
                scope.launch {
                    val settings = settingsRepository.settings.first()
                    if (restartedAfterKill && !settings.recordingEnabled) {
                        stopSelf()
                    } else {
                        startListening()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户划掉后台卡片：安排一次自愈探测（原生机可复活；激进 ROM 的 force stop 无法自愈）
        if (engine.isListening || !engine.status.value.let { it.state == RecorderEngine.State.IDLE }) {
            scheduleKeepAliveSelfHeal(this)
        }
    }

    private suspend fun startListening() {
        val settings = settingsRepository.settings.first()
        engine.start(
            windowMs = settings.windowSeconds * 1000L,
            preset = settings.vadSensitivity.toPreset(),
        )
        settingsRepository.setRecordingEnabled(true)
        acquireWakeLock()
        refreshNotification(settings)
    }

    private suspend fun performRecall() {
        val settings = settingsRepository.settings.first()
        val segments = engine.recall()
        if (segments.isEmpty()) {
            RecorderNotifications.notify(
                this,
                windowLabel = settings.windowLabel(),
                statusText = "最近 ${settings.windowLabel()} 没有记录到人声",
                paused = !engine.isListening,
            )
            return
        }
        val ordered = segments.sortedBy { it.startMs }
        val entity = memoryRepository.createFromRecall(ordered, engine.epochOffsetMs())
        // 模型已就绪则立刻本地转写（PCM 还在内存里，无需解码）
        if (entity != null && transcription.isModelReady) {
            transcription.transcribeSegments(entity.id, ordered.map { it.pcm })
        }
        RecorderNotifications.notify(
            this,
            windowLabel = settings.windowLabel(),
            statusText = when {
                entity == null -> "记忆生成失败"
                transcription.isModelReady -> "已生成记忆，正在本地转写"
                else -> "已生成本次记忆（未下载转写模型）"
            },
            paused = !engine.isListening,
        )
    }

    private suspend fun shutdown() {
        engine.stop()
        settingsRepository.setRecordingEnabled(false)
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    private fun refreshNotification(settings: EchoSettings? = null) {
        scope.launch {
            val current = settings ?: settingsRepository.settings.first()
            val status = engine.status.value
            val paused = !engine.isListening
            RecorderNotifications.notify(
                this@RecorderService,
                windowLabel = current.windowLabel(),
                statusText = when {
                    status.state == RecorderEngine.State.ERROR -> "出错：${status.message ?: "未知"}"
                    paused -> "已暂停"
                    status.speechDetected -> "正在记录人声"
                    else -> "正在聆听"
                },
                paused = paused,
            )
        }
    }

    private fun promoteToForeground(statusText: String, paused: Boolean) {
        val notification = RecorderNotifications.build(
            context = this,
            windowLabel = EchoSettings.formatWindow(EchoSettings.DEFAULT_WINDOW_MINUTES),
            statusText = statusText,
            paused = paused,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, RecorderNotifications.NOTIFICATION_ID, notification, type)
        }.onFailure { Log.e(TAG, "startForeground failed", it) }
    }

    private fun stopForegroundCompat() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val manager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecorderService"
        private const val WAKELOCK_TAG = "echo:listening"

        /** 自愈探测用：服务是否活着 */
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START = "com.echo.recall.action.START"
        const val ACTION_STOP = "com.echo.recall.action.STOP"
        const val ACTION_PAUSE = "com.echo.recall.action.PAUSE"
        const val ACTION_RESUME = "com.echo.recall.action.RESUME"
        const val ACTION_RECALL = "com.echo.recall.action.RECALL"

        fun start(context: Context) = send(context, ACTION_START)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun recall(context: Context) = send(context, ACTION_RECALL)

        private fun send(context: Context, action: String) {
            val intent = Intent(context, RecorderService::class.java).setAction(action)
            if (action == ACTION_START || action == ACTION_RESUME) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

private fun VadSensitivity.toPreset(): VadSensitivityPreset = when (this) {
    VadSensitivity.LOW -> VadSensitivityPreset.LOW
    VadSensitivity.MEDIUM -> VadSensitivityPreset.MEDIUM
    VadSensitivity.HIGH -> VadSensitivityPreset.HIGH
}
