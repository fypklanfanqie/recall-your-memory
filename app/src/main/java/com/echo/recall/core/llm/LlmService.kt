package com.echo.recall.core.llm

import android.util.Log
import com.echo.recall.core.util.TodoExtraction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 高层 AI 能力：记忆总结 / 追问 / 备忘录总结 / 润色。
 *
 * 隐私：只把**文字**发给云端，音频永不外传。
 */
@Singleton
class LlmService @Inject constructor(
    private val store: ProviderStore,
    private val client: OpenAiCompatClient,
) {

    fun usableConfig(): ProviderConfig? = store.usableConfig()

    fun notConfiguredMessage(): String = "请先在 设置 → AI 供应商 填入 API Key 并选择模型"

    suspend fun summarizeMemory(transcript: String, timeRange: String): Result<String> {
        val config = store.usableConfig()
            ?: return Result.failure(IllegalStateException(notConfiguredMessage()))
        val apiKey = store.apiKey(config.providerId)
            ?: return Result.failure(IllegalStateException("该供应商缺少 API Key"))
        if (transcript.isBlank()) return Result.failure(IllegalStateException("这条记忆还没有转写文字"))
        return client.chat(config, apiKey, Prompts.memorySummary(transcript, timeRange))
    }

    /**
     * 追问：以该条记忆的转写为上下文做流式对话。
     * [history] 为此前的问答往返（不含本轮问题）。
     */
    fun streamAskMemory(
        transcript: String,
        timeRange: String,
        history: List<ChatMessage>,
        question: String,
    ): Flow<LlmDelta> = flow {
        val config = store.usableConfig()
        if (config == null) {
            emit(LlmDelta.Failure(notConfiguredMessage()))
            return@flow
        }
        val apiKey = store.apiKey(config.providerId)
        if (apiKey.isNullOrBlank()) {
            emit(LlmDelta.Failure("该供应商缺少 API Key"))
            return@flow
        }
        if (transcript.isBlank()) {
            emit(LlmDelta.Failure("这条记忆还没有转写文字，无法追问"))
            return@flow
        }
        client.streamChat(
            config = config,
            apiKey = apiKey,
            messages = Prompts.memoryAsk(transcript, timeRange, history, question),
        ).collect { emit(it) }
    }

    suspend fun summarizeNote(content: String): Result<String> {
        val config = store.usableConfig()
            ?: return Result.failure(IllegalStateException(notConfiguredMessage()))
        val apiKey = store.apiKey(config.providerId)
            ?: return Result.failure(IllegalStateException("该供应商缺少 API Key"))
        if (content.isBlank()) return Result.failure(IllegalStateException("备忘录是空的"))
        return client.chat(config, apiKey, Prompts.noteSummary(content))
    }

    suspend fun polishNote(content: String): Result<String> {
        val config = store.usableConfig()
            ?: return Result.failure(IllegalStateException(notConfiguredMessage()))
        val apiKey = store.apiKey(config.providerId)
            ?: return Result.failure(IllegalStateException("该供应商缺少 API Key"))
        if (content.isBlank()) return Result.failure(IllegalStateException("备忘录是空的"))
        return client.chat(config, apiKey, Prompts.notePolish(content))
    }

    /** 从记忆转写里提取待办（结构化抽取，直接写入待办列表） */
    suspend fun extractTodos(transcript: String, timeRange: String): Result<List<String>> {
        val config = store.usableConfig()
            ?: return Result.failure(IllegalStateException(notConfiguredMessage()))
        val apiKey = store.apiKey(config.providerId)
            ?: return Result.failure(IllegalStateException("该供应商缺少 API Key"))
        if (transcript.isBlank()) return Result.failure(IllegalStateException("这条记忆还没有转写文字"))

        return client.chat(config, apiKey, Prompts.extractTodos(transcript, timeRange))
            .map { raw -> TodoExtraction.parse(raw) }
            .mapCatching { items ->
                if (items.isEmpty()) throw IllegalStateException("这段记忆里没有可提取的待办") else items
            }
    }

    /** 验证 Key 并拉取模型列表 */
    suspend fun verifyAndListModels(config: ProviderConfig, apiKey: String): Result<List<String>> {
        Log.i(TAG, "verify provider=${config.providerId} url=${config.baseUrl}")
        return client.listModels(config, apiKey)
    }

    companion object {
        private const val TAG = "LlmService"
    }
}
