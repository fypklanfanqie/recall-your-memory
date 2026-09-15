package com.echo.recall.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 把已保存的 .m4a 记忆音频解码回 PCM16，用于「补转写 / 重新转写」。
 */
object AudioDecoder {

    private const val TAG = "AudioDecoder"
    private const val TIMEOUT_US = 10_000L

    fun decodeToPcm16(file: File): ShortArray? {
        if (!file.exists()) return null
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return try {
            extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val output = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf: ByteBuffer = codec.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    val outBuf: ByteBuffer = codec.getOutputBuffer(outIndex) ?: continue
                    if (info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.get(chunk)
                        output.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                }
            }

            val raw = output.toByteArray()
            // AAC 解码输出为 16bit 小端 PCM
            val shorts = ShortArray(raw.size / 2)
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            Log.i(TAG, "decoded ${file.name}: ${shorts.size} samples (${AudioSpec.samplesToMs(shorts.size)}ms)")
            shorts
        } catch (t: Throwable) {
            Log.e(TAG, "decode failed", t)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    /** 按片段偏移从整段 PCM 中切片（补转写用） */
    fun slice(pcm: ShortArray, startMs: Long, endMs: Long): ShortArray {
        val from = AudioSpec.msToSamples(startMs).coerceIn(0, pcm.size)
        val to = AudioSpec.msToSamples(endMs).coerceIn(from, pcm.size)
        if (to <= from) return ShortArray(0)
        return pcm.copyOfRange(from, to)
    }
}
