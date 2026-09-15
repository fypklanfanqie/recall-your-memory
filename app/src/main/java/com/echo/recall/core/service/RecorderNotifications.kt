package com.echo.recall.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.echo.recall.MainActivity
import com.echo.recall.R

/** 常驻聆听通知：可见、可暂停、可直接回溯。 */
object RecorderNotifications {

    const val CHANNEL_ID = "echo_listening"
    const val NOTIFICATION_ID = 1001
    const val RESUME_NOTIFICATION_ID = 1002
    private const val CHANNEL_NAME = "聆听状态"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "回声在后台聆听环境声时显示的常驻通知"
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        windowLabel: String,
        statusText: String,
        paused: Boolean,
    ) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_echo)
        .setContentTitle("回声 · $statusText")
        .setContentText("只保留最近 $windowLabel 的人声，点此查看")
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(openAppIntent(context))
        .addAction(
            0,
            "回溯",
            serviceIntent(context, RecorderService.ACTION_RECALL, requestCode = 1),
        )
        .addAction(
            0,
            if (paused) "继续" else "暂停",
            serviceIntent(
                context,
                if (paused) RecorderService.ACTION_RESUME else RecorderService.ACTION_PAUSE,
                requestCode = 2,
            ),
        )
        .build()

    fun notify(context: Context, windowLabel: String, statusText: String, paused: Boolean) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching {
            manager.notify(NOTIFICATION_ID, build(context, windowLabel, statusText, paused))
        }
    }

    /** 重启/升级后提示恢复聆听（点击即启动，属用户操作） */
    fun notifyResumePrompt(context: Context) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_echo)
            .setContentTitle("回声已停止聆听")
            .setContentText("系统限制重启后不能自动录音，点按即可恢复")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(serviceIntent(context, RecorderService.ACTION_START, requestCode = 3))
            .build()
        runCatching { manager.notify(RESUME_NOTIFICATION_ID, notification) }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RecorderService::class.java).setAction(action)
        return PendingIntent.getForegroundService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
