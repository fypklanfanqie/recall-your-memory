package com.echo.recall.core.asr

/** 一次识别结果。`language`/`emotion`/`event` 仅 SenseVoice 会填充。 */
data class AsrResult(
    val text: String,
    val language: String? = null,
    val emotion: String? = null,
    val event: String? = null,
    val timestamps: FloatArray? = null,
)

/**
 * 离线识别引擎抽象。
 *
 * 引入原因：v1.0 把 SenseVoice 硬编码在 `AsrModels` / `SenseVoiceEngine` / `ModelManager`
 * 三处，导致无法换模型。v1.1 支持多档模型，按 [AsrModelSpec.kind] 分派实现。
 */
interface AsrEngine {
    /** @param samples float PCM [-1,1]，16kHz 单声道 */
    fun transcribe(samples: FloatArray): AsrResult

    fun release()
}

/** 模型家族 —— 决定用哪个 sherpa-onnx 配置与哪个引擎实现 */
enum class AsrModelKind {
    /** SenseVoice：多语种 + 情感/事件标签，体积最大 */
    SENSE_VOICE,

    /** Paraformer：纯中文/中英，非流式，准确率高 */
    PARAFORMER,

    /** 流式 Zipformer：体积小、可边录边出字 */
    ZIPFORMER_STREAM,

    /** Whisper：多语种通用，中文偏弱（体积最小的兜底档） */
    WHISPER,
}

/**
 * 性能分档（按「最低可用机型」划分，而非按参数量）。
 *
 * 用户决策：只上 L1/L2/L3 三档，砍掉原方案的 L4（232MB 的 Paraformer-zh 大模型，
 * 体积与 L3 几乎相同而提升有限）。
 */
enum class AsrModelTier(val label: String, val order: Int) {
    /** 老机型 / 低内存：体积最小，够用就好 */
    L1_LIGHT("轻量", 1),

    /** 绝大多数用户的最优解：体积约为 L3 的 1/3 */
    L2_BALANCED("均衡", 2),

    /** 高配机：多语种 + 情感/事件标签 */
    L3_ACCURATE("高精度", 3),
}

/** 单个模型文件的下载描述（沿用 v1.0 的断点续传 + SHA-256 校验流程） */
data class AsrModelFile(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
    val urls: List<String>,
)

/**
 * 一个可下载的本地转写模型。
 *
 * @param dirName  filesDir/models/<dirName> 下的独立子目录（多模型可并存）
 * @param minCores 建议的最低 CPU 核心数
 * @param minRamMb 建议的最低可用内存（MB）
 * @param accuracyNote 诚实标注的准确率/适用性说明，直接展示给用户
 */
data class AsrModelSpec(
    val id: String,
    val displayName: String,
    val tier: AsrModelTier,
    val kind: AsrModelKind,
    val dirName: String,
    val files: List<AsrModelFile>,
    val minCores: Int,
    val minRamMb: Int,
    val languages: String,
    val accuracyNote: String,
    val supportsEmotion: Boolean,
    val supportsStreaming: Boolean,
    /** 是否已在仓库中实测过下载地址可达（国内直连） */
    val verifiedInChina: Boolean = true,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }

    fun totalMb(): Long = totalBytes / (1024 * 1024)

    /** 主模型文件（用于 UI 展示与哈希校验提示） */
    val primaryFile: AsrModelFile get() = files.first()
}
