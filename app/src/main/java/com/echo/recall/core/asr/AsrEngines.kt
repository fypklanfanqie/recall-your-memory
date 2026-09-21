package com.echo.recall.core.asr

import android.util.Log
import com.echo.recall.core.audio.AudioSpec
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig

/**
 * 非流式识别引擎：覆盖 SenseVoice 与 Paraformer 两种模型。
 *
 * 两者共用 sherpa-onnx 的 [OfflineRecognizer]，只是 `modelConfig` 里填的字段不同。
 *
 * ⚠ 必须走文件版构造（不传 assetManager）：
 * 传了 AssetManager 时 sherpa-onnx 走 newFromAsset，把绝对路径当 APK assets 读，
 * 必然失败并导致 native abort（进程闪退，Kotlin try/catch 接不住）。
 * 模型存放在 filesDir/models/<dir>，属于文件系统路径 → newFromFile。
 */
class OfflineAsrEngine(
    spec: AsrModelSpec,
    modelDir: String,
    numThreads: Int = ModelCatalog.threadsFor(),
) : AsrEngine {

    private val recognizer: OfflineRecognizer
    private val supportsEmotion = spec.supportsEmotion

    init {
        val modelConfig = when (spec.kind) {
            AsrModelKind.SENSE_VOICE -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = "$modelDir/${spec.primaryFile.name}",
                    language = "auto",
                    useInverseTextNormalization = true,
                ),
                tokens = "$modelDir/${tokensName(spec)}",
                numThreads = numThreads,
                provider = "cpu",
                debug = false,
            )

            AsrModelKind.PARAFORMER -> OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(
                    model = "$modelDir/${spec.primaryFile.name}",
                ),
                tokens = "$modelDir/${tokensName(spec)}",
                numThreads = numThreads,
                provider = "cpu",
                debug = false,
            )

            else -> error("OfflineAsrEngine 不支持 ${spec.kind}（请用对应引擎）")
        }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = AudioSpec.SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(config = config)
        Log.i(TAG, "${spec.id} ready (threads=$numThreads, dir=$modelDir)")
    }

    /** @param samples float PCM [-1,1]，16kHz 单声道 */
    override fun transcribe(samples: FloatArray): AsrResult {
        if (samples.isEmpty()) return AsrResult(text = "")
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, AudioSpec.SAMPLE_RATE)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            // 显式声明为可空：Kotlin 视角这些字段是非空 String，但底层是 JNI，
            // 原生侧仍可能返回 null。这样写既保留防御性判空，又不产生「多余安全调用」告警。
            val rawText: String? = result.text
            val rawLang: String? = result.lang
            val rawEmotion: String? = result.emotion
            val rawEvent: String? = result.event
            AsrResult(
                text = rawText?.trim().orEmpty(),
                // Paraformer 不产出 lang/emotion/event，留空
                language = if (supportsEmotion) rawLang?.trim()?.takeIf { it.isNotBlank() } else null,
                emotion = if (supportsEmotion) rawEmotion?.trim()?.takeIf { it.isNotBlank() } else null,
                event = if (supportsEmotion) rawEvent?.trim()?.takeIf { it.isNotBlank() } else null,
                timestamps = result.timestamps,
            )
        } finally {
            runCatching { stream.release() }
        }
    }

    override fun release() {
        runCatching { recognizer.release() }
    }

    companion object {
        private const val TAG = "OfflineAsrEngine"
        private const val FEATURE_DIM = 80

        fun tokensName(spec: AsrModelSpec): String =
            spec.files.firstOrNull { it.name == "tokens.txt" }?.name ?: "tokens.txt"
    }
}

/**
 * 流式 Zipformer（transducer）引擎，用于最小体积档。
 *
 * 转写流程仍是「整段批量」：把整段波形喂完 → `inputFinished()` → 取结果。
 * 流式结构的意义在于同一份模型未来可以做「边录边出字」，且 14M 参数体积仅约 31 MB。
 *
 * 同样必须走文件版构造（不传 assetManager），理由见 [OfflineAsrEngine]。
 */
class StreamingAsrEngine(
    spec: AsrModelSpec,
    modelDir: String,
    numThreads: Int = ModelCatalog.threadsFor(),
) : AsrEngine {

    private val recognizer: com.k2fsa.sherpa.onnx.OnlineRecognizer

    init {
        val config = com.k2fsa.sherpa.onnx.OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = AudioSpec.SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = com.k2fsa.sherpa.onnx.OnlineModelConfig(
                transducer = com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig(
                    encoder = "$modelDir/${fileOf(spec, "encoder")}",
                    decoder = "$modelDir/${fileOf(spec, "decoder")}",
                    joiner = "$modelDir/${fileOf(spec, "joiner")}",
                ),
                tokens = "$modelDir/${OfflineAsrEngine.tokensName(spec)}",
                numThreads = numThreads,
                provider = "cpu",
                debug = false,
            ),
            // 批量转写场景不需要端点检测（我们自己按 VAD 片段切好了）
            enableEndpoint = false,
            decodingMethod = "greedy_search",
        )
        recognizer = com.k2fsa.sherpa.onnx.OnlineRecognizer(config = config)
        Log.i(TAG, "${spec.id} ready (threads=$numThreads, dir=$modelDir)")
    }

    override fun transcribe(samples: FloatArray): AsrResult {
        if (samples.isEmpty()) return AsrResult(text = "")
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, AudioSpec.SAMPLE_RATE)
            stream.inputFinished()
            // 流式解码：把所有可用的帧都推完（批量场景下通常 1~2 轮就够）
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
            val rawText: String? = recognizer.getResult(stream).text
            AsrResult(text = rawText?.trim().orEmpty())
        } finally {
            runCatching { stream.release() }
        }
    }

    override fun release() {
        runCatching { recognizer.release() }
    }

    companion object {
        private const val TAG = "StreamingAsrEngine"
        private const val FEATURE_DIM = 80

        /** 按文件名前缀找 encoder / decoder / joiner（文件名带 epoch 后缀，不能写死） */
        fun fileOf(spec: AsrModelSpec, prefix: String): String =
            spec.files.firstOrNull { it.name.startsWith(prefix) }?.name
                ?: error("${spec.id} 缺少 $prefix 文件")
    }
}

/** 按模型类型创建引擎（唯一的引擎工厂） */
object AsrEngineFactory {
    fun create(spec: AsrModelSpec, modelDir: String, numThreads: Int = ModelCatalog.threadsFor()): AsrEngine =
        when (spec.kind) {
            AsrModelKind.SENSE_VOICE, AsrModelKind.PARAFORMER ->
                OfflineAsrEngine(spec, modelDir, numThreads)
            AsrModelKind.ZIPFORMER_STREAM ->
                StreamingAsrEngine(spec, modelDir, numThreads)
            AsrModelKind.WHISPER ->
                error("Whisper 档位未启用（中文准确率偏弱，已在 v1.1 分级中移除）")
        }
}
