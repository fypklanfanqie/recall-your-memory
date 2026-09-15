package com.echo.recall.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity)

    /** 置顶优先，其次未完成，最后按创建时间倒序 */
    @Query("SELECT * FROM todos ORDER BY pinned DESC, done ASC, createdAt DESC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    fun observeById(id: String): Flow<TodoEntity?>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun findById(id: String): TodoEntity?

    @Query("UPDATE todos SET done = :done, completedAt = :completedAt WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean, completedAt: Long?)

    @Query("UPDATE todos SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM todos")
    suspend fun count(): Int
}
