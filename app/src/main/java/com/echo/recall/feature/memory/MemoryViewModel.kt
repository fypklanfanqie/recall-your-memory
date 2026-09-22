package com.echo.recall.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.audio.RecorderEngine
import com.echo.recall.core.data.MemoryRepository
import com.echo.recall.core.data.db.MemoryEntity
import com.echo.recall.core.data.settings.EchoSettings
import com.echo.recall.core.data.settings.SettingsRepository
import com.echo.recall.core.service.RecallCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val engine: RecorderEngine,
    private val memoryRepository: MemoryRepository,
    private val recallCoordinator: RecallCoordinator,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val status: StateFlow<RecorderEngine.Status> = engine.status

    val settings: StateFlow<EchoSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = EchoSettings(),
    )

    val memories: StateFlow<List<MemoryEntity>> = memoryRepository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 回溯：取出窗口内录音 → 生成记忆 → 启动本地转写 */
    fun recall() {
        viewModelScope.launch {
            // 走 RecallCoordinator，与前台服务的「回溯」共用同一条流程。
            // v1.0 这里是自己实现的一份，**漏了启动转写**，导致首页大按钮产出的
            // 记忆永远停在「转写中」。
            val message = when (val outcome = recallCoordinator.recall()) {
                RecallCoordinator.Outcome.NoSpeech ->
                    "最近 ${settings.value.windowLabel()} 没有记录到人声"
                RecallCoordinator.Outcome.Failed -> "音频保存失败"
                is RecallCoordinator.Outcome.Created -> when {
                    outcome.transcribing -> "已生成记忆：${outcome.segmentCount} 段人声，正在转写"
                    else -> "已生成记忆：${outcome.segmentCount} 段人声（未下载转写模型）"
                }
            }
            _messages.tryEmit(message)
        }
    }

    fun clearBuffer() {
        engine.clearBuffer()
        _messages.tryEmit("已清空当前缓冲")
    }

    fun delete(id: String) {
        viewModelScope.launch {
            memoryRepository.delete(id)
            _messages.tryEmit("已删除该记忆")
        }
    }

    fun toggleFavorite(memory: MemoryEntity) {
        viewModelScope.launch { memoryRepository.setFavorited(memory.id, !memory.favorited) }
    }
}
