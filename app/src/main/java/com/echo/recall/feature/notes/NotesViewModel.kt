package com.echo.recall.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.data.NoteRepository
import com.echo.recall.core.data.db.NoteEntity
import com.echo.recall.core.llm.LlmService
import com.echo.recall.core.util.NoteTextUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AI 面板状态：总结 / 润色共用 */
sealed interface AiUi {
    data object Idle : AiUi

    data object Loading : AiUi

    /** [action] 为 summarize 或 polish */
    data class Result(val action: AiAction, val text: String) : AiUi

    data class Error(val message: String) : AiUi

    /** 润色：原文与润色稿并排展示，方便对比 */
    data class Polished(val original: String, val polished: String) : AiUi
}

enum class AiAction(val label: String) {
    SUMMARIZE("AI 总结"),
    POLISH("一键润色"),
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val llmService: LlmService,
) : ViewModel() {

    /** 编辑器里正在编辑的备忘 id（null = 列表页） */
    private val editingId = MutableStateFlow<String?>(null)

    /** 编辑中的本地草稿：打字时立即更新，落盘走防抖 */
    private val draft = MutableStateFlow<Draft?>(null)

    private val query = MutableStateFlow("")

    private var saveJob: Job? = null

    private data class Draft(val id: String, val content: String)

    val notes: StateFlow<List<NoteEntity>> = repository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val searchQuery: StateFlow<String> = query

    /** 列表展示用：关键词过滤（与 DAO 的 LIKE 语义一致） */
    val visibleNotes: StateFlow<List<NoteEntity>> = combine(notes, query) { list, q ->
        if (q.isBlank()) list else list.filter { NoteTextUtils.matches(q, it.title, it.content) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pinned: StateFlow<List<NoteEntity>> = visibleNotes
        .map { list -> list.filter { it.pinned } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val others: StateFlow<List<NoteEntity>> = visibleNotes
        .map { list -> list.filterNot { it.pinned } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val llmConfigured: Boolean get() = llmService.usableConfig() != null

    /** 编辑器当前正文（草稿优先，保证打字不卡） */
    val editorContent: StateFlow<String> = draft
        .map { it?.content.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** 编辑器对应的实体（用于显示创建/更新时间） */
    val editing: StateFlow<NoteEntity?> = combine(notes, editingId) { list, id ->
        id?.let { target -> list.firstOrNull { it.id == target } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _aiState = MutableStateFlow<AiUi>(AiUi.Idle)
    val aiState: StateFlow<AiUi> = _aiState

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setQuery(value: String) {
        query.value = value
    }

    /** 新建：立刻落库一条空备忘，避免用户退出时丢东西 */
    fun create() {
        viewModelScope.launch {
            val id = repository.create()
            openEditor(id)
        }
    }

    fun openEditor(id: String) {
        _aiState.value = AiUi.Idle
        editingId.value = id
        viewModelScope.launch {
            val note = repository.findById(id)
            if (note == null) {
                closeEditor()
                return@launch
            }
            draft.value = Draft(note.id, note.content)
        }
    }

    /** 关闭编辑器：先把未落盘的改动刷下去 */
    fun closeEditor() {
        flush()
        editingId.value = null
        draft.value = null
        _aiState.value = AiUi.Idle
    }

    /** 打字：立即更新草稿，800ms 无输入后落盘 */
    fun onContentChange(content: String) {
        val id = editingId.value ?: return
        draft.value = Draft(id, content)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist(id, content)
        }
    }

    /** 立即落盘（返回键 / 关闭编辑器时调用） */
    fun flush() {
        saveJob?.cancel()
        saveJob = null
        val current = draft.value ?: return
        // 显式传参：避免协程真正执行时 draft/editingId 已被置空而丢字
        viewModelScope.launch { persist(current.id, current.content) }
    }

    /** 保存指定内容（标题始终由正文首行派生） */
    fun save(content: String) {
        val id = editingId.value ?: return
        draft.value = Draft(id, content)
        flush()
    }

    private suspend fun persist(id: String, content: String) {
        val existing = repository.findById(id) ?: return
        repository.save(
            existing.copy(
                content = content,
                title = NoteTextUtils.titleOf(content),
            ),
        )
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch {
            repository.setPinned(note.id, !note.pinned)
            _messages.tryEmit(if (note.pinned) "已取消置顶" else "已置顶")
        }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch {
            flush()
            repository.delete(note.id)
            if (editingId.value == note.id) {
                editingId.value = null
                draft.value = null
                _aiState.value = AiUi.Idle
            }
            _messages.tryEmit("已删除备忘")
        }
    }

    // ---- AI ----

    fun summarize(content: String) {
        val text = content.trim()
        if (!ensureConfigured()) return
        if (text.isEmpty()) {
            _aiState.value = AiUi.Error("备忘还是空的，先写点什么")
            return
        }
        _aiState.value = AiUi.Loading
        viewModelScope.launch {
            _aiState.value = llmService.summarizeNote(text).fold(
                onSuccess = { AiUi.Result(AiAction.SUMMARIZE, it.trim()) },
                onFailure = { AiUi.Error(it.message ?: "总结失败") },
            )
        }
    }

    fun polish(content: String) {
        val text = content.trim()
        if (!ensureConfigured()) return
        if (text.isEmpty()) {
            _aiState.value = AiUi.Error("备忘还是空的，先写点什么")
            return
        }
        _aiState.value = AiUi.Loading
        viewModelScope.launch {
            _aiState.value = llmService.polishNote(text).fold(
                onSuccess = { AiUi.Polished(original = text, polished = it.trim()) },
                onFailure = { AiUi.Error(it.message ?: "润色失败") },
            )
        }
    }

    /** 放弃 AI 结果 */
    fun dismissAi() {
        _aiState.value = AiUi.Idle
    }

    /** 把 AI 输出写回正文（应用后立即落盘） */
    fun applyAi(text: String, append: Boolean = false) {
        val id = editingId.value
        if (id == null) {
            _messages.tryEmit("先打开一条备忘")
            return
        }
        val base = draft.value?.content.orEmpty()
        val next = if (append && base.isNotBlank()) "$base\n\n$text" else text
        draft.value = Draft(id, next)
        _aiState.value = AiUi.Idle
        flush()
        _messages.tryEmit(if (append) "已追加到正文" else "已应用到正文")
    }

    private fun ensureConfigured(): Boolean {
        val message = llmService.notConfiguredMessage()
        if (llmService.usableConfig() == null) {
            _aiState.value = AiUi.Error(message)
            _messages.tryEmit(message)
            return false
        }
        return true
    }

    companion object {
        /** 打字停止多久后自动保存 */
        const val SAVE_DEBOUNCE_MS = 800L
    }
}
