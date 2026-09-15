package com.echo.recall.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 录音引擎：后台常驻聆听 + 人声环形缓冲。
 *
 * 省电策略：静默期间只有 AudioRecord 读取 + silero-vad 推理，
 * 不做转写、不做编码、不联网。
 */
@Singleton
class RecorderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class State { IDLE, LISTENING, PAUSED, ERROR }

    data class Status(
        val state: State = State.IDLE,
        val speechDetected: Boolean = false,
        val voicedMillisInWindow: Long = 0L,
        val lastVoiceEndMs: Long? = null,
        val windowMs: Long = DEFAULT_WINDOW_MS,
        val bufferBytes: Int = 0,
        val message: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val ring = SpeechRingBuffer()
    private val audioLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var vad: VadEngine? = null
    private var record: AudioRecord? = null
    private var captureThread: Thread? = null
    private var tickerJob: Job? = null

    @Volatile private var running = false
    @Volatile private var speechDetected = false
    @Volatile private var windowMs = DEFAULT_WINDOW_MS
    @Volatile private var preset = VadSensitivityPreset.MEDIUM
    private var lastPublishMs = 0L

    val isListening: Boolean get() = running

    val currentWindowMs: Long get() = windowMs

    /** VAD 初始化较重（加载 onnx），务必在后台线程调用 */
    suspend fun start(windowMs: Long, preset: VadSensitivityPreset) = withContext(Dispatchers.IO) {
        if (running) {
            this@RecorderEngine.windowMs = windowMs
            this@RecorderEngine.preset = preset
            publishStatus(SystemClock.elapsedRealtime())
            return@withContext
        }
        this@RecorderEngine.windowMs = windowMs
        this@RecorderEngine.preset = preset

        try {
            val now = SystemClock.elapsedRealtime()
            vad?.release()
            vad = VadEngine(context.assets, preset).also { it.reset(now) }

            val audioRecord = createAudioRecord()
            if (audioRecord == null) {
                _status.value = Status(
                    state = State.ERROR,
                    windowMs = windowMs,
                    message = "麦克风不可用（权限或占用）",
                )
                return@withContext
            }
            record = audioRecord
            audioRecord.startRecording()
            running = true

            captureThread = Thread({ captureLoop() }, "echo-capture").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            startTicker()
            publishStatus(now)
            Log.i(TAG, "listening started (window=${windowMs}ms, preset=$preset)")
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            _status.value = Status(state = State.ERROR, windowMs = windowMs, message = t.message ?: "启动失败")
        }
    }

    fun pause() {
        if (!running) return
        running = false
        stopTicker()
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        captureThread?.join(500)
        captureThread = null
        speechDetected = false
        _status.value = _status.value.copy(state = State.PAUSED, speechDetected = false)
        Log.i(TAG, "paused (buffer kept: ${ring.size} segments)")
    }

    suspend fun resume() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        try {
            val now = SystemClock.elapsedRealtime()
            synchronized(audioLock) { vad?.reset(now) }
            val audioRecord = createAudioRecord() ?: run {
                _status.value = _status.value.copy(state = State.ERROR, message = "麦克风不可用")
                return@withContext
            }
            record = audioRecord
            audioRecord.startRecording()
            running = true
            captureThread = Thread({ captureLoop() }, "echo-capture").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            startTicker()
            publishStatus(now)
            Log.i(TAG, "resumed")
        } catch (t: Throwable) {
            Log.e(TAG, "resume failed", t)
            _status.value = _status.value.copy(state = State.ERROR, message = t.message ?: "恢复失败")
        }
    }

    fun stop() {
        running = false
        stopTicker()
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        captureThread?.join(500)
        captureThread = null
        synchronized(audioLock) {
            vad?.release()
            vad = null
            ring.clear()
        }
        speechDetected = false
        _status.value = Status(state = State.IDLE, windowMs = windowMs)
        Log.i(TAG, "stopped")
    }

    /** 回溯：冲刷在说的那一段 → 窗口裁剪 → 取走并清空 */
    fun recall(): List<SpeechRingBuffer.Segment> {
        val now = SystemClock.elapsedRealtime()
        synchronized(audioLock) {
            vad?.flush(now)?.forEach { ring.add(it.toRingSegment()) }
            ring.trim(now, windowMs)
            val taken = ring.snapshotAndClear()
            lastPublishMs = 0
            publishStatusLocked(now)
            Log.i(TAG, "recall took ${taken.size} segments")
            return taken
        }
    }

    /** epoch 偏移：把 elapsedRealtime 时间戳换算成真实墙钟时间 */
    fun epochOffsetMs(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /** 丢弃缓冲（例如切换窗口时长后） */
    fun clearBuffer() {
        synchronized(audioLock) { ring.clear() }
        publishStatusLocked(SystemClock.elapsedRealtime())
    }

    private fun captureLoop() {
        val buffer = ShortArray(AudioSpec.FRAME_SAMPLES)
        while (running) {
            val read = try {
                record?.read(buffer, 0, buffer.size) ?: -1
            } catch (t: Throwable) {
                Log.e(TAG, "read failed", t)
                -1
            }
            if (read > 0) {
                val now = SystemClock.elapsedRealtime()
                val engine = vad ?: break
                synchronized(audioLock) {
                    val closed = engine.accept(PcmUtils.shortsToFloats(buffer, read), now)
                    for (segment in closed) ring.add(segment.toRingSegment())
                    ring.trim(now, windowMs)
                    speechDetected = engine.isSpeechDetected()
                    publishStatusLocked(now)
                }
            } else if (read < 0) {
                Log.w(TAG, "AudioRecord.read returned $read")
            }
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive && running) {
                delay(1_000)
                val now = SystemClock.elapsedRealtime()
                synchronized(audioLock) {
                    ring.trim(now, windowMs)
                    publishStatusLocked(now, force = true)
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun publishStatus(nowMs: Long) {
        synchronized(audioLock) { publishStatusLocked(nowMs) }
    }

    private fun publishStatusLocked(nowMs: Long, force: Boolean = false) {
        if (!force && nowMs - lastPublishMs < STATUS_INTERVAL_MS) return
        lastPublishMs = nowMs
        val state = when {
            _status.value.state == State.ERROR -> State.ERROR
            running -> State.LISTENING
            else -> State.PAUSED
        }
        _status.value = Status(
            state = state,
            speechDetected = speechDetected,
            voicedMillisInWindow = ring.voicedMillis(),
            lastVoiceEndMs = ring.lastVoiceEndMs(),
            windowMs = windowMs,
            bufferBytes = ring.bytes,
            message = _status.value.message,
        )
    }

    private fun createAudioRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioSpec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        val bufferSize = maxOf(minBuffer, AudioSpec.FRAME_SAMPLES * AudioSpec.BYTES_PER_SAMPLE * 4)
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioSpec.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "AudioRecord creation failed", t)
            return null
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return null
        }
        return audioRecord
    }

    companion object {
        private const val TAG = "RecorderEngine"
        private const val STATUS_INTERVAL_MS = 250L
        const val DEFAULT_WINDOW_MS = 3 * 60 * 1000L
    }
}
