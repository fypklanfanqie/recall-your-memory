package com.echo.recall.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    /** 置顶优先，其次最近更新 */
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: String): NoteEntity?

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 标题或正文模糊匹配。
     * [pattern] 需由调用方拼好通配符（见 `NoteRepository.searchPattern`），
     * 内部用 `\` 做 ESCAPE，避免用户输入的 % / _ 变成通配符。
     */
    @Query(
        "SELECT * FROM notes WHERE title LIKE :pattern ESCAPE '\\' OR content LIKE :pattern ESCAPE '\\' " +
            "ORDER BY pinned DESC, updatedAt DESC",
    )
    fun search(pattern: String): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int
}
