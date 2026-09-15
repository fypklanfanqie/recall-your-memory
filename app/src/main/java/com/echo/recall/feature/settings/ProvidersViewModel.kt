package com.echo.recall.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.llm.LlmProvider
import com.echo.recall.core.llm.LlmService
import com.echo.recall.core.llm.ProviderCatalog
import com.echo.recall.core.llm.ProviderConfig
import com.echo.recall.core.llm.ProviderStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 单个供应商在界面上的编辑状态 */
data class ProviderUiState(
    val provider: LlmProvider,
    val config: ProviderConfig? = null,
    val expanded: Boolean = false,
    val keyInput: String = "",
    val keyEdited: Boolean = false,
    val verifying: Boolean = false,
    val error: String? = null,
    val fetchedModels: List<String> = emptyList(),
) {
    val configured: Boolean get() = config?.hasKey() == true
    val model: String? get() = config?.model
    val modelsToShow: List<String>
        get() = (fetchedModels.ifEmpty { config?.models ?: emptyList() })
            .ifEmpty { provider.suggestedModels }
}

data class CustomProviderForm(
    val visible: Boolean = false,
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
)

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val store: ProviderStore,
    private val llmService: LlmService,
) : ViewModel() {

    private val _states = MutableStateFlow(
        ProviderCatalog.builtIn.map { ProviderUiState(provider = it) },
    )
    val states: StateFlow<List<ProviderUiState>> = _states.asStateFlow()

    val activeId: StateFlow<String?> = store.activeId

    private val _custom = MutableStateFlow(CustomProviderForm())
    val custom: StateFlow<CustomProviderForm> = _custom.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            store.configs.collect { configs ->
                _states.update { current ->
                    current.map { state ->
                        state.copy(config = configs.firstOrNull { it.providerId == state.provider.id })
                    }
                }
            }
        }
    }

    fun toggle(providerId: String) {
        _states.update { list ->
            list.map { if (it.provider.id == providerId) it.copy(expanded = !it.expanded) else it }
        }
    }

    fun onKeyChange(providerId: String, value: String) {
        _states.update { list ->
            list.map {
                if (it.provider.id == providerId) it.copy(keyInput = value, keyEdited = true, error = null) else it
            }
        }
    }

    /** 验证 Key 并拉取模型列表；失败时回退到内置建议模型 */
    fun verify(providerId: String) {
        val state = _states.value.firstOrNull { it.provider.id == providerId } ?: return
        val key = state.keyInput.takeIf { it.isNotBlank() }
            ?: store.apiKey(providerId)
            ?: run {
                _states.update { it.map { s -> if (s.provider.id == providerId) s.copy(error = "请先填入 API Key") else s } }
                return
            }

        _states.update { list ->
            list.map { if (it.provider.id == providerId) it.copy(verifying = true, error = null) else it }
        }

        viewModelScope.launch {
            val config = ProviderConfig(
                providerId = providerId,
                displayName = state.provider.displayName,
                baseUrl = state.provider.baseUrl,
                model = state.model,
            )
            val result = llmService.verifyAndListModels(config, key)
            result.fold(
                onSuccess = { models ->
                    store.upsert(
                        providerId = providerId,
                        displayName = state.provider.displayName,
                        baseUrl = state.provider.baseUrl,
                        apiKey = key,
                        model = state.model ?: models.firstOrNull(),
                        models = models,
                    )
                    _states.update { list ->
                        list.map {
                            if (it.provider.id == providerId) {
                                it.copy(
                                    verifying = false,
                                    error = null,
                                    keyInput = "",
                                    keyEdited = false,
                                    fetchedModels = models,
                                )
                            } else {
                                it
                            }
                        }
                    }
                    _messages.tryEmit("已连接，拉取到 ${models.size} 个模型")
                },
                onFailure = { error ->
                    // 拉不到列表也要能保存 Key，用内置建议模型兜底
                    store.upsert(
                        providerId = providerId,
                        displayName = state.provider.displayName,
                        baseUrl = state.provider.baseUrl,
                        apiKey = key,
                        model = state.model ?: state.provider.suggestedModels.firstOrNull(),
                        models = state.provider.suggestedModels,
                    )
                    _states.update { list ->
                        list.map {
                            if (it.provider.id == providerId) {
                                it.copy(
                                    verifying = false,
                                    keyInput = "",
                                    keyEdited = false,
                                    error = error.message ?: "验证失败",
                                )
                            } else {
                                it
                            }
                        }
                    }
                    _messages.tryEmit("已保存 Key，但模型列表拉取失败，使用内置建议模型")
                },
            )
        }
    }

    fun selectModel(providerId: String, model: String) {
        val state = _states.value.firstOrNull { it.provider.id == providerId } ?: return
        val existing = state.config
        store.upsert(
            providerId = providerId,
            displayName = state.provider.displayName,
            baseUrl = existing?.baseUrl ?: state.provider.baseUrl,
            apiKey = null,
            model = model,
            models = state.modelsToShow,
        )
        store.setActive(providerId)
        _messages.tryEmit("已切换到 ${state.provider.displayName} · $model")
    }

    fun setActive(providerId: String) {
        store.setActive(providerId)
        _messages.tryEmit("已设为当前供应商")
    }

    fun remove(providerId: String) {
        store.remove(providerId)
        _messages.tryEmit("已删除该供应商配置")
    }

    // ---- 自定义供应商 ----

    fun showCustomForm() {
        _custom.value = CustomProviderForm(visible = true)
    }

    fun hideCustomForm() {
        _custom.update { it.copy(visible = false) }
    }

    fun onCustomChange(
        name: String? = null,
        baseUrl: String? = null,
        apiKey: String? = null,
        model: String? = null,
    ) {
        _custom.update {
            it.copy(
                name = name ?: it.name,
                baseUrl = baseUrl ?: it.baseUrl,
                apiKey = apiKey ?: it.apiKey,
                model = model ?: it.model,
            )
        }
    }

    fun saveCustom() {
        val form = _custom.value
        if (form.name.isBlank() || form.baseUrl.isBlank() || form.apiKey.isBlank()) {
            _messages.tryEmit("名称、Base URL、API Key 都要填")
            return
        }
        val id = "custom:" + form.name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
        store.upsert(
            providerId = id,
            displayName = form.name.trim(),
            baseUrl = form.baseUrl.trim(),
            apiKey = form.apiKey.trim(),
            model = form.model.trim().takeIf { it.isNotBlank() },
            custom = true,
        )
        store.setActive(id)
        _custom.value = CustomProviderForm()
        _messages.tryEmit("自定义供应商已保存并设为当前")

        // 顺手尝试拉取模型列表
        viewModelScope.launch {
            val config = store.config(id) ?: return@launch
            llmService.verifyAndListModels(config, form.apiKey.trim()).onSuccess { models ->
                store.upsert(
                    providerId = id,
                    displayName = form.name.trim(),
                    baseUrl = form.baseUrl.trim(),
                    apiKey = null,
                    model = config.model ?: models.firstOrNull(),
                    models = models,
                    custom = true,
                )
                _messages.tryEmit("拉取到 ${models.size} 个模型")
            }
        }
    }

    fun customConfigs(): List<ProviderConfig> = store.configs.value.filter { it.custom }
}
