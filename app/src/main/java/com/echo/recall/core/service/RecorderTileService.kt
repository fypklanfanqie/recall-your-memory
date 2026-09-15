package com.echo.recall.core.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.echo.recall.core.audio.RecorderEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 快捷磁贴：下拉通知栏一键恢复/停止聆听。
 *
 * 这是「系统杀后台」「重启手机」之后最方便的一键恢复入口
 * （Android 15 起麦克风前台服务不允许开机自启，只能由用户操作触发）。
 */
@AndroidEntryPoint
class RecorderTileService : TileService() {

    @Inject lateinit var engine: RecorderEngine

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (engine.isListening) {
            RecorderService.stop(this)
        } else {
            RecorderService.start(this)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val listening = engine.isListening
        tile.state = if (listening) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "回声"
        tile.subtitle = when {
            listening && engine.status.value.speechDetected -> "正在记录人声"
            listening -> "聆听中"
            else -> "已停止"
        }
        tile.updateTile()
    }
}
