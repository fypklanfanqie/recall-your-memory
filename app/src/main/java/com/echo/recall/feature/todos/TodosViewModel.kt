package com.echo.recall.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.data.TodoRepository
import com.echo.recall.core.data.db.TodoEntity
import com.echo.recall.core.util.TodoGroupCounts
import com.echo.recall.core.util.TodoGrouping
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class TodosViewModel @Inject constructor(
    private val repository: TodoRepository,
) : ViewModel() {

    private val editingId = MutableStateFlow<String?>(null)

    val todos: StateFlow<List<TodoEntity>> = repository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** 置顶（不论完成与否） */
    val pinned: StateFlow<List<TodoEntity>> = todos
        .map { list -> list.filter { it.pinned } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 未完成（不含置顶） */
    val active: StateFlow<List<TodoEntity>> = todos
        .map { list -> list.filter { !it.pinned && !it.done } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已完成（不含置顶） */
    val completed: StateFlow<List<TodoEntity>> = todos
        .map { list -> list.filter { !it.pinned && it.done } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val counts: StateFlow<TodoGroupCounts> = todos
        .map { TodoGrouping.counts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoGroupCounts(0, 0, 0))

    /** 编辑器里正在查看的条目（null = 未打开编辑器） */
    val editing: StateFlow<TodoEntity?> = combine(todos, editingId) { list, id ->
        id?.let { target -> list.firstOrNull { it.id == target } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun add(title: String, notes: String? = null, dueAt: Long? = null) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            _messages.tryEmit("先写点什么吧")
            return
        }
        viewModelScope.launch {
            repository.create(trimmed, notes, dueAt)
            _messages.tryEmit("已添加待办")
        }
    }

    fun toggleDone(todo: TodoEntity) {
        viewModelScope.launch {
            repository.setDone(todo.id, !todo.done)
            _messages.tryEmit(if (todo.done) "已恢复为未完成" else "已完成 🎉")
        }
    }

    fun togglePinned(todo: TodoEntity) {
        viewModelScope.launch {
            repository.setPinned(todo.id, !todo.pinned)
            _messages.tryEmit(if (todo.pinned) "已取消置顶" else "已置顶")
        }
    }

    fun update(todo: TodoEntity) {
        val trimmed = todo.copy(title = todo.title.trim())
        if (trimmed.title.isEmpty()) {
            _messages.tryEmit("标题不能为空")
            return
        }
        viewModelScope.launch {
            repository.upsert(trimmed)
            _messages.tryEmit("已保存")
        }
    }

    fun delete(todo: TodoEntity) {
        viewModelScope.launch {
            repository.delete(todo.id)
            closeEditor()
            _messages.tryEmit("已删除待办")
        }
    }

    fun openEditor(id: String) {
        editingId.value = id
    }

    fun closeEditor() {
        editingId.value = null
    }
}
