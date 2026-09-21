package com.echo.recall.core.audio

/**
 * 两级触发门（省 CPU + 降误报）。
 *
 * ## 第一级「能量门」—— 在 VAD 之前
 *
 * 静默期绝大多数帧根本不含人声，喂给 silero-vad 做 onnx 推理是纯浪费。
 * 用一个自适应噪声底（EMA）算出能量门限，**不达标直接丢弃，不调用 VAD**。
 * 这是静默期最主要的 CPU 节省点。
 *
 * ## 第二级「非人声启发式」—— 过零率 + 能量平稳度
 *
 * 目标：滤掉把环形缓冲塞满的**稳态**声音（持续嗡鸣、白噪声、长音 drone）。
 *
 * 判据是「**同时**满足」下面两条才拒绝：
 * 1. 绝大多数帧的过零率落在人声带之外（< [ZCR_TONE_MAX] 像低频嗡鸣，> [ZCR_NOISE_MIN] 像宽带噪声）
 * 2. 帧间能量非常平稳（变异系数 ≤ [STEADY_CV_MAX]）—— 真实说话的音节包络起伏很大
 *
 * ### ⚠ 有意为之的局限（不要误以为它能滤掉所有音乐）
 *
 * 这个门是**召回优先**的，宁可漏过滤也不误杀真人声：
 *
 * - **中频持续纯音**（如 440Hz，ZCR ≈ 0.055）落在人声带**之内**，因此**不会**被拒绝。
 *   这不是 bug —— 靠过零率无法把 440Hz 纯音与语音分开，强行调低阈值会开始误杀
 *   元音和浊音。真实的电视/音乐声是宽带的，通常也不会是单一纯音。
 * - **带节奏起伏的音乐**（鼓点）能量变异系数高，同样会放行。
 *
 * 之所以这样取舍：漏过滤的代价是「回溯时听到一段音乐」（轻微烦扰，用户仍可继续用），
 * 而误杀的代价是「用户的一段记忆永久消失」（不可挽回）。产品上后者严重得多。
 * 想更激进地过滤，正确做法是**转写后**用 SenseVoice 的 `event` 标签回标
 * （见 SenseVoice 支持的 Speech/BGM/Applause 标签），而不是在 VAD 阶段冒险。
 *
 * 纯 Kotlin、无 Android 依赖 → 可用 JVM 单元测试。
 */
