package com.echo.recall.core.asr

import android.content.res.AssetManager
import android.util.Log
import com.echo.recall.core.audio.AudioSpec
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig

data class AsrResult(
    val text: String,
    val language: String? = null,
    val emotion: String? = null,
    val event: String? = null,
    val timestamps: FloatArray? = null,
)

/**
 * SenseVoice-small（sherpa-onnx）离线识别封装。
 * useInverseTextNormalization = true → 输出带标点、数字规范化。
 */
class SenseVoiceEngine(
    assetManager: AssetManager,
    modelDir: String,
    numThreads: Int = DEFAULT_THREADS,
) {
    private val recognizer: OfflineRecognizer

    init {
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = AudioSpec.SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = "$modelDir/${SenseVoiceFiles.MODEL.name}",
                    language = "auto",
                    useInverseTextNormalization = true,
                ),
                tokens = "$modelDir/${SenseVoiceFiles.TOKENS.name}",
                numThreads = numThreads,
                provider = "cpu",
                debug = false,
            ),
            decodingMethod = "greedy_search",
        )
        recognizer = OfflineRecognizer(assetManager = assetManager, config = config)
        Log.i(TAG, "SenseVoice ready (threads=$numThreads, dir=$modelDir)")
    }

    /** @param samples float PCM [-1,1]，16kHz 单声道 */
    fun transcribe(samples: FloatArray): AsrResult {
        if (samples.isEmpty()) return AsrResult(text = "")
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, AudioSpec.SAMPLE_RATE)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            AsrResult(
                text = result.text?.trim().orEmpty(),
                language = result.lang?.trim()?.takeIf { it.isNotBlank() },
                emotion = result.emotion?.trim()?.takeIf { it.isNotBlank() },
                event = result.event?.trim()?.takeIf { it.isNotBlank() },
                timestamps = result.timestamps,
            )
        } finally {
            runCatching { stream.release() }
        }
    }

    fun release() {
        runCatching { recognizer.release() }
    }

    companion object {
        private const val TAG = "SenseVoiceEngine"
        private const val FEATURE_DIM = 80
        const val DEFAULT_THREADS = 2
    }
}
