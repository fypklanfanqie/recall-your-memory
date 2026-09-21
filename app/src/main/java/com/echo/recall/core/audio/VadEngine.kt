package com.echo.recall.core.audio

import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/** 人声灵敏度三档 → silero-vad 参数 */
enum class VadSensitivityPreset(
    val threshold: Float,
    val minSpeechDuration: Float,
    val minSilenceDuration: Float,
) {
    /** 吵一点也才触发 */
    LOW(0.35f, 0.35f, 0.70f),

    /** 默认 */
    MEDIUM(0.50f, 0.25f, 0.50f),

    /** 轻声也算人声，收尾更快 */
    HIGH(0.65f, 0.15f, 0.35f),
}

/** 关闭后的一段人声 */
data class ClosedSegment(
    val startMs: Long,
    val endMs: Long,
    val samples: FloatArray,
) {
    fun toRingSegment(): SpeechRingBuffer.Segment =
        SpeechRingBuffer.Segment(
            startMs = startMs,
            endMs = endMs,
            pcm = PcmUtils.floatsToShorts(samples),
        )
}

/**
 * silero-vad 封装（sherpa-onnx），把连续音频流切成「人声片段」并附上墙钟时间戳。
 *
 * 时间戳策略：以 [reset] 时的 elapsedRealtime 为基准，用 VAD 给出的采样偏移换算；
 * 若偏移映射出现漂移（例如 flush/reset 后计数变化），自动以「现在」为锚点自愈。
 */
class VadEngine(
    assetManager: AssetManager,
    preset: VadSensitivityPreset = VadSensitivityPreset.MEDIUM,
) {
    private val vad: Vad
    private var baseMs: Long = 0L
    private val minSilenceMs: Long = (preset.minSilenceDuration * 1000).toLong()

    init {
        val silero = SileroVadModelConfig(
            model = MODEL_ASSET,
            threshold = preset.threshold,
            minSilenceDuration = preset.minSilenceDuration,
            minSpeechDuration = preset.minSpeechDuration,
            windowSize = AudioSpec.FRAME_SAMPLES,
            maxSpeechDuration = 30.0f,
        )
        val config = VadModelConfig(
            sileroVadModelConfig = silero,
            sampleRate = AudioSpec.SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
            debug = false,
        )
        vad = Vad(assetManager = assetManager, config = config)
        Log.i(TAG, "VAD ready (threshold=${preset.threshold}, window=${AudioSpec.FRAME_SAMPLES})")
    }

    fun reset(nowMs: Long) {
        baseMs = nowMs
        vad.reset()
    }

    /**
     * 告知 VAD「有 [durationMs] 毫秒的音频被永久跳过（从未喂入）」。
     *
     * 极致省电档会丢弃过老的静默帧；若不补偿，VAD 内部的采样计数会永久落后于真实时间，
     * 导致后续片段的时间戳系统性偏早。这里把基准时间前移，保持映射对齐。
     */
    fun skipMs(durationMs: Long) {
        if (durationMs > 0) baseMs += durationMs
    }

    /** 喂入一帧（长度应为 windowSize），返回本次新闭合的片段 */
    fun accept(samples: FloatArray, nowMs: Long): List<ClosedSegment> {
        vad.acceptWaveform(samples)
        return drain(nowMs)
    }

    /** 回溯时调用：把正在说话的那一段也闭合出来 */
    fun flush(nowMs: Long): List<ClosedSegment> {
        vad.flush()
        return drain(nowMs)
    }

    fun isSpeechDetected(): Boolean = vad.isSpeechDetected()

    fun release() {
        runCatching { vad.release() }
    }

    private fun drain(nowMs: Long): List<ClosedSegment> {
        val out = ArrayList<ClosedSegment>(2)
        while (!vad.empty()) {
            val segment = vad.front()
            val durationMs = AudioSpec.samplesToMs(segment.samples.size)
            var startMs = baseMs + AudioSpec.samplesToMs(segment.start)
            var endMs = startMs + durationMs

            // 自愈：正常情况片段结束时间应早于「现在」，否则说明采样偏移映射失效
            if (endMs > nowMs || startMs < baseMs) {
                endMs = nowMs - minSilenceMs
                startMs = endMs - durationMs
                baseMs = endMs - AudioSpec.samplesToMs(segment.start + segment.samples.size)
            }
            if (startMs < 0L) {
                startMs = 0L
                endMs = durationMs
            }
            out.add(ClosedSegment(startMs, endMs, segment.samples))
            vad.pop()
        }
        return out
    }

    companion object {
        private const val TAG = "VadEngine"
        const val MODEL_ASSET = "silero_vad.onnx"
    }
}
