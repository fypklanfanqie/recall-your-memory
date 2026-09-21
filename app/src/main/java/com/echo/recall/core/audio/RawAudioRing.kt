package com.echo.recall.core.audio

/**
 * 原始音频环形缓冲：始终保存「最近 N 毫秒的全部音频」（不分人声/静默）。
 *
 * 两个用途：
 * 1. **句首预滚**（B2）：VAD 报出人声起点时，从这里取出起点之前的音频补上，
 *    避免切掉第一个字。
 * 2. **省电回灌**（A3）：极致省电档在静默期跳过 VAD 推理，一旦能量触发，
 *    把刚刚跳过的这段音频回灌给 VAD —— 所以**不丢音**。
 *
 * 时间戳语义：写入时传入该帧最后一个样本的 elapsedRealtime（[nowMs]），
 * 由此可以把任意时刻换算回环形缓冲内的样本偏移。
 *
 * 纯 Kotlin、无 Android 依赖 → 可用 JVM 单元测试。
 */
class RawAudioRing(val capacitySamples: Int = DEFAULT_CAPACITY_SAMPLES) {

    private val buf = ShortArray(capacitySamples)
    private var writePos = 0
    private var filled = 0

    /** 最新样本的 elapsedRealtime */
    var newestMs: Long = 0L
        private set

    val size: Int get() = filled

    val oldestMs: Long
        get() = newestMs - AudioSpec.samplesToMs(filled)

    fun clear() {
        writePos = 0
        filled = 0
        newestMs = 0L
    }

    /**
     * 写入一帧。[nowMs] 为该帧最后一个样本对应的 elapsedRealtime。
     * 帧长超过容量时只保留尾部。
     */
    fun write(samples: ShortArray, length: Int = samples.size, nowMs: Long) {
        if (length <= 0) return
        // 帧长超过容量时只保留尾部（srcOffset 跳过被丢弃的前段）
        val n = minOf(length, capacitySamples)
        val srcOffset = length - n

        for (i in 0 until n) {
            buf[writePos] = samples[srcOffset + i]
            writePos++
            if (writePos == capacitySamples) writePos = 0
        }
        filled = minOf(capacitySamples, filled + n)
        newestMs = nowMs
    }

    /** 取最近 [durationMs] 毫秒的音频（按时间正序）。不足则返回全部。 */
    fun readLast(durationMs: Long): ShortArray {
        if (filled == 0) return ShortArray(0)
        val want = minOf(AudioSpec.msToSamples(durationMs), filled)
        return readOldestFirst(filled - want, want)
    }

    /**
     * 取 [startMs, endMs) 区间的音频（按时间正序）。
     * 超出可用范围时自动裁剪；完全不在范围内返回空数组。
     */
    fun readRange(startMs: Long, endMs: Long): ShortArray {
        if (filled == 0 || endMs <= startMs) return ShortArray(0)
        // 距最新样本往回多少毫秒 → 往回多少样本
        val backFromEndStart = (newestMs - startMs).coerceAtLeast(0L)
        val backFromEndEnd = (newestMs - endMs).coerceAtLeast(0L)

        val startIdxFromOldest = (filled - AudioSpec.msToSamples(backFromEndStart)).coerceIn(0, filled)
        val endIdxFromOldest = (filled - AudioSpec.msToSamples(backFromEndEnd)).coerceIn(0, filled)
        if (endIdxFromOldest <= startIdxFromOldest) return ShortArray(0)
        return readOldestFirst(startIdxFromOldest, endIdxFromOldest - startIdxFromOldest)
    }

    /** 从「最旧」一侧数起第 [offset] 个样本开始取 [count] 个（时间正序） */
    private fun readOldestFirst(offset: Int, count: Int): ShortArray {
        if (count <= 0 || filled == 0) return ShortArray(0)
        val out = ShortArray(count)
        // 最旧样本在环形缓冲里的位置
        val oldestPos = if (filled == capacitySamples) writePos else 0
        var pos = (oldestPos + offset) % capacitySamples
        for (i in 0 until count) {
            out[i] = buf[pos]
            pos++
            if (pos == capacitySamples) pos = 0
        }
        return out
    }

    companion object {
        /** 默认 1 秒（16kHz × 1s = 16000 样本 = 32KB） */
        const val DEFAULT_CAPACITY_SAMPLES = 16_000

        /** 句首预滚时长 */
        const val PREROLL_MS = 400L
    }
}
