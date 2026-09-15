package com.echo.recall.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.audio.RecorderEngine
import com.echo.recall.core.data.MemoryRepository
import com.echo.recall.core.data.db.MemoryEntity
import com.echo.recall.core.data.settings.EchoSettings
import com.echo.recall.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val engine: RecorderEngine,
    private val memoryRepository: MemoryRepository,
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

    /** 回溯：取出窗口内录音 → 生成记忆 */
    fun recall() {
        viewModelScope.launch {
            val segments = withContext(Dispatchers.Default) { engine.recall() }
            if (segments.isEmpty()) {
                _messages.tryEmit("最近 ${settings.value.windowLabel()} 没有记录到人声")
                return@launch
            }
            val memory = memoryRepository.createFromRecall(segments, engine.epochOffsetMs())
            _messages.tryEmit(
                if (memory != null) "已生成记忆：${segments.size} 段人声" else "音频保存失败",
            )
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
