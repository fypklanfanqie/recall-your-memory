package com.echo.recall.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE favorited = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    fun observeById(id: String): Flow<MemoryEntity?>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun findById(id: String): MemoryEntity?

    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MemoryEntity>

    @Query("UPDATE memories SET favorited = :favorited WHERE id = :id")
    suspend fun setFavorited(id: String, favorited: Boolean)

    @Query("UPDATE memories SET summary = :summary, modelTag = :modelTag WHERE id = :id")
    suspend fun setSummary(id: String, summary: String?, modelTag: String?)

    @Query("UPDATE memories SET transcript = :transcript, segmentsJson = :segmentsJson, language = :lang, emotion = :emotion, transcribeState = :state WHERE id = :id")
    suspend fun setTranscription(
        id: String,
        transcript: String?,
        segmentsJson: String?,
        lang: String?,
        emotion: String?,
        state: Int,
    )

    @Query("UPDATE memories SET transcribeState = :state WHERE id = :id")
    suspend fun setTranscribeState(id: String, state: Int)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 7 天清理：只清未收藏的 */
    @Query("SELECT * FROM memories WHERE favorited = 0 AND createdAt < :cutoff")
    suspend fun expired(cutoff: Long): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int
}
