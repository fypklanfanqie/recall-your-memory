package com.echo.recall.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RawAudioRing] 测试。
 *
 * 它承担两个关键职责，错了会直接导致「句首被切」或「省电模式丢音」：
 * 1. 句首预滚：按时间戳取「起点之前」的音频
 * 2. 省电回灌：取最近一段音频补喂给 VAD
 */
class RawAudioRingTest {

    /** 用递增样本值写入，便于断言取出的区间是否正确 */
    private fun ramp(from: Int, count: Int): ShortArray =
        ShortArray(count) { (from + it).toShort() }

    @Test
    fun `starts empty`() {
        val ring = RawAudioRing(capacitySamples = 1000)
        assertEquals(0, ring.size)
        assertEquals(0, ring.readLast(500).size)
        assertEquals(0, ring.readRange(0, 100).size)
    }

    @Test
    fun `readLast returns the newest samples in time order`() {
        val ring = RawAudioRing(capacitySamples = 1000)
        // 写 100 个样本，最后样本时间 = 1000ms
        ring.write(ramp(0, 100), 100, nowMs = 1_000)

        // 要最近 1 秒（远大于已有 100 样本）→ 全部返回
        val last = ring.readLast(1_000)
        assertEquals(100, last.size)
        assertEquals(0.toShort(), last.first())
        assertEquals(99.toShort(), last.last())
    }

    @Test
    fun `readLast honours the requested duration`() {
        val ring = RawAudioRing(capacitySamples = 10_000)
        ring.write(ramp(0, 5_000), 5_000, nowMs = 5_000)

        // 要最近 100ms = 1600 样本
        val last = ring.readLast(100)
        assertEquals(1_600, last.size)
        // 应是最新的 1600 个：索引 3400..4999
        assertEquals(3_400.toShort(), last.first())
        assertEquals(4_999.toShort(), last.last())
    }

    @Test
    fun `wraps around without losing the newest samples`() {
        val ring = RawAudioRing(capacitySamples = 100)
        ring.write(ramp(0, 80), 80, nowMs = 80)
        ring.write(ramp(80, 80), 80, nowMs = 160) // 溢出：只剩最新 100

        assertEquals(100, ring.size)
        val all = ring.readLast(1_000)
        assertEquals(100, all.size)
        assertEquals("最旧应是被保留下来的第 60 号", 60.toShort(), all.first())
        assertEquals(159.toShort(), all.last())
    }

    @Test
    fun `write with a frame larger than capacity keeps the tail`() {
        val ring = RawAudioRing(capacitySamples = 50)
        ring.write(ramp(0, 200), 200, nowMs = 200)

        assertEquals(50, ring.size)
        val all = ring.readLast(1_000)
        assertEquals(150.toShort(), all.first())
        assertEquals(199.toShort(), all.last())
    }

    @Test
    fun `newestMs and oldestMs track the time span`() {
        val ring = RawAudioRing(capacitySamples = 16_000)
        ring.write(ramp(0, 1_600), 1_600, nowMs = 1_000) // 1600 样本 = 100ms

        assertEquals(1_000L, ring.newestMs)
        assertEquals(900L, ring.oldestMs)
    }

    @Test
    fun `readRange returns the window that precedes a given instant`() {
        val ring = RawAudioRing(capacitySamples = 16_000)
        // 1 秒音频，最新样本在 1000ms → 缓冲覆盖 [0ms, 1000ms)
        // 样本 i 对应时刻 i*1000/16000 ms，即 1ms = 16 个样本
        ring.write(ramp(0, 16_000), 16_000, nowMs = 1_000)

        // 取 [400ms, 500ms) → 样本 6400..7999
        val window = ring.readRange(startMs = 400, endMs = 500)
        assertEquals(1_600, window.size)
        assertEquals(6_400.toShort(), window.first())
        assertEquals(7_999.toShort(), window.last())
    }

    @Test
    fun `readRange window ends exactly where the requested interval ends`() {
        val ring = RawAudioRing(capacitySamples = 16_000)
        ring.write(ramp(0, 16_000), 16_000, nowMs = 1_000)

        // 取末尾的 [900ms, 1000ms) → 最后 1600 个样本
        val window = ring.readRange(startMs = 900, endMs = 1_000)
        assertEquals(1_600, window.size)
        assertEquals(14_400.toShort(), window.first())
        assertEquals(15_999.toShort(), window.last())
    }

    @Test
    fun `readRange clamps to what is available`() {
        val ring = RawAudioRing(capacitySamples = 16_000)
        ring.write(ramp(0, 1_600), 1_600, nowMs = 1_000) // 只有 100ms

        // 请求一个远早于缓冲范围的窗口 → 裁剪到可用部分
        val window = ring.readRange(startMs = -5_000, endMs = 1_000)
        assertEquals(1_600, window.size)
        assertEquals(0.toShort(), window.first())
    }

    @Test
    fun `readRange returns empty for an inverted or empty window`() {
        val ring = RawAudioRing(capacitySamples = 1_000)
        ring.write(ramp(0, 100), 100, nowMs = 100)
        assertEquals(0, ring.readRange(startMs = 500, endMs = 400).size)
        assertEquals(0, ring.readRange(startMs = 400, endMs = 400).size)
    }

    @Test
    fun `readRange of a window entirely after the newest sample is empty`() {
        val ring = RawAudioRing(capacitySamples = 1_000)
        ring.write(ramp(0, 100), 100, nowMs = 100)
        // 请求未来的时间窗（缓冲里还没有）
        assertEquals(0, ring.readRange(startMs = 5_000, endMs = 6_000).size)
    }

    @Test
    fun `clear resets everything`() {
        val ring = RawAudioRing(capacitySamples = 1_000)
        ring.write(ramp(0, 100), 100, nowMs = 100)
        ring.clear()

        assertEquals(0, ring.size)
        assertEquals(0L, ring.newestMs)
        assertEquals(0, ring.readLast(100).size)
    }

    @Test
    fun `zero length write is ignored`() {
        val ring = RawAudioRing(capacitySamples = 1_000)
        ring.write(ShortArray(10), 0, nowMs = 50)
        assertEquals(0, ring.size)
        assertEquals(0L, ring.newestMs)
    }

    @Test
    fun `preroll duration is available for a 400ms window`() {
        // 预滚常量应能被默认容量覆盖（否则句首补不齐）
        val ring = RawAudioRing()
        val need = AudioSpec.msToSamples(RawAudioRing.PREROLL_MS)
        assertTrue(
            "默认容量 ${ring.capacitySamples} 必须 >= 预滚所需 $need",
            ring.capacitySamples >= need,
        )
    }

    @Test
    fun `consecutive writes form a contiguous time line`() {
        val ring = RawAudioRing(capacitySamples = 16_000)
        // 模拟连续帧：每帧 512 样本 = 32ms
        var t = 0L
        repeat(10) {
            t += 32
            ring.write(ramp(0, AudioSpec.FRAME_SAMPLES), AudioSpec.FRAME_SAMPLES, nowMs = t)
        }
        assertEquals(320L, ring.newestMs)
        assertEquals(10 * AudioSpec.FRAME_SAMPLES, ring.size)
        // 最新一帧的最后一个样本应能被取回
        val tail = ring.readLast(32)
        assertArrayEquals(ramp(0, AudioSpec.FRAME_SAMPLES), tail)
    }
}
