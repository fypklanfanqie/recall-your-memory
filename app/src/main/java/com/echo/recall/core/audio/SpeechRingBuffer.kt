package com.echo.recall.core.audio

/**
 * 内存环形缓冲：只保留最近 [windowMs] 内被 silero-vad 判定为人声的片段。
 *
 * 设计要点（对应「取走即消失」语义）：
 * - 只在有人声时写入（静默不占空间）
 * - 窗口滚动时按**整段**淘汰，不切开句子（宁可多留半句，不做半句裁剪）
 * - [snapshotAndClear] 是回溯语义：原子地取走全部内容并清空
 * - 纯 Kotlin、无 Android 依赖 → 可用 JVM 单元测试
 */
class SpeechRingBuffer(
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    data class Segment(
        val startMs: Long,
        val endMs: Long,
        val pcm: ShortArray,
    ) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
        val bytes: Int get() = pcm.size * AudioSpec.BYTES_PER_SAMPLE
    }

    private val segments = ArrayDeque<Segment>()
    private var cachedBytes = 0

    val size: Int
        @Synchronized get() = segments.size

    val bytes: Int
        @Synchronized get() = cachedBytes

    @Synchronized
    fun add(segment: Segment) {
        if (segment.pcm.isEmpty()) return
        segments.addLast(segment)
        cachedBytes += segment.bytes
        enforceByteCap()
    }

    /** 窗口滚动：丢掉结束时间早于 nowMs - windowMs 的整段 */
    @Synchronized
    fun trim(nowMs: Long, windowMs: Long) {
        val cutoff = nowMs - windowMs
        while (segments.isNotEmpty() && segments.first().endMs < cutoff) {
            cachedBytes -= segments.removeFirst().bytes
        }
    }

    @Synchronized
    fun snapshot(): List<Segment> = segments.toList()

    /** 回溯：取走并清空 */
    @Synchronized
    fun snapshotAndClear(): List<Segment> {
        val copy = segments.toList()
        segments.clear()
        cachedBytes = 0
        return copy
    }

    @Synchronized
    fun clear() {
        segments.clear()
        cachedBytes = 0
    }

    /** 窗口内累计人声时长 */
    @Synchronized
    fun voicedMillis(): Long {
        var total = 0L
        for (s in segments) total += s.durationMs
        return total
    }

    @Synchronized
    fun lastVoiceEndMs(): Long? = segments.lastOrNull()?.endMs

    @Synchronized
    fun firstVoiceStartMs(): Long? = segments.firstOrNull()?.startMs

    private fun enforceByteCap() {
        // 防御异常长语音：超出上限时淘汰最旧的段（至少保留一段）
        while (cachedBytes > maxBytes && segments.size > 1) {
            cachedBytes -= segments.removeFirst().bytes
        }
    }

    companion object {
        /** ≈ 6 分钟连续人声（32KB/s） */
        const val DEFAULT_MAX_BYTES = 12 * 1024 * 1024
    }
}
