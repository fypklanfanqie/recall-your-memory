package com.echo.recall.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.asr.AsrModelSpec
import com.echo.recall.core.asr.ModelCatalog
import com.echo.recall.core.asr.ModelInstall
import com.echo.recall.core.asr.ModelManager
import com.echo.recall.core.asr.ModelState
import com.echo.recall.core.asr.TranscriptionCoordinator
import com.echo.recall.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 模型列表页的完整视图状态 */
data class ModelScreenState(
    val device: ModelCatalog.DeviceTier = ModelCatalog.DeviceTier(0, 0, false),
    val recommendedId: String = "",
    val recommendReason: String = "",
    val selectedId: String = "",
    val installs: List<ModelInstall> = emptyList(),
    val download: ModelState = ModelState.NotReady,
    val totalUsedBytes: Long = 0L,
)

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val transcription: TranscriptionCoordinator,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<ModelState> = modelManager.state
    val progress: StateFlow<Map<String, Int>> = transcription.progress

    private val _screen = MutableStateFlow(ModelScreenState())
    val screen: StateFlow<ModelScreenState> = _screen.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val device = withContext(Dispatchers.IO) { modelManager.deviceTier() }
            val recommended = ModelCatalog.recommend(device)
            _screen.value = ModelScreenState(
                device = device,
                recommendedId = recommended.id,
                recommendReason = ModelCatalog.recommendReason(device, recommended),
                selectedId = modelManager.selectedId.value,
                installs = withContext(Dispatchers.IO) { modelManager.installs() },
                download = modelManager.state.value,
                totalUsedBytes = withContext(Dispatchers.IO) { modelManager.usedBytes() },
            )
            modelManager.refreshState()
        }
    }

    /** 选择某个模型作为当前转写引擎（无需下载即可先选，未装会提示下载） */
    fun select(spec: AsrModelSpec) {
        viewModelScope.launch {
            modelManager.select(spec)
            transcription.onModelChanged()
            settingsRepository.setAsrModelId(spec.id)
            _messages.tryEmit("已切换到「${spec.displayName}」")
            refresh()
        }
    }

    fun download(spec: AsrModelSpec) {
        viewModelScope.launch {
            val result = modelManager.download(spec)
            if (result.isSuccess) {
                // 下载完直接切到它，用户意图很明确
                modelManager.select(spec)
                transcription.onModelChanged()
                settingsRepository.setAsrModelId(spec.id)
                _messages.tryEmit("「${spec.displayName}」已就绪，正在补转写待处理记忆…")
                transcription.transcribePending()
            } else {
                _messages.tryEmit(result.exceptionOrNull()?.message ?: "下载失败")
            }
            refresh()
        }
    }

    fun delete(spec: AsrModelSpec) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { modelManager.delete(spec) }
            transcription.onModelChanged()
            _messages.tryEmit("已删除「${spec.displayName}」")
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { modelManager.deleteAll() }
            transcription.onModelChanged()
            _messages.tryEmit("已删除全部本地模型")
            refresh()
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            val result = modelManager.importModel(uri)
            _messages.tryEmit(result.fold({ "模型导入成功" }, { it.message ?: "导入失败" }))
            if (result.isSuccess) transcription.transcribePending()
            refresh()
        }
    }

    fun importTokens(uri: Uri) {
        viewModelScope.launch {
            val result = modelManager.importTokens(uri)
            _messages.tryEmit(result.fold({ "tokens.txt 导入成功" }, { it.message ?: "导入失败" }))
            refresh()
        }
    }

    fun transcribePending() {
        viewModelScope.launch {
            _messages.tryEmit("开始补转写…")
            transcription.transcribePending()
        }
    }
}
