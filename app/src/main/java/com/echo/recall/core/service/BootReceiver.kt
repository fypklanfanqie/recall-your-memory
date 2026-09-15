package com.echo.recall.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机/升级后提示恢复聆听。
 *
 * 注意：Android 15 (API 35) 起禁止从 BOOT_COMPLETED 启动 microphone 类型的前台服务，
 * 因此这里**不尝试自启**，只发一条可点按的通知（点击 = 用户操作，合法）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        RecorderNotifications.ensureChannel(context)
        RecorderNotifications.notifyResumePrompt(context)
    }
}
