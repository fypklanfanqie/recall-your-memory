package com.echo.recall.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 备忘录条目。
 *
 * [title] 由正文首行派生（见 `NoteTextUtils.titleOf`），
 * 之所以冗余存一份，是为了让列表和搜索不必每次都解析正文。
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val content: String,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
