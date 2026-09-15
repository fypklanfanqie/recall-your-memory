package com.echo.recall.core.util

import com.echo.recall.core.data.db.TodoEntity

/**
 * 待办列表的分组结果（纯逻辑，不依赖 Compose / Android，可直接单测）。
 *
 * 分组优先级与 DAO 的排序保持一致：置顶 → 未完成 → 已完成。
 * 传入前若已按 `pinned DESC, done ASC, createdAt DESC` 排序，组内顺序会被原样保留。
 */
enum class TodoGroupKind {
    /** 置顶（不论完成与否，都排在最上面） */
    PINNED,

    /** 未完成 */
    ACTIVE,

    /** 已完成（可折叠） */
    COMPLETED,
}

data class TodoGroupSection(
    val kind: TodoGroupKind,
    val title: String,
    val todos: List<TodoEntity>,
)

/** 各分组条目数，便于在页头/折叠行显示。 */
data class TodoGroupCounts(
    val pinned: Int,
    val active: Int,
    val completed: Int,
) {
    val total: Int get() = pinned + active + completed
}

object TodoGrouping {

    const val TITLE_PINNED = "置顶"
    const val TITLE_ACTIVE = "未完成"
    const val TITLE_COMPLETED = "已完成"

    /** 先把列表拆成 置顶 / 未完成 / 已完成 三桶（空桶会被丢掉）。 */
    fun group(todos: List<TodoEntity>): List<TodoGroupSection> {
        val pinned = todos.filter { it.pinned }
        val completed = todos.filter { !it.pinned && it.done }
        val active = todos.filter { !it.pinned && !it.done }
        return listOfNotNull(
            pinned.takeIf { it.isNotEmpty() }?.let { TodoGroupSection(TodoGroupKind.PINNED, TITLE_PINNED, it) },
            active.takeIf { it.isNotEmpty() }?.let { TodoGroupSection(TodoGroupKind.ACTIVE, TITLE_ACTIVE, it) },
            completed.takeIf { it.isNotEmpty() }?.let { TodoGroupSection(TodoGroupKind.COMPLETED, TITLE_COMPLETED, it) },
        )
    }

    /** 未置顶条目再按 未完成 / 已完成 划分，用于各自独立的列表区域。 */
    fun split(todos: List<TodoEntity>): Pair<List<TodoEntity>, List<TodoEntity>> =
        todos.filter { !it.pinned && !it.done } to todos.filter { !it.pinned && it.done }

    fun counts(todos: List<TodoEntity>): TodoGroupCounts = TodoGroupCounts(
        pinned = todos.count { it.pinned },
        active = todos.count { !it.pinned && !it.done },
        completed = todos.count { !it.pinned && it.done },
    )

    /** 折叠标题：「已完成 (3)」 */
    fun completedLabel(count: Int): String = "$TITLE_COMPLETED ($count)"
}
