package com.echo.recall.core.llm

import android.content.Context
import android.util.Log
import com.echo.recall.core.security.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 一个供应商的本地配置（API Key 加密存放） */
@Serializable
data class ProviderConfig(
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val model: String? = null,
    val models: List<String> = emptyList(),
    val encryptedKey: String? = null,
    val custom: Boolean = false,
) {
    fun hasKey(): Boolean = !encryptedKey.isNullOrBlank()
}

@Serializable
private data class ProviderFile(
    val activeId: String? = null,
    val configs: List<ProviderConfig> = emptyList(),
)

/**
 * 供应商配置存储：filesDir/llm/providers.json（Key 字段为 Keystore 密文）。
 */
@Singleton
class ProviderStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file: File
        get() = File(context.filesDir, "llm/providers.json").apply { parentFile?.mkdirs() }

    private val _configs = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val configs: StateFlow<List<ProviderConfig>> = _configs.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    init {
        load()
    }

    fun config(providerId: String): ProviderConfig? = _configs.value.firstOrNull { it.providerId == providerId }

    fun activeConfig(): ProviderConfig? = _activeId.value?.let { id -> config(id) }

    fun apiKey(providerId: String): String? =
        config(providerId)?.encryptedKey?.let { SecretStore.decrypt(it) }

    fun setActive(providerId: String) {
        _activeId.value = providerId
        persist()
    }

    fun upsert(
        providerId: String,
        displayName: String,
        baseUrl: String,
        apiKey: String? = null,
        model: String? = null,
        models: List<String>? = null,
        custom: Boolean = false,
    ) {
        val existing = config(providerId)
        val encrypted = when {
            apiKey.isNullOrBlank() -> existing?.encryptedKey
            else -> SecretStore.encrypt(apiKey.trim()) ?: existing?.encryptedKey
        }
        val updated = ProviderConfig(
            providerId = providerId,
            displayName = displayName,
            baseUrl = baseUrl.trim().trimEnd('/'),
            model = model ?: existing?.model,
            models = models?.distinct() ?: existing?.models ?: emptyList(),
            encryptedKey = encrypted,
            custom = custom,
        )
        val list = _configs.value.toMutableList()
        val index = list.indexOfFirst { it.providerId == providerId }
        if (index >= 0) list[index] = updated else list.add(updated)
        _configs.value = list
        if (_activeId.value == null) _activeId.value = providerId
        persist()
    }

    fun remove(providerId: String) {
        _configs.value = _configs.value.filterNot { it.providerId == providerId }
        if (_activeId.value == providerId) _activeId.value = _configs.value.firstOrNull()?.providerId
        persist()
    }

    /** 已配置并选好模型的供应商（可直接用于 AI 功能） */
    fun usableConfig(): ProviderConfig? {
        val active = activeConfig()
        if (active != null && active.hasKey() && !active.model.isNullOrBlank()) return active
        return _configs.value.firstOrNull { it.hasKey() && !it.model.isNullOrBlank() }
    }

    private fun persist() {
        runCatching {
            val payload = ProviderFile(_activeId.value, _configs.value)
            file.writeText(json.encodeToString(payload))
        }.onFailure { Log.e(TAG, "persist failed", it) }
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val payload = json.decodeFromString<ProviderFile>(file.readText())
            _configs.value = payload.configs
            _activeId.value = payload.activeId
        }.onFailure { Log.e(TAG, "load failed", it) }
    }

    suspend fun reload() = withContext(Dispatchers.IO) { load() }

    companion object {
        private const val TAG = "ProviderStore"
    }
}
