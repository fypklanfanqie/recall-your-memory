package com.echo.recall.core.data

import android.util.Log
import com.echo.recall.core.audio.AacEncoder
import com.echo.recall.core.audio.AudioSpec
import com.echo.recall.core.audio.PcmUtils
import com.echo.recall.core.audio.SpeechRingBuffer
import com.echo.recall.core.data.db.MemoryDao
import com.echo.recall.core.data.db.MemoryEntity
import com.echo.recall.core.data.db.MemorySegment
import com.echo.recall.core.data.db.TranscribeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryDao,
    private val audioStore: AudioStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeAll(): Flow<List<MemoryEntity>> = dao.observeAll()

    fun observeFavorites(): Flow<List<MemoryEntity>> = dao.observeFavorites()

    fun observeById(id: String): Flow<MemoryEntity?> = dao.observeById(id)

    suspend fun findById(id: String): MemoryEntity? = dao.findById(id)

    suspend fun recent(limit: Int = 20): List<MemoryEntity> = dao.recent(limit)

    /**
     * 把一次回溯取出的片段落成记忆条目：拼接 → 编码 AAC → 写库。
     * @param segments 已按时间排序的人声片段
     * @param epochOffsetMs System.currentTimeMillis() - SystemClock.elapsedRealtime()
     */
    suspend fun createFromRecall(
        segments: List<SpeechRingBuffer.Segment>,
        epochOffsetMs: Long,
    ): MemoryEntity? = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext null

        val id = UUID.randomUUID().toString()
        val ordered = segments.sortedBy { it.startMs }

        // 音频内偏移：片段之间静默被丢弃，用累计样本数换算
        var cursorSamples = 0
        val memorySegments = ordered.map { seg ->
            val audioStart = AudioSpec.samplesToMs(cursorSamples)
            cursorSamples += seg.pcm.size
            MemorySegment(
                audioStartMs = audioStart,
                audioEndMs = AudioSpec.samplesToMs(cursorSamples),
                wallStartAt = seg.startMs + epochOffsetMs,
                wallEndAt = seg.endMs + epochOffsetMs,
            )
        }
        val totalDurationMs = AudioSpec.samplesToMs(cursorSamples)
        val voicedMs = ordered.sumOf { it.durationMs }

        val combined = PcmUtils.concat(ordered.map { it.pcm })
        val file = audioStore.audioFile(id)
        val encoded = AacEncoder.encodeToM4a(combined, file)
        if (!encoded) Log.w(TAG, "audio encode failed for $id, keeping transcript-less entry")

        val entity = MemoryEntity(
            id = id,
            createdAt = System.currentTimeMillis(),
            wallStartAt = memorySegments.first().wallStartAt,
            wallEndAt = memorySegments.last().wallEndAt,
            voicedMillis = voicedMs,
            audioPath = if (encoded) file.absolutePath else null,
            audioDurationMs = totalDurationMs,
            segmentsJson = json.encodeToString(memorySegments),
            transcribeState = TranscribeState.PENDING,
        )
        dao.upsert(entity)
        Log.i(TAG, "memory $id created: ${ordered.size} segments, ${voicedMs}ms voiced, encoded=$encoded")
        entity
    }

    suspend fun setFavorited(id: String, favorited: Boolean) = dao.setFavorited(id, favorited)

    suspend fun setSummary(id: String, summary: String?, modelTag: String?) =
        dao.setSummary(id, summary, modelTag)

    suspend fun setTranscription(
        id: String,
        transcript: String?,
        segments: List<MemorySegment>?,
        language: String?,
        emotion: String?,
        state: Int,
    ) = dao.setTranscription(
        id = id,
        transcript = transcript,
        segmentsJson = segments?.let { json.encodeToString(it) },
        lang = language,
        emotion = emotion,
        state = state,
    )

    suspend fun setTranscribeState(id: String, state: Int) = dao.setTranscribeState(id, state)

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        audioStore.delete(id)
        dao.deleteById(id)
    }

    suspend fun readSegments(entity: MemoryEntity): List<MemorySegment> {
        val raw = entity.segmentsJson ?: return emptyList()
        return runCatching { json.decodeFromString<List<MemorySegment>>(raw) }.getOrDefault(emptyList())
    }

    /** 7 天保留策略：未收藏且超期的条目连音频一起删 */
    suspend fun cleanupExpired(retentionDays: Int = DEFAULT_RETENTION_DAYS): Int =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
            val expired = dao.expired(cutoff)
            expired.forEach { entity ->
                audioStore.delete(entity.id)
                dao.deleteById(entity.id)
            }
            if (expired.isNotEmpty()) Log.i(TAG, "retention: removed ${expired.size} expired memories")
            expired.size
        }

    suspend fun count(): Int = dao.count()

    companion object {
        private const val TAG = "MemoryRepository"
        const val DEFAULT_RETENTION_DAYS = 7
    }
}
