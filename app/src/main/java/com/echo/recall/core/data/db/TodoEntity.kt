package com.echo.recall.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 待办条目。
 *
 * 排序语义（见 [TodoDao.observeAll]）：置顶 → 未完成 → 最新创建。
 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String? = null,
    val done: Boolean = false,
    val pinned: Boolean = false,
    val createdAt: Long,
    val completedAt: Long? = null,
    /** 可选截止时间（epoch ms） */
    val dueAt: Long? = null,
)
