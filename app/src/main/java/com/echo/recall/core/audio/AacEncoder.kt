package com.echo.recall.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * PCM16 → AAC-LC(.m4a) 编码。
 * 只在「回溯」时执行一次，后台聆听期间不做任何编码（省电）。
 */
object AacEncoder {

    private const val TAG = "AacEncoder"
    private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val TIMEOUT_US = 10_000L

    /**
     * @param pcm 16kHz 单声道 PCM16
     * @return 成功返回 true
     */
    fun encodeToM4a(
        pcm: ShortArray,
        output: File,
        sampleRate: Int = AudioSpec.SAMPLE_RATE,
        bitRate: Int = DEFAULT_BIT_RATE,
    ): Boolean {
        if (pcm.isEmpty()) return false
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        return try {
            val format = MediaFormat.createAudioFormat(MIME, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 16)
            }
            codec = MediaCodec.createEncoderByType(MIME)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            val bytes = PcmUtils.shortsToBytes(pcm)
            val totalBytes = bytes.size
            var inputOffset = 0
            var trackIndex = -1
            var muxerStarted = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (inputOffset < totalBytes) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf: ByteBuffer = codec.getInputBuffer(inIndex) ?: continue
                        inBuf.clear()
                        val chunk = minOf(inBuf.capacity(), totalBytes - inputOffset)
                        inBuf.put(bytes, inputOffset, chunk)
                        codec.queueInputBuffer(inIndex, 0, chunk, 0L, 0)
                        inputOffset += chunk
                    }
                } else {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf: ByteBuffer = codec.getOutputBuffer(outIndex) ?: continue
                        // 编码器可能带 codec config 数据，仅在 muxer 启动后写入
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }
            Log.i(TAG, "encoded ${pcm.size} samples -> ${output.name} (${output.length()} bytes)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "encode failed", t)
            output.delete()
            false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    const val DEFAULT_BIT_RATE = 32_000
}