class SpeechGate(
    /** 能量门限 = 噪声底 × 该系数 */
    private val energyFactor: Float = DEFAULT_ENERGY_FACTOR,
    /** 能量门限的绝对值下限，防止安静房间里门限趋近于 0 */
    private val minEnergy: Float = DEFAULT_MIN_ENERGY,
    /** 噪声底 EMA 平滑系数（越小越迟钝） */
    private val noiseAdapt: Float = DEFAULT_NOISE_ADAPT,
) {
    /** 单帧统计量 */
    data class FrameStats(
        val rms: Float,
        val zcr: Float,
    )

    @Volatile
    var noiseFloor: Float = DEFAULT_INITIAL_NOISE_FLOOR
        private set

    /** 当前能量门限（供诊断/测试观察） */
    val energyThreshold: Float
        get() = maxOf(noiseFloor * energyFactor, minEnergy)

    /**
     * 第一级：这一帧是否值得送进 VAD。
     *
     * 未达标时顺带把噪声底往下拉（静默期自适应），达标则不更新噪声底，
     * 避免把人声能量污染进噪声估计。
     */
    fun shouldInvokeVad(stats: FrameStats): Boolean {
        val loud = stats.rms >= energyThreshold
        if (!loud) {
            noiseFloor = noiseFloor * (1f - noiseAdapt) + stats.rms * noiseAdapt
        }
        return loud
    }

    /**
     * 第二级：一段（已被 VAD 判为人声的）音频是否「像音乐/稳态噪声」。
     *
     * 保守策略：必须**同时**满足
     *  1. 绝大多数帧的过零率落在人声带之外（过低=纯音，过高=噪声）
     *  2. 帧间能量非常平稳（真实说话的能量起伏很大）
     * 才判为非人声。任一不满足即放行。
     *
     * @param frames 该段逐帧统计；帧数太少时一律放行（样本不足不妄下结论）
     */
    fun looksLikeNonSpeech(frames: List<FrameStats>): Boolean {
        if (frames.size < MIN_FRAMES_FOR_VERDICT) return false

        var tooLow = 0
        var tooHigh = 0
        for (f in frames) {
            if (f.zcr < ZCR_TONE_MAX) tooLow++
            if (f.zcr > ZCR_NOISE_MIN) tooHigh++
        }
        val tonal = tooLow.toFloat() / frames.size >= OUT_OF_BAND_RATIO
        val noisy = tooHigh.toFloat() / frames.size >= OUT_OF_BAND_RATIO
        if (!tonal && !noisy) return false

        // 能量平稳度：变异系数低 = 像稳态信号；说话会忽大忽小
        val mean = frames.sumOf { it.rms.toDouble() } / frames.size
        if (mean <= 1e-9) return false
        var variance = 0.0
        for (f in frames) {
            val d = f.rms - mean
            variance += d * d
        }
        val cv = kotlin.math.sqrt(variance / frames.size) / mean
        return cv <= STEADY_CV_MAX
    }

    fun reset() {
        noiseFloor = DEFAULT_INITIAL_NOISE_FLOOR
    }

    companion object {
        const val DEFAULT_ENERGY_FACTOR = 2.5f
        const val DEFAULT_MIN_ENERGY = 1.5e-4f
        const val DEFAULT_NOISE_ADAPT = 0.05f
        const val DEFAULT_INITIAL_NOISE_FLOOR = 1e-4f

        /** 人声带之外的比例超过该值才考虑拒绝 */
        const val OUT_OF_BAND_RATIO = 0.85f

        /** 过零率低于此值 → 像持续纯音（乐音/嗡鸣） */
        const val ZCR_TONE_MAX = 0.02f

        /** 过零率高于此值 → 像宽带噪声 */
        const val ZCR_NOISE_MIN = 0.35f

        /** 能量变异系数低于此值 → 像稳态信号而非说话 */
        const val STEADY_CV_MAX = 0.35

        /** 少于这么多帧不做判定（短促人声不误杀） */
        const val MIN_FRAMES_FOR_VERDICT = 8

        /** 计算单帧统计量（纯函数，可测） */
        fun analyze(samples: ShortArray, offset: Int = 0, length: Int = samples.size - offset): FrameStats {
            if (length <= 0 || offset < 0 || offset + length > samples.size) return FrameStats(0f, 0f)
            var sumSq = 0.0
            for (i in offset until offset + length) {
                val v = samples[i] / 32768.0
                sumSq += v * v
            }
            val rms = kotlin.math.sqrt(sumSq / length).toFloat()

            var crossings = 0
            for (i in offset + 1 until offset + length) {
                val prev = samples[i - 1]
                val cur = samples[i]
                if ((prev < 0 && cur >= 0) || (prev >= 0 && cur < 0)) crossings++
            }
            val zcr = if (length > 1) crossings.toFloat() / (length - 1) else 0f
            return FrameStats(rms, zcr)
        }

        /**
         * 把整段 PCM 切成 [AudioSpec.FRAME_SAMPLES] 帧后逐帧统计。
         * 尾部不足一帧的丢弃（不影响判定）。
         */
        fun analyzeSegment(samples: ShortArray): List<FrameStats> {
            val n = samples.size / AudioSpec.FRAME_SAMPLES
            if (n <= 0) return emptyList()
            val out = ArrayList<FrameStats>(n)
            for (i in 0 until n) {
                out.add(analyze(samples, i * AudioSpec.FRAME_SAMPLES, AudioSpec.FRAME_SAMPLES))
            }
            return out
        }
    }
}
