package com.echo.recall.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * [SpeechGate] 的两级触发门测试。
 *
 * 重点验证「不伤召回」：真实人声形状的输入必须被放行，只有明确的
 * 稳态纯音 / 宽带噪声才被拒绝。
 */
class SpeechGateTest {

    // ---------------------------------------------------------------- 工具

    /** 生成正弦波（持续纯音 → 低过零率，像乐音） */
    private fun tone(freqHz: Double, samples: Int, amp: Double = 0.3): ShortArray {
        val out = ShortArray(samples)
        for (i in 0 until samples) {
            out[i] = (sin(2 * PI * freqHz * i / AudioSpec.SAMPLE_RATE) * amp * 32767).toInt().toShort()
        }
        return out
    }

    /** 生成伪随机噪声（宽带 → 高过零率） */
    private fun noise(samples: Int, amp: Double = 0.3, seed: Long = 42L): ShortArray {
        val rnd = java.util.Random(seed)
        val out = ShortArray(samples)
        for (i in 0 until samples) {
            out[i] = ((rnd.nextDouble() * 2 - 1) * amp * 32767).toInt().toShort()
        }
        return out
    }

    /**
     * 生成「像说话」的信号：能量随音节起伏 + 中等过零率。
     * 真实语音的能量包络起伏很大，这正是与稳态信号的判别依据。
     */
    private fun speechLike(samples: Int, seed: Long = 7L): ShortArray {
        val rnd = java.util.Random(seed)
        val out = ShortArray(samples)
        // 每 ~120ms 一个「音节」，音节内幅度先强后弱
        val syllable = AudioSpec.SAMPLE_RATE * 120 / 1000
        for (i in 0 until samples) {
            val posInSyllable = (i % syllable).toDouble() / syllable
            // 音节包络：起音快、衰减快，音节之间接近静音
            val envelope = if (posInSyllable < 0.5) {
                sin(PI * posInSyllable / 0.5) * 0.9
            } else {
                // 音节间隙：能量很低
                0.02
            }
            val v = rnd.nextGaussian() * 0.35 * envelope
            out[i] = (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return out
    }

    private fun framesOf(samples: ShortArray) = SpeechGate.analyzeSegment(samples)

    // ---------------------------------------------------------------- 第一级：能量门

    @Test
    fun `silence is not loud enough to invoke vad`() {
        val gate = SpeechGate()
        val silence = SpeechGate.FrameStats(rms = 0f, zcr = 0f)
        assertFalse("静音帧不应该触发 VAD 推理", gate.shouldInvokeVad(silence))
    }

    @Test
    fun `loud frames pass the energy gate`() {
        val gate = SpeechGate()
        val loud = SpeechGate.FrameStats(rms = 0.2f, zcr = 0.1f)
        assertTrue("明显有声的帧应该进入 VAD", gate.shouldInvokeVad(loud))
    }

    @Test
    fun `noise floor adapts downward during quiet periods`() {
        val gate = SpeechGate()
        val initial = gate.noiseFloor
        // 连续喂入比初始噪声底更低的静音帧
        repeat(50) { gate.shouldInvokeVad(SpeechGate.FrameStats(rms = 0.00005f, zcr = 0f)) }
        assertTrue(
            "静默期噪声底应向下自适应（$initial -> ${gate.noiseFloor}）",
            gate.noiseFloor < initial,
        )
    }

    @Test
    fun `noise floor does not drift upward on loud frames`() {
        val gate = SpeechGate()
        val initial = gate.noiseFloor
        repeat(50) { gate.shouldInvokeVad(SpeechGate.FrameStats(rms = 0.5f, zcr = 0.1f)) }
        assertEquals(
            "人声帧不应污染噪声底估计",
            initial, gate.noiseFloor, 1e-9f,
        )
    }

    @Test
    fun `threshold never drops below the absolute floor`() {
        val gate = SpeechGate()
        repeat(200) { gate.shouldInvokeVad(SpeechGate.FrameStats(rms = 0f, zcr = 0f)) }
        assertTrue(
            "门限必须有不被噪声底拖到 0 的绝对下限",
            gate.energyThreshold >= SpeechGate.DEFAULT_MIN_ENERGY - 1e-9f,
        )
    }

    @Test
    fun `reset restores the initial noise floor`() {
        val gate = SpeechGate()
        repeat(50) { gate.shouldInvokeVad(SpeechGate.FrameStats(rms = 0f, zcr = 0f)) }
        gate.reset()
        assertEquals(SpeechGate.DEFAULT_INITIAL_NOISE_FLOOR, gate.noiseFloor, 1e-9f)
    }

    // ---------------------------------------------------------------- 第二级：非人声启发式

    @Test
    fun `steady low frequency drone is rejected as non-speech`() {
        val gate = SpeechGate()
        // 100Hz 持续嗡鸣（电流声/低频 drone）：ZCR ≈ 0.0125，落在人声带之下
        val frames = framesOf(tone(100.0, AudioSpec.SAMPLE_RATE * 2))
        assertTrue("帧数应足够做判定", frames.size >= SpeechGate.MIN_FRAMES_FOR_VERDICT)
        assertTrue("持续低频嗡鸣应被判为非人声", gate.looksLikeNonSpeech(frames))
    }

    @Test
    fun `mid band steady tone is deliberately NOT rejected`() {
        // 召回优先的有意取舍：440Hz 纯音的 ZCR ≈ 0.055，落在人声带之内，
        // 靠过零率无法与语音区分。强行拒绝会开始误杀元音/浊音，
        // 而误杀 = 用户记忆永久丢失，代价远大于漏过滤。详见 SpeechGate KDoc。
        val gate = SpeechGate()
        val frames = framesOf(tone(440.0, AudioSpec.SAMPLE_RATE * 2))
        assertTrue("帧数应足够做判定", frames.size >= SpeechGate.MIN_FRAMES_FOR_VERDICT)
        assertFalse(
            "中频纯音落在人声带内，按设计必须放行（宁可漏过滤也不误杀）",
            gate.looksLikeNonSpeech(frames),
        )
    }

    @Test
    fun `steady tone zcr actually lands inside the speech band`() {
        // 锁住上面那条取舍所依赖的事实，避免以后有人误调 ZCR_TONE_MAX
        val frames = framesOf(tone(440.0, AudioSpec.SAMPLE_RATE * 2))
        val avgZcr = frames.map { it.zcr }.average().toFloat()
        assertTrue(
            "440Hz 的过零率($avgZcr)应高于 ZCR_TONE_MAX(${SpeechGate.ZCR_TONE_MAX})",
            avgZcr > SpeechGate.ZCR_TONE_MAX,
        )
        assertTrue(
            "440Hz 的过零率($avgZcr)应低于 ZCR_NOISE_MIN(${SpeechGate.ZCR_NOISE_MIN})",
            avgZcr < SpeechGate.ZCR_NOISE_MIN,
        )
    }

    @Test
    fun `steady broadband noise is rejected as non-speech`() {
        val gate = SpeechGate()
        val frames = framesOf(noise(AudioSpec.SAMPLE_RATE * 2))
        assertTrue("稳态宽带噪声应被判为非人声", gate.looksLikeNonSpeech(frames))
    }

    @Test
    fun `speech-like signal is NOT rejected`() {
        val gate = SpeechGate()
        val frames = framesOf(speechLike(AudioSpec.SAMPLE_RATE * 2))
        assertTrue("帧数应足够做判定", frames.size >= SpeechGate.MIN_FRAMES_FOR_VERDICT)
        assertFalse(
            "能量起伏明显的人声绝不能被误杀（这是召回底线）",
            gate.looksLikeNonSpeech(frames),
        )
    }

    @Test
    fun `short segments are never rejected`() {
        val gate = SpeechGate()
        // 不足 MIN_FRAMES_FOR_VERDICT 帧：样本不足，一律放行
        val frames = framesOf(tone(100.0, AudioSpec.FRAME_SAMPLES * 3))
        assertTrue(frames.size < SpeechGate.MIN_FRAMES_FOR_VERDICT)
        assertFalse("短促人声不能被误杀", gate.looksLikeNonSpeech(frames))
    }

    @Test
    fun `empty frames are never rejected`() {
        val gate = SpeechGate()
        assertFalse(gate.looksLikeNonSpeech(emptyList()))
    }

    @Test
    fun `silent segment is never rejected`() {
        val gate = SpeechGate()
        val frames = framesOf(ShortArray(AudioSpec.SAMPLE_RATE))
        // 全零：过零率 0（像纯音）但能量为 0 → 变异系数无意义，必须放行
        assertFalse("全静音段不应被判定为「非人声」而丢弃", gate.looksLikeNonSpeech(frames))
    }

    // ---------------------------------------------------------------- analyze

    @Test
    fun `analyze computes rms and zero crossing rate`() {
        val silence = SpeechGate.analyze(ShortArray(512))
        assertEquals(0f, silence.rms, 1e-9f)
        assertEquals(0f, silence.zcr, 1e-9f)

        // 满幅方波：每个采样点都变号 → 过零率接近 1
        val square = ShortArray(512) { if (it % 2 == 0) 32767 else -32768 }
        val stats = SpeechGate.analyze(square)
        assertTrue("方波 RMS 应接近满幅（${stats.rms}）", stats.rms > 0.9f)
        assertTrue("方波过零率应很高（${stats.zcr}）", stats.zcr > 0.9f)
    }

    @Test
    fun `analyze respects offset and length`() {
        val samples = ShortArray(1024)
        for (i in 512 until 1024) samples[i] = 32767
        val first = SpeechGate.analyze(samples, offset = 0, length = 512)
        val second = SpeechGate.analyze(samples, offset = 512, length = 512)
        assertEquals(0f, first.rms, 1e-9f)
        assertTrue(second.rms > 0.99f)
    }

    @Test
    fun `analyze handles out of range arguments safely`() {
        val samples = ShortArray(64)
        assertEquals(0f, SpeechGate.analyze(samples, offset = 100, length = 64).rms, 1e-9f)
        assertEquals(0f, SpeechGate.analyze(samples, offset = 0, length = 0).rms, 1e-9f)
    }

    @Test
    fun `analyzeSegment splits into frame sized windows`() {
        val samples = ShortArray(AudioSpec.FRAME_SAMPLES * 5)
        val frames = SpeechGate.analyzeSegment(samples)
        assertEquals(5, frames.size)
    }

    @Test
    fun `analyzeSegment drops a partial trailing frame`() {
        val samples = ShortArray(AudioSpec.FRAME_SAMPLES * 2 + 100)
        val frames = SpeechGate.analyzeSegment(samples)
        assertEquals("不足一帧的尾巴应被丢弃", 2, frames.size)
    }
}
