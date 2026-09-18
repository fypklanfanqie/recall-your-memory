package com.echo.recall.core.asr

import android.content.Context
import android.util.Log
import com.echo.recall.core.audio.AudioDecoder
import com.echo.recall.core.audio.PcmUtils
import com.echo.recall.core.data.MemoryRepository
import com.echo.recall.core.data.db.MemoryEntity
import com.echo.recall.core.data.db.MemorySegment
import com.echo.recall.core.data.db.TranscribeState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 转写协调器：把「回溯后内存中的 PCM」或「已保存的音频」转成文字。
 *
 * - 同时只跑一个转写任务（[mutex]），避免 228MB 模型被并发加载
 * - 逐片段推进度，文字实时写库（UI 自动刷新）
 * - 转写完立即释放模型，避免常驻内存（后台服务要长时间活着）
 */
@Singleton
class TranscriptionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val memoryRepository: MemoryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** memoryId → 进度百分比 */
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    val isModelReady: Boolean get() = modelManager.isReady()

    /** 回溯后立刻转写：PCM 还在内存里，无需解码 */
    fun transcribeSegments(memoryId: String, orderedPcm: List<ShortArray>) {
        scope.launch {
            run(memoryId) { orderedPcm }
        }
    }

    /** 补转写 / 重新转写：从已保存的 .m4a 解码后按片段偏移切片 */
    fun transcribeMemory(memoryId: String) {
        scope.launch {
            run(memoryId) { null }
        }
    }

    /** 补转写所有待处理记忆（模型刚下载完时用） */
    fun transcribePending(limit: Int = 10) {
        scope.launch {
            val pending = memoryRepository.recent(limit).filter {
                it.transcribeState == TranscribeState.PENDING || it.transcribeState == TranscribeState.FAILED
            }
            for (entity in pending) {
                run(entity.id) { null }
            }
        }
    }

    private suspend fun run(memoryId: String, pcmProvider: suspend () -> List<ShortArray>?) {
        mutex.withLock {
            Log.i(TAG, "run start memoryId=$memoryId modelDir=${modelManager.modelDir}")
            if (!modelManager.isReady()) {
                Log.w(TAG, "model not ready -> keep PENDING for $memoryId")
                memoryRepository.setTranscribeState(memoryId, TranscribeState.PENDING)
                return
            }
            val entity = memoryRepository.findById(memoryId) ?: return
            val segments = memoryRepository.readSegments(entity)
            if (segments.isEmpty()) {
                memoryRepository.setTranscribeState(memoryId, TranscribeState.DONE)
                return
            }

            memoryRepository.setTranscribeState(memoryId, TranscribeState.RUNNING)
            _progress.update { it + (memoryId to 0) }

            var engine: SenseVoiceEngine? = null
            try {
                val chunks = resolveChunks(entity, segments, pcmProvider)
                engine = SenseVoiceEngine(modelManager.modelDir.absolutePath)

                var language: String? = null
                var emotion: String? = null
                val updated = ArrayList<MemorySegment>(segments.size)

                segments.forEachIndexed { index, segment ->
                    val chunk = chunks?.getOrNull(index)
                    val text = if (chunk != null && chunk.isNotEmpty()) {
                        val result = engine.transcribe(PcmUtils.shortsToFloats(chunk))
                        if (language == null) language = cleanTag(result.language)
                        if (emotion == null) emotion = cleanTag(result.emotion)
                        result.text
                    } else {
                        segment.text
                    }
                    updated += segment.copy(text = text?.takeIf { it.isNotBlank() })
                    _progress.update { it + (memoryId to ((index + 1) * 100 / segments.size)) }
                    // 逐段流式落库：UI 立即看到已转好的片段，不等整篇完成
                    memoryRepository.setTranscription(
                        id = memoryId,
                        transcript = updated.mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }
                            .joinToString("\n"),
                        segments = updated.toList(),
                        language = language,
                        emotion = emotion,
                        state = TranscribeState.RUNNING,
                    )
                }

                val transcript = updated.mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }
                    .joinToString("\n")
                memoryRepository.setTranscription(
                    id = memoryId,
                    transcript = transcript.ifBlank { null },
                    segments = updated,
                    language = language,
                    emotion = emotion,
                    state = TranscribeState.DONE,
                )
                Log.i(TAG, "transcribed $memoryId: ${updated.count { !it.text.isNullOrBlank() }}/${segments.size} segments")
            } catch (t: Throwable) {
                Log.e(TAG, "transcription failed for $memoryId", t)
                memoryRepository.setTranscribeState(memoryId, TranscribeState.FAILED)
            } finally {
                engine?.release()
                _progress.update { it - memoryId }
            }
        }
    }

    private suspend fun resolveChunks(
        entity: MemoryEntity,
        segments: List<MemorySegment>,
        pcmProvider: suspend () -> List<ShortArray>?,
    ): List<ShortArray>? {
        pcmProvider()?.let { return it }
        val path = entity.audioPath ?: return null
        val decoded = AudioDecoder.decodeToPcm16(File(path)) ?: return null
        return segments.map { AudioDecoder.slice(decoded, it.audioStartMs, it.audioEndMs) }
    }

    /** SenseVoice 输出形如 <|zh|> / <|NEUTRAL|>，去掉包裹标记 */
    private fun cleanTag(raw: String?): String? =
        raw?.removePrefix("<|")?.removeSuffix("|>")?.trim()?.takeIf { it.isNotBlank() }

    companion object {
        private const val TAG = "Transcription"
    }
}
