package com.echo.recall.core.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.echo.recall.core.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 保活自愈：用户划掉后台卡片（国产 ROM 常见 ≈ 强制停止）或进程被杀后，
 * 闹钟唤醒时检查「用户开过聆听但服务没在跑」→ 尝试拉起前台服务。
 *
 * 平台限制（知情的尽力而为）：
 * - Android 12+ 禁止从后台启动麦克风前台服务 → 拉起失败时静默放弃，
 *   由既有的「回声已停止聆听，点按即可恢复」通知 + 快捷磁贴 + 打开 App 自动恢复兜底；
 * - 若 ROM 执行的是强制停止（force stop），闹钟会被系统取消，无法自愈。
 */
const val ACTION_KEEPALIVE_SELF_HEAL = "com.echo.recall.action.KEEPALIVE_SELF_HEAL"
private const val REQUEST_CODE = 1001
private const val TAG = "KeepAliveAlarm"

/** 进程被移出后台卡片时调用：安排一次延迟自愈探测 */
fun scheduleKeepAliveSelfHeal(context: Context) {
    val am = context.getSystemService(AlarmManager::class.java) ?: return
    val pi = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, KeepAliveAlarm::class.java).setAction(ACTION_KEEPALIVE_SELF_HEAL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val triggerAt = SystemClock.elapsedRealtime() + 2_500L
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
    }.onFailure { Log.w(TAG, "schedule self-heal failed", it) }
}

@AndroidEntryPoint
class KeepAliveAlarm : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_KEEPALIVE_SELF_HEAL) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val enabled = settingsRepository.settings.first().recordingEnabled
                if (!enabled || RecorderService.isRunning) return@launch
                Log.i(TAG, "self-heal: restarting recorder service")
                runCatching { RecorderService.start(context) }
                    .onFailure { Log.w(TAG, "self-heal start failed (background FGS restriction?)", it) }
            } finally {
                pending.finish()
            }
        }
    }
}
