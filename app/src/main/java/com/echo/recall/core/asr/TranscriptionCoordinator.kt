package com.echo.recall.core.asr

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
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
 * - 同时只跑一个转写任务（[mutex]），避免多个大模型被并发加载
 * - 逐片段推进度，文字实时写库（UI 自动刷新）
 * - **引擎 LRU 缓存**（v1.1 新增）：v1.0 每次转写都新建 → 立即 release，
 *   228MB 模型反复加载是秒级开销。现在按模型 id 缓存 1 个引擎，
 *   系统内存吃紧（onTrimMemory）时立即释放。
 */
@Singleton
class TranscriptionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val memoryRepository: MemoryRepository,
) : ComponentCallbacks2 {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** memoryId → 进度百分比 */
    val progress: StateFlow<Map<String, Int>> = _progress.asStateFlow()

    val isModelReady: Boolean get() = modelManager.isReady()

    // ---- 引擎缓存（只保留 1 个；模型不同则先释放旧的）----
    private var cachedEngine: AsrEngine? = null
    private var cachedModelId: String? = null

    init {
        runCatching { context.registerComponentCallbacks(this) }
    }

    private fun engineFor(spec: AsrModelSpec): AsrEngine {
        cachedEngine?.let { if (cachedModelId == spec.id) return it }
        cachedEngine?.release()
        cachedEngine = null
        val engine = AsrEngineFactory.create(spec, modelManager.dirFor(spec).absolutePath)
        cachedEngine = engine
        cachedModelId = spec.id
        return engine
    }

    private fun releaseEngine() {
        cachedEngine?.release()
        cachedEngine = null
        cachedModelId = null
    }

    /** 系统内存吃紧：立刻放掉大模型 */
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Log.i(TAG, "onTrimMemory($level) -> release ASR engine")
            releaseEngine()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onLowMemory() {
        Log.i(TAG, "onLowMemory -> release ASR engine")
        releaseEngine()
    }

    /** 切换模型后调用：旧引擎必须释放 */
    fun onModelChanged() {
        releaseEngine()
    }

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
            val spec = modelManager.selectedSpec
            Log.i(TAG, "run start memoryId=$memoryId model=${spec.id} dir=${modelManager.dirFor(spec)}")
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

            try {
                val chunks = resolveChunks(entity, segments, pcmProvider)
                val engine = engineFor(spec)

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
                // 引擎可能已损坏（native 异常后不可再用）→ 丢弃缓存，下次重建
                releaseEngine()
                memoryRepository.setTranscribeState(memoryId, TranscribeState.FAILED)
            } finally {
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
