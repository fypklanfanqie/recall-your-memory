package com.echo.recall.core.audio

/** 全局音频规格：16kHz 单声道 PCM16（silero-vad 与 SenseVoice 都要求 16k）。 */
object AudioSpec {
    const val SAMPLE_RATE = 16000

    /** silero-vad 的窗口大小（512 采样 = 32ms @16k） */
    const val FRAME_SAMPLES = 512

    const val BYTES_PER_SAMPLE = 2
    const val BYTES_PER_SECOND = SAMPLE_RATE * BYTES_PER_SAMPLE // 32000 B/s

    fun msToSamples(ms: Long): Int = (ms * SAMPLE_RATE / 1000L).toInt()
    fun samplesToMs(samples: Int): Long = samples * 1000L / SAMPLE_RATE
}

/** PCM 16bit ↔ Float [-1,1] ↔ 小端字节 转换。 */
object PcmUtils {

    fun shortsToFloats(src: ShortArray, length: Int = src.size): FloatArray {
        val out = FloatArray(length)
        for (i in 0 until length) out[i] = src[i] / 32768.0f
        return out
    }

    fun floatsToShorts(src: FloatArray, length: Int = src.size): ShortArray {
        val out = ShortArray(length)
        for (i in 0 until length) {
            val v = (src[i] * 32768.0f).toInt()
            out[i] = v.coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** 小端写字节（MediaCodec / WAV 都要求小端） */
    fun shortsToBytes(src: ShortArray, length: Int = src.size): ByteArray {
        val out = ByteArray(length * 2)
        for (i in 0 until length) {
            val s = src[i].toInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    fun bytesToShorts(src: ByteArray, length: Int = src.size / 2): ShortArray {
        val out = ShortArray(length)
        for (i in 0 until length) {
            val lo = src[i * 2].toInt() and 0xFF
            val hi = src[i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }

    /** 拼接多个片段（召回时用于整体编码 / 转写） */
    fun concat(chunks: List<ShortArray>): ShortArray {
        val total = chunks.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (c in chunks) {
            System.arraycopy(c, 0, out, offset, c.size)
            offset += c.size
        }
        return out
    }
}
