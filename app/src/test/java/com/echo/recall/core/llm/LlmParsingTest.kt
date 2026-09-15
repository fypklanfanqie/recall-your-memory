package com.echo.recall.core.llm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmParsingTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `parses an openai compatible model list`() {
        val raw = """
            {"object":"list","data":[
              {"id":"deepseek-chat","object":"model","owned_by":"deepseek"},
              {"id":"deepseek-reasoner","object":"model","owned_by":"deepseek"}
            ]}
        """.trimIndent()

        val parsed = json.decodeFromString<ModelListResponse>(raw)

        assertEquals(2, parsed.data.size)
        assertEquals("deepseek-chat", parsed.data[0].id)
        assertTrue(parsed.data.map { it.id }.contains("deepseek-reasoner"))
    }

    @Test
    fun `parses a non streaming chat completion`() {
        val raw = """
            {"id":"x","model":"deepseek-chat","choices":[
              {"index":0,"message":{"role":"assistant","content":"一句话：在讨论周末安排。"},"finish_reason":"stop"}
            ]}
        """.trimIndent()

        val parsed = json.decodeFromString<ChatResponse>(raw)

        assertEquals("一句话：在讨论周末安排。", parsed.choices.first().message?.content)
    }

    @Test
    fun `parses provider error payloads`() {
        val raw = """{"error":{"message":"Incorrect API key provided","type":"incorrect_api_key_error"}}"""

        val parsed = json.decodeFromString<ChatResponse>(raw)

        assertNotNull(parsed.error)
        assertEquals("Incorrect API key provided", parsed.error?.message)
    }

    @Test
    fun `parses sse delta chunks and ignores keepalive lines`() {
        val chunk = """{"choices":[{"index":0,"delta":{"content":"你好"},"finish_reason":null}]}"""

        val parsed = json.decodeFromString<ChatStreamChunk>(chunk)

        assertEquals("你好", parsed.choices.first().delta?.content)
        // 首包只带 role、没有 content：必须解析成功且不产生可见文本
        val roleOnly = json.decodeFromString<ChatStreamChunk>(
            """{"choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}""",
        )
        assertTrue(roleOnly.choices.first().delta?.content.isNullOrEmpty())
    }

    @Test
    fun `chat request serialises snake case max tokens`() {
        val payload = json.encodeToString(
            ChatRequest(
                model = "qwen-plus",
                messages = listOf(ChatMessage("user", "hi")),
                stream = true,
                temperature = 0.3,
                maxTokens = 512,
            ),
        )

        assertTrue(payload.contains("\"max_tokens\":512"))
        assertTrue(payload.contains("\"stream\":true"))
    }

    @Test
    fun `memory summary prompt carries transcript and forbids inventing`() {
        val messages = Prompts.memorySummary("明天九点开会", "今天 14:30 – 14:35")

        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertTrue(messages[0].content.contains("绝不编造"))
        assertTrue(messages[1].content.contains("明天九点开会"))
        assertTrue(messages[1].content.contains("今天 14:30 – 14:35"))
    }

    @Test
    fun `memory ask prompt injects history before the new question`() {
        val history = listOf(
            ChatMessage("user", "谁提到了预算？"),
            ChatMessage("assistant", "没有提到预算。"),
        )
        val messages = Prompts.memoryAsk("随便聊聊", "今天 10:00", history, "那时间呢？")

        assertEquals("system", messages.first().role)
        assertEquals("那时间呢？", messages.last().content)
        assertTrue(messages.any { it.content == "没有提到预算。" })
        assertTrue(messages.first { it.role == "user" }.content.contains("随便聊聊"))
    }
}
