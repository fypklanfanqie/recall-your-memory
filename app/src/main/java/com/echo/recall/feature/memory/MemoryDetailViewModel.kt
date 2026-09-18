package com.echo.recall.feature.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.asr.TranscriptionCoordinator
import com.echo.recall.core.audio.MemoryPlayer
import com.echo.recall.core.data.MemoryRepository
import com.echo.recall.core.data.TodoRepository
import com.echo.recall.core.data.db.MemoryEntity
import com.echo.recall.core.data.db.MemorySegment
import com.echo.recall.core.data.db.TranscribeState
import com.echo.recall.core.llm.LlmService
import com.echo.recall.core.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AI 总结状态 */
sealed interface SummaryUi {
    data object Idle : SummaryUi
    data object Loading : SummaryUi
    data class Error(val message: String) : SummaryUi
}

/** 从记忆提取待办的状态 */
sealed interface TodoExtractUi {
    data object Idle : TodoExtractUi
    data object Loading : TodoExtractUi
    data class Done(val count: Int) : TodoExtractUi
    data class Error(val message: String) : TodoExtractUi
}

@HiltViewModel
class MemoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MemoryRepository,
    private val player: MemoryPlayer,
    private val transcription: TranscriptionCoordinator,
    private val llmService: LlmService,
    private val todoRepository: TodoRepository,
) : ViewModel() {

    private val memoryId: String = checkNotNull(savedStateHandle[ARG_MEMORY_ID]) { "memoryId missing" }

    val memory: StateFlow<MemoryEntity?> = repository.observeById(memoryId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val segments: StateFlow<List<MemorySegment>> = repository.observeById(memoryId)
        .map { entity -> entity?.let { repository.readSegments(it) } ?: emptyList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val playerState: StateFlow<MemoryPlayer.UiState> = player.state

    /** 该条记忆的转写进度（0-100），未在转写则为 null */
    val transcribeProgress: StateFlow<Int?> = transcription.progress
        .map { it[memoryId] }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val modelReady: Boolean get() = transcription.isModelReady

    private val _summaryState = MutableStateFlow<SummaryUi>(SummaryUi.Idle)
    val summaryState: StateFlow<SummaryUi> = _summaryState.asStateFlow()

    private val _todoUi = MutableStateFlow<TodoExtractUi>(TodoExtractUi.Idle)
    val todoUi: StateFlow<TodoExtractUi> = _todoUi.asStateFlow()

    val llmConfigured: Boolean get() = llmService.usableConfig() != null

    /** 云端 AI 总结（只发送转写文字） */
    fun summarize() {
        val entity = memory.value ?: return
        val transcript = entity.transcript
        if (transcript.isNullOrBlank()) {
            _events.tryEmit(DetailEvent.Message("请先本地转写这段记忆"))
            return
        }
        if (!llmConfigured) {
            _events.tryEmit(DetailEvent.Message(llmService.notConfiguredMessage()))
            return
        }
        _summaryState.value = SummaryUi.Loading
        viewModelScope.launch {
            val result = llmService.summarizeMemory(
                transcript = transcript,
                timeRange = TimeFormat.range(entity.wallStartAt, entity.wallEndAt),
            )
            result.fold(
                onSuccess = { summary ->
                    repository.setSummary(entity.id, summary, llmService.usableConfig()?.model)
                    _summaryState.value = SummaryUi.Idle
                },
                onFailure = { error ->
                    _summaryState.value = SummaryUi.Error(error.message ?: "总结失败")
                },
            )
        }
    }

    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

    fun togglePlay() {
        val path = memory.value?.audioPath ?: run {
            _events.tryEmit(DetailEvent.Message("这条记忆没有可用音频"))
            return
        }
        player.toggle(path)
    }

    fun seekTo(ms: Int) = player.seekTo(ms)

    fun toggleFavorite() {
        val current = memory.value ?: return
        viewModelScope.launch { repository.setFavorited(current.id, !current.favorited) }
    }

    fun retranscribe() {
        if (!transcription.isModelReady) {
            _events.tryEmit(DetailEvent.Message("请先在 设置 → 本地语音模型 下载模型"))
            return
        }
        transcription.transcribeMemory(memoryId)
        _events.tryEmit(DetailEvent.Message("已开始本地转写"))
    }

    /**
     * 把这条记忆的转写文字直接存进待办（不走 LLM）。
     */
    fun extractTodos() {
        val entity = memory.value ?: return
        val transcript = entity.transcript
        if (transcript.isNullOrBlank()) {
            _events.tryEmit(DetailEvent.Message("请先本地转写这段记忆"))
            return
        }
        _todoUi.value = TodoExtractUi.Loading
        viewModelScope.launch {
            val title = transcript.lineSequence().firstOrNull { it.isNotBlank() }
                ?.trim()?.take(30) ?: "记忆转写"
            val summary = memory.value?.summary
            val notes = buildString {
                append(transcript)
                if (!summary.isNullOrBlank()) {
                    append("\n\n—— AI 总结 ——\n")
                    append(summary)
                }
            }
            val count = todoRepository.createMany(listOf(title), notes = notes)
            _todoUi.value = TodoExtractUi.Done(count)
            _events.tryEmit(DetailEvent.Message("已把转写文字存入待办"))
        }
    }

    fun delete() {
        viewModelScope.launch {
            player.stop()
            repository.delete(memoryId)
            _events.tryEmit(DetailEvent.Deleted)
        }
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }

    sealed interface DetailEvent {
        data class Message(val text: String) : DetailEvent
        data object Deleted : DetailEvent
    }

    companion object {
        const val ARG_MEMORY_ID = "memoryId"
        val TRANSCRIBING_STATES = setOf(TranscribeState.PENDING, TranscribeState.RUNNING)
    }
}
