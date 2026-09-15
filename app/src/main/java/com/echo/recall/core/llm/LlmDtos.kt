package com.echo.recall.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 注意：SSE 流式响应的首个 chunk 常常只有 role、没有 content，
 * 因此两个字段都必须有默认值，否则反序列化会抛 MissingFieldException。
 */
@Serializable
data class ChatMessage(
    val role: String = "",
    val content: String = "",
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    val delta: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
data class ChatStreamChunk(
    val choices: List<ChatChoice> = emptyList(),
)

@Serializable
data class ModelListResponse(
    val data: List<ModelInfo> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("owned_by") val ownedBy: String? = null,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)

/** 流式输出事件 */
sealed interface LlmDelta {
    data class Text(val value: String) : LlmDelta
    data class Failure(val message: String) : LlmDelta
    data object Done : LlmDelta
}
