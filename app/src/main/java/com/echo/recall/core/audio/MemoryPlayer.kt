package com.echo.recall.core.audio

import android.content.Context
import android.media.MediaPlayer
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
import javax.inject.Inject
import javax.inject.Singleton

/** 记忆音频播放器（同时只播一条） */
@Singleton
class MemoryPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class UiState(
        val playing: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val path: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    fun toggle(path: String) {
        val current = player
        if (current != null && _state.value.path == path) {
            if (current.isPlaying) pause() else start()
            return
        }
        releasePlayer()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.setOnPreparedListener {
                _state.value = UiState(
                    playing = true,
                    positionMs = 0,
                    durationMs = it.duration.coerceAtLeast(0),
                    path = path,
                )
                it.start()
                startTicker()
            }
            mp.setOnCompletionListener {
                _state.value = _state.value.copy(playing = false, positionMs = 0)
                stopTicker()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                _state.value = UiState()
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (t: Throwable) {
            Log.e(TAG, "cannot play $path", t)
            _state.value = UiState()
        }
    }

    private fun start() {
        player?.let {
            it.start()
            _state.value = _state.value.copy(playing = true)
            startTicker()
        }
    }

    fun pause() {
        player?.let {
            if (it.isPlaying) it.pause()
            _state.value = _state.value.copy(playing = false)
        }
        stopTicker()
    }

    fun seekTo(ms: Int) {
        player?.let {
            it.seekTo(ms)
            _state.value = _state.value.copy(positionMs = ms)
        }
    }

    fun stop() {
        releasePlayer()
        _state.value = UiState()
    }

    private fun releasePlayer() {
        stopTicker()
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive) {
                val mp = player ?: break
                val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                _state.value = _state.value.copy(positionMs = pos)
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    companion object {
        private const val TAG = "MemoryPlayer"
    }
}
