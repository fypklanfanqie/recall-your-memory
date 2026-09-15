package com.echo.recall.core.asr

/**
 * SenseVoice-small int8 模型文件清单。
 *
 * 下载源顺序：国内镜像 → 官方站（用户侧通常无需 VPN）。
 * 哈希用于完整性校验（可用 tools/Download.java 复现）。
 */
object SenseVoiceFiles {

    const val HF_MIRROR = "https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main"
    const val HF_OFFICIAL = "https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main"

    val MODEL = AsrModelFile(
        name = "model.int8.onnx",
        sizeBytes = 239_233_841L,
        sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
        urls = listOf(
            "$HF_MIRROR/model.int8.onnx",
            "$HF_OFFICIAL/model.int8.onnx",
        ),
    )

    val TOKENS = AsrModelFile(
        name = "tokens.txt",
        sizeBytes = 315_894L,
        sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
        urls = listOf(
            "$HF_MIRROR/tokens.txt",
            "$HF_OFFICIAL/tokens.txt",
        ),
    )

    val all = listOf(MODEL, TOKENS)
}

data class AsrModelFile(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
    val urls: List<String>,
)
