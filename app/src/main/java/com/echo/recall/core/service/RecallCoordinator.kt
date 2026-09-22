package com.echo.recall.core.service

import com.echo.recall.core.asr.TranscriptionCoordinator
import com.echo.recall.core.audio.RecorderEngine
import com.echo.recall.core.data.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一次「回溯」的完整流程：取缓冲 → 落库 → **启动本地转写**。
 *
 * ## 为什么要抽出来（v1.1 修的真实 bug）
 *
 * v1.0 里这条流程有**两份独立实现**：
 * - `RecorderService.performRecall()` —— 含转写 ✅（但只有通知栏「回溯」按钮能走到）
 * - `MemoryViewModel.recall()` —— **漏了转写** ❌（首页那个最大的「回溯记忆」按钮走这里）
 *
 * 后果：用户点首页大按钮生成的记忆，`transcribeState` 永远是 PENDING，
 * UI 上一直显示「转写中」，**本地转写核心功能等于没跑**。
 * 真机验证时发现（点回溯后无任何 `Transcription` 日志、记忆卡在转写中）。
 *
 * 两份实现必然漂移，所以收敛到这一处：服务与 ViewModel 都只调 [recall]。
 */
@Singleton
class RecallCoordinator @Inject constructor(
    private val engine: RecorderEngine,
    private val memoryRepository: MemoryRepository,
    private val transcription: TranscriptionCoordinator,
) {

    sealed interface Outcome {
        /** 窗口内没有人声，什么都没生成 */
        data object NoSpeech : Outcome

        /** 落库失败（例如音频编码失败） */
        data object Failed : Outcome

        /**
         * 已生成记忆。
         * @param transcribing 是否已启动本地转写（模型未就绪时为 false）
         */
        data class Created(
            val memoryId: String,
            val segmentCount: Int,
            val transcribing: Boolean,
        ) : Outcome
    }

    /**
     * 取走缓冲并生成记忆；模型就绪时立即启动转写。
     *
     * 注意：本方法可在主线程调用方（viewModelScope 默认 Main）安全使用 ——
     * 取缓冲与编码都切到了后台调度器。
     */
    suspend fun recall(): Outcome {
        // engine.recall() 会做 PCM 拼接/裁剪，不能跑在主线程
        val segments = withContext(Dispatchers.Default) { engine.recall() }
        if (segments.isEmpty()) return Outcome.NoSpeech

        val ordered = segments.sortedBy { it.startMs }
        val memory = memoryRepository.createFromRecall(ordered, engine.epochOffsetMs())
            ?: return Outcome.Failed

        // 模型已就绪则立刻本地转写：PCM 还在内存里，无需从 .m4a 解码
        val transcribing = transcription.isModelReady
        if (transcribing) {
            transcription.transcribeSegments(memory.id, ordered.map { it.pcm })
        }
        return Outcome.Created(
            memoryId = memory.id,
            segmentCount = ordered.size,
            transcribing = transcribing,
        )
    }
}
