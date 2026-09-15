package com.echo.recall.core.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI 兼容客户端：8 家国内厂商 + 自定义供应商共用。
 * - listModels: GET {base}/models（失败时由调用方回退到内置建议列表）
 * - chat: 非流式（总结/润色）
 * - streamChat: SSE 流式（追问对话）
 */
@Singleton
class OpenAiCompatClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun listModels(config: ProviderConfig, apiKey: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${config.baseUrl.trimEnd('/')}/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException(describeError(response.code, body)))
                    }
                    val parsed = json.decodeFromString<ModelListResponse>(body)
                    val ids = parsed.data.map { it.id }.filter { it.isNotBlank() }.sorted()
                    if (ids.isEmpty()) Result.failure(IllegalStateException("该供应商没有返回可用模型列表"))
                    else Result.success(ids)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "listModels failed", t)
                Result.failure(t)
            }
        }

    /** 非流式对话 */
    suspend fun chat(
        config: ProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        temperature: Double = 0.3,
        maxTokens: Int? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = config.model ?: return@withContext Result.failure(IllegalStateException("未选择模型"))
        try {
            val payload = json.encodeToString(
                ChatRequest(model = model, messages = messages, stream = false, temperature = temperature, maxTokens = maxTokens),
            )
            val request = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(describeError(response.code, body)))
                }
                val parsed = runCatching { json.decodeFromString<ChatResponse>(body) }.getOrNull()
                val text = parsed?.choices?.firstOrNull()?.message?.content
                when {
                    !text.isNullOrBlank() -> Result.success(text.trim())
                    parsed?.error?.message != null -> Result.failure(IllegalStateException(parsed.error.message))
                    else -> Result.failure(IllegalStateException("模型返回为空"))
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "chat failed", t)
            Result.failure(t)
        }
    }

    /** 流式对话（SSE） */
    fun streamChat(
        config: ProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>,
        temperature: Double = 0.4,
    ): Flow<LlmDelta> = flow {
        val model = config.model
        if (model.isNullOrBlank()) {
            emit(LlmDelta.Failure("未选择模型"))
            return@flow
        }
        val payload = json.encodeToString(
            ChatRequest(model = model, messages = messages, stream = true, temperature = temperature),
        )
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    emit(LlmDelta.Failure(describeError(response.code, body)))
                    return@flow
                }
                val source = response.body?.source() ?: run {
                    emit(LlmDelta.Failure("响应为空"))
                    return@flow
                }
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payloadLine = line.removePrefix("data:").trim()
                    if (payloadLine.isEmpty()) continue
                    if (payloadLine == "[DONE]") break
                    val chunk = runCatching { json.decodeFromString<ChatStreamChunk>(payloadLine) }.getOrNull()
                        ?: continue
                    val delta = chunk.choices.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) emit(LlmDelta.Text(delta))
                }
                emit(LlmDelta.Done)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "streamChat failed", t)
            emit(LlmDelta.Failure(t.message ?: "网络错误"))
        }
    }.flowOn(Dispatchers.IO)

    private fun describeError(code: Int, body: String): String {
        val parsed = runCatching { json.decodeFromString<ChatResponse>(body) }.getOrNull()
        val apiMessage = parsed?.error?.message
        val raw = body.replace(Regex("\\s+"), " ").trim().take(160)
        return when {
            code == 401 -> "API Key 无效或已过期（401）"
            code == 403 -> "无权访问该模型（403）"
            code == 404 -> "接口地址或模型名不存在（404）：${apiMessage ?: raw}"
            code == 429 -> "请求过于频繁或额度不足（429）"
            apiMessage != null -> "HTTP $code：$apiMessage"
            else -> "HTTP $code：$raw"
        }
    }

    companion object {
        private const val TAG = "OpenAiCompat"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
