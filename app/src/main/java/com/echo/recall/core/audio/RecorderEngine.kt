package com.echo.recall.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.echo.recall.core.data.settings.EchoSettings
import com.echo.recall.core.data.settings.PowerProfile
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
 * ## 省电设计（v1.1 重构）
 *
 * 1. **两级触发门**（[SpeechGate]）：静默期绝大多数帧不喂 VAD，直接省掉 onnx 推理。
 * 2. **采集循环去锁化 + 零分配**：预分配缓冲复用；VAD 推理不再持 [audioLock]。
 * 3. **静默期降频**：连续静默后 ticker 从 1s 降到 5s；极致省电档进一步跳过 VAD 推理
 *    （音频仍写入 [raw] 环形缓冲，能量触发后**回灌**给 VAD，因此不丢音）。
 * 4. **线程优先级**：capture 线程用 `THREAD_PRIORITY_AUDIO` 而非 `MAX_PRIORITY`，
 *    不再抢占大核。
 * 5. **唤醒锁**：不在本类持有（由 [com.echo.recall.core.service.RecorderService]
 *    按屏幕状态与聆听状态决定）。
 */
@Singleton
class RecorderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class State { IDLE, LISTENING, PAUSED, ERROR }

    /** 静默期占空比档位（对外可观测，便于 UI 展示与真机验证） */
    enum class CaptureMode { ACTIVE, COOLDOWN, VAD_SKIP }

    data class Status(
        val state: State = State.IDLE,
        val speechDetected: Boolean = false,
        val voicedMillisInWindow: Long = 0L,
        val lastVoiceEndMs: Long? = null,
        val windowMs: Long = DEFAULT_WINDOW_MS,
        val bufferBytes: Int = 0,
        val message: String? = null,
        val captureMode: CaptureMode = CaptureMode.ACTIVE,
        /** VAD 推理被跳过的帧占比（省电效果的自证指标，0..1） */
        val vadSkippedRatio: Float = 0f,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val ring = SpeechRingBuffer()
    private val raw = RawAudioRing()
    private val gate = SpeechGate()

    /** 保护 ring / raw / gate / status */
    private val audioLock = Any()

    /** 保护 vad（capture 线程推理 vs recall 线程 flush） */
    private val vadLock = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var vad: VadEngine? = null
    private var record: AudioRecord? = null
    private var captureThread: Thread? = null
    private var tickerJob: Job? = null

    @Volatile private var running = false
    @Volatile private var speechDetected = false
    @Volatile private var windowMs = DEFAULT_WINDOW_MS
    @Volatile private var preset = VadSensitivityPreset.MEDIUM
    @Volatile private var powerProfile = PowerProfile.BALANCED

    // ---- 采集线程私有状态（不需要锁）----
    private val frameShorts = ShortArray(AudioSpec.FRAME_SAMPLES)
    private val frameFloats = FloatArray(AudioSpec.FRAME_SAMPLES)
    private var lastVoiceAtMs = 0L
    private var captureMode = CaptureMode.ACTIVE
    private var skippedFrames = 0L
    private var totalFrames = 0L
    /** VAD 报出起点时抓下的句首预滚 */
    private var pendingPreroll: ShortArray? = null
    private var wasSpeech = false
    /** VAD_SKIP 期间攒下的帧（回灌用，容量够装一个占空比周期） */
    private val skipBacklog = ArrayDeque<ShortArray>()
    /** 被永久丢弃、从未喂给 VAD 的音频时长（用于补偿时间戳映射） */
    private var vadSkipDebtMs = 0L

    private var lastPublishMs = 0L

    val isListening: Boolean get() = running

    val currentWindowMs: Long get() = windowMs

    /** VAD 初始化较重（加载 onnx），务必在后台线程调用 */
    suspend fun start(
        windowMs: Long,
        preset: VadSensitivityPreset,
        powerProfile: PowerProfile = PowerProfile.BALANCED,
    ) = withContext(Dispatchers.IO) {
        if (running) {
            this@RecorderEngine.windowMs = windowMs
            this@RecorderEngine.preset = preset
            this@RecorderEngine.powerProfile = powerProfile
            publishStatus(SystemClock.elapsedRealtime())
            return@withContext
        }
        this@RecorderEngine.windowMs = windowMs
        this@RecorderEngine.preset = preset
        this@RecorderEngine.powerProfile = powerProfile

        try {
            val now = SystemClock.elapsedRealtime()
            synchronized(vadLock) {
                vad?.release()
                vad = VadEngine(context.assets, preset).also { it.reset(now) }
            }

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
            resetCaptureState(now)

            captureThread = Thread({ captureLoop() }, "echo-capture").apply {
                // AUDIO 优先级足够保证采集实时性，又不像 MAX_PRIORITY 那样抢占大核
                priority = Thread.NORM_PRIORITY
                start()
            }
            startTicker()
            publishStatus(now)
            Log.i(TAG, "listening started (window=${windowMs}ms, preset=$preset, power=$powerProfile)")
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            _status.value = Status(state = State.ERROR, windowMs = windowMs, message = t.message ?: "启动失败")
        }
    }

    /** 切换功耗档（不重启采集） */
    fun setPowerProfile(profile: PowerProfile) {
        powerProfile = profile
        if (running) {
            val now = SystemClock.elapsedRealtime()
            synchronized(audioLock) { publishStatusLocked(now, force = true) }
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
            synchronized(vadLock) { vad?.reset(now) }
            val audioRecord = createAudioRecord() ?: run {
                _status.value = _status.value.copy(state = State.ERROR, message = "麦克风不可用")
                return@withContext
            }
            record = audioRecord
            audioRecord.startRecording()
            running = true
            resetCaptureState(now)
            captureThread = Thread({ captureLoop() }, "echo-capture").apply {
                priority = Thread.NORM_PRIORITY
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
        synchronized(vadLock) {
            vad?.release()
            vad = null
        }
        synchronized(audioLock) {
            ring.clear()
            raw.clear()
            gate.reset()
            skipBacklog.clear()
            pendingPreroll = null
        }
        speechDetected = false
        _status.value = Status(state = State.IDLE, windowMs = windowMs)
        Log.i(TAG, "stopped")
    }

    /** 回溯：冲刷在说的那一段 → 窗口裁剪 → 取走并清空 */
    fun recall(): List<SpeechRingBuffer.Segment> {
        val now = SystemClock.elapsedRealtime()
        // 锁序固定：vadLock → audioLock（与 captureLoop 一致）
        val flushed = synchronized(vadLock) { vad?.flush(now) }.orEmpty()
        commitSegments(flushed, now)
        synchronized(audioLock) {
            ring.trim(now, windowMs)
            val taken = ring.snapshotAndClear()
            pendingPreroll = null
            lastPublishMs = 0
            publishStatusLocked(now, force = true)
            Log.i(TAG, "recall took ${taken.size} segments")
            return taken
        }
    }

    /** epoch 偏移：把 elapsedRealtime 时间戳换算成真实墙钟时间 */
    fun epochOffsetMs(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /** 丢弃缓冲（例如切换窗口时长后） */
    fun clearBuffer() {
        synchronized(audioLock) {
            ring.clear()
            raw.clear()
            skipBacklog.clear()
            pendingPreroll = null
        }
        publishStatusLocked(SystemClock.elapsedRealtime())
    }

    private fun resetCaptureState(now: Long) {
        synchronized(audioLock) {
            lastVoiceAtMs = now
            captureMode = CaptureMode.ACTIVE
            skippedFrames = 0
            totalFrames = 0
            pendingPreroll = null
            wasSpeech = false
            skipBacklog.clear()
            vadSkipDebtMs = 0L
            raw.clear()
        }
    }

    private fun captureLoop() {
        // 让系统知道这是音频相关的线程（配合 NORM_PRIORITY 避免抢占大核）
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }

        while (running) {
            val read = try {
                record?.read(frameShorts, 0, frameShorts.size) ?: -1
            } catch (t: Throwable) {
                Log.e(TAG, "read failed", t)
                -1
            }
            if (read > 0) {
                handleFrame(read)
            } else if (read < 0) {
                Log.w(TAG, "AudioRecord.read returned $read")
            }
        }
    }

    /** 处理一帧：门控 → （可选）VAD → 入环形缓冲 */
    private fun handleFrame(read: Int) {
        val now = SystemClock.elapsedRealtime()
        totalFrames++

        // 1) 原始音频始终进 raw 环形缓冲（RawAudioRing.write 内部复制，无别名问题）
        val rawCopy = if (read == frameShorts.size) frameShorts else frameShorts.copyOf(read)
        synchronized(audioLock) { raw.write(rawCopy, read, now) }

        // 2) 第一级门：能量门（廉价；静默期直接省掉 VAD 推理）
        val loud = gate.shouldInvokeVad(SpeechGate.analyze(frameShorts, 0, read))

        updateCaptureMode(now, loud)

        // 3) 极致省电档：静默期跳过 VAD 推理，但把音频攒起来以便回灌（不丢音）
        if (captureMode == CaptureMode.VAD_SKIP && !loud) {
            skippedFrames++
            synchronized(audioLock) {
                if (skipBacklog.size >= MAX_BACKLOG_FRAMES) {
                    val dropped = skipBacklog.removeFirst()
                    // 永久丢弃的帧必须补偿，否则 VAD 时间戳会系统性偏早
                    vadSkipDebtMs += AudioSpec.samplesToMs(dropped.size)
                }
                skipBacklog.addLast(rawCopy.copyOf(read))
            }
            publishThrottled(now)
            return
        }

        val engine = vad ?: return

        // 4) 从 VAD_SKIP 唤醒：先回灌攒下的帧，再喂当前帧（时间戳映射保持连续）
        val backlog = synchronized(audioLock) {
            if (skipBacklog.isEmpty()) emptyList() else skipBacklog.toList().also { skipBacklog.clear() }
        }
        val closed = ArrayList<ClosedSegment>(2)
        synchronized(vadLock) {
            if (vadSkipDebtMs > 0) {
                engine.skipMs(vadSkipDebtMs)
                vadSkipDebtMs = 0
            }
            for (frame in backlog) {
                if (frame.size == AudioSpec.FRAME_SAMPLES) {
                    closed += engine.accept(PcmUtils.shortsToFloats(frame), now)
                }
            }
            for (i in 0 until read) frameFloats[i] = frameShorts[i] / 32768.0f
            if (read == AudioSpec.FRAME_SAMPLES) {
                closed += engine.accept(frameFloats, now)
            }
            speechDetected = engine.isSpeechDetected()
        }

        // 5) 人声起点 → 抓句首预滚（B2）
        if (speechDetected && !wasSpeech) {
            synchronized(audioLock) { pendingPreroll = raw.readLast(RawAudioRing.PREROLL_MS) }
        }
        wasSpeech = speechDetected
        if (speechDetected) lastVoiceAtMs = now

        if (closed.isNotEmpty()) commitSegments(closed, now)
        publishThrottled(now)
    }

    /** 闭合片段 → 补句首预滚 → 第二级门（非人声过滤）→ 入环形缓冲 */
    private fun commitSegments(closed: List<ClosedSegment>, now: Long) {
        if (closed.isEmpty()) return
        synchronized(audioLock) {
            // 预滚只补给「本次第一个」片段（它就是那次起音对应的段）
            val preroll = pendingPreroll
            pendingPreroll = null
            val usablePreroll = if (preroll != null && preroll.isNotEmpty()) preroll else null
            var prerollUsed = false

            for (segment in closed) {
                var pcm = PcmUtils.floatsToShorts(segment.samples)
                var startMs = segment.startMs
                if (!prerollUsed && usablePreroll != null) {
                    val merged = ShortArray(usablePreroll.size + pcm.size)
                    System.arraycopy(usablePreroll, 0, merged, 0, usablePreroll.size)
                    System.arraycopy(pcm, 0, merged, usablePreroll.size, pcm.size)
                    pcm = merged
                    startMs -= AudioSpec.samplesToMs(usablePreroll.size)
                    prerollUsed = true
                }
                if (gate.looksLikeNonSpeech(SpeechGate.analyzeSegment(pcm))) {
                    Log.d(TAG, "segment rejected as non-speech (${pcm.size} samples)")
                    continue
                }
                ring.add(SpeechRingBuffer.Segment(startMs = startMs, endMs = segment.endMs, pcm = pcm))
            }
            ring.trim(now, windowMs)
        }
    }

    /** 静默期状态机：ACTIVE → COOLDOWN → VAD_SKIP（仅极致省电档） */
    private fun updateCaptureMode(now: Long, loud: Boolean) {
        if (loud) {
            captureMode = CaptureMode.ACTIVE
            lastVoiceAtMs = now
            return
        }
        val silence = now - lastVoiceAtMs
        captureMode = when {
            silence < EchoSettings.SILENCE_COOLDOWN_MS -> CaptureMode.ACTIVE
            powerProfile == PowerProfile.SAVER && silence >= EchoSettings.SILENCE_DEEP_MS ->
                CaptureMode.VAD_SKIP
            else -> CaptureMode.COOLDOWN
        }
    }

    /** 给闭合片段补上句首预滚（B2），并把起点时间戳一并前移 */
    private fun publishThrottled(now: Long) {
        if (now - lastPublishMs < STATUS_INTERVAL_MS) return
        synchronized(audioLock) { publishStatusLocked(now) }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive && running) {
                // 静默越久，状态发布越稀（UI 不在前台时几乎无感）
                val silence = SystemClock.elapsedRealtime() - lastVoiceAtMs
                val interval = if (silence < EchoSettings.SILENCE_COOLDOWN_MS) 1_000L else 5_000L
                delay(interval)
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
            captureMode = captureMode,
            vadSkippedRatio = if (totalFrames > 0) skippedFrames.toFloat() / totalFrames else 0f,
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

        /** 回灌队列上限：约 2 秒的帧（64 帧 × 32ms） */
        private const val MAX_BACKLOG_FRAMES = 64

        const val DEFAULT_WINDOW_MS = 3 * 60 * 1000L
    }
}
