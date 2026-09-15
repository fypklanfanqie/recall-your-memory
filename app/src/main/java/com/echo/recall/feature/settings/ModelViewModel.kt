package com.echo.recall.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.asr.ModelManager
import com.echo.recall.core.asr.ModelState
import com.echo.recall.core.asr.TranscriptionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val transcription: TranscriptionCoordinator,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager.state
    val progress: StateFlow<Map<String, Int>> = transcription.progress

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        modelManager.refreshState()
    }

    fun usedBytes(): Long = modelManager.usedBytes()

    fun download() {
        viewModelScope.launch {
            val result = modelManager.download()
            if (result.isSuccess) {
                _messages.tryEmit("模型已就绪，正在补转写待处理记忆…")
                transcription.transcribePending()
            } else {
                _messages.tryEmit(result.exceptionOrNull()?.message ?: "下载失败")
            }
        }
    }

    fun delete() {
        modelManager.deleteAll()
        _messages.tryEmit("已删除本地模型")
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val result = modelManager.importModel(uri)
            _messages.tryEmit(result.fold({ "模型导入成功" }, { it.message ?: "导入失败" }))
            if (result.isSuccess) transcription.transcribePending()
        }
    }

    fun importTokens(uri: Uri) {
        viewModelScope.launch {
            val result = modelManager.importTokens(uri)
            _messages.tryEmit(result.fold({ "tokens.txt 导入成功" }, { it.message ?: "导入失败" }))
        }
    }

    fun transcribePending() {
        viewModelScope.launch {
            _messages.tryEmit("开始补转写…")
            transcription.transcribePending()
        }
    }

    suspend fun usedBytesAsync(): Long = withContext(Dispatchers.IO) { modelManager.usedBytes() }
}
