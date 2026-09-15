package com.echo.recall.feature.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.data.MemoryRepository
import com.echo.recall.core.data.db.MemoryEntity
import com.echo.recall.core.llm.ChatMessage
import com.echo.recall.core.llm.LlmDelta
import com.echo.recall.core.llm.LlmService
import com.echo.recall.core.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiMessage(
    val role: String,
    val content: String,
    val streaming: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
class MemoryChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: MemoryRepository,
    private val llmService: LlmService,
) : ViewModel() {

    private val memoryId: String = checkNotNull(savedStateHandle[MemoryDetailViewModel.ARG_MEMORY_ID])

    val memory: StateFlow<MemoryEntity?> = repository.observeById(memoryId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val messages: StateFlow<List<ChatUiMessage>> = _messages.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    val configured: Boolean get() = llmService.usableConfig() != null

    fun suggestions(): List<String> = listOf(
        "这段内容的核心是什么？",
        "提到了哪些时间或安排？",
        "有没有需要我做的事？",
        "帮我挑出可能听错的地方",
    )

    fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _sending.value) return
        val entity = memory.value ?: return
        val transcript = entity.transcript
        if (transcript.isNullOrBlank()) {
            _messages.update {
                it + ChatUiMessage(role = "assistant", content = "这条记忆还没有转写文字，请先转写。", failed = true)
            }
            return
        }

        val history = _messages.value
            .filterNot { it.failed }
            .map { ChatMessage(role = it.role, content = it.content) }

        _messages.update {
            it + ChatUiMessage(role = "user", content = text) +
                ChatUiMessage(role = "assistant", content = "", streaming = true)
        }
        _sending.value = true

        viewModelScope.launch {
            val builder = StringBuilder()
            try {
                llmService.streamAskMemory(
                    transcript = transcript,
                    timeRange = TimeFormat.range(entity.wallStartAt, entity.wallEndAt),
                    history = history,
                    question = text,
                ).collect { delta ->
                    when (delta) {
                        is LlmDelta.Text -> {
                            builder.append(delta.value)
                            updateLastAssistant(builder.toString(), streaming = true, failed = false)
                        }

                        is LlmDelta.Failure -> {
                            updateLastAssistant(delta.message, streaming = false, failed = true)
                        }

                        LlmDelta.Done -> {
                            updateLastAssistant(builder.toString(), streaming = false, failed = false)
                        }
                    }
                }
            } finally {
                _sending.value = false
                updateLastAssistant(builder.toString(), streaming = false, failed = false)
            }
        }
    }

    private fun updateLastAssistant(content: String, streaming: Boolean, failed: Boolean) {
        _messages.update { list ->
            if (list.isEmpty()) return@update list
            val last = list.last()
            if (last.role != "assistant") return@update list
            list.dropLast(1) + last.copy(content = content, streaming = streaming, failed = failed)
        }
    }
}
