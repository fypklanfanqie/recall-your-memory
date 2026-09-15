package com.echo.recall.core.llm

/**
 * 内置国内大模型厂商（全部提供 OpenAI 兼容接口）。
 *
 * baseUrl 均为 chat/completions 与 models 的共同前缀，客户端会拼接：
 *   POST {baseUrl}/chat/completions
 *   GET  {baseUrl}/models
 *
 * 已实测（无 Key 时均返回标准 401，说明接口存在）：
 *   deepseek / zhipu / moonshot / dashscope / ark / hunyuan / minimax / siliconflow
 */
data class LlmProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val keyHint: String,
    val suggestedModels: List<String>,
) {
    val modelsUrl: String get() = "${baseUrl.trimEnd('/')}/models"
    val chatUrl: String get() = "${baseUrl.trimEnd('/')}/chat/completions"
}

object ProviderCatalog {

    val builtIn: List<LlmProvider> = listOf(
        LlmProvider(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            keyHint = "platform.deepseek.com → API Keys",
            suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
        ),
        LlmProvider(
            id = "zhipu",
            displayName = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            keyHint = "bigmodel.cn → API Keys",
            suggestedModels = listOf("glm-4-plus", "glm-4-air", "glm-4-flash"),
        ),
        LlmProvider(
            id = "moonshot",
            displayName = "Kimi 月之暗面",
            baseUrl = "https://api.moonshot.cn/v1",
            keyHint = "platform.moonshot.cn → API Key 管理",
            suggestedModels = listOf("moonshot-v1-8k", "moonshot-v1-32k", "kimi-k2-0905-preview"),
        ),
        LlmProvider(
            id = "dashscope",
            displayName = "通义千问（阿里百炼）",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            keyHint = "bailian.console.aliyun.com → API-KEY",
            suggestedModels = listOf("qwen-plus", "qwen-max", "qwen-turbo", "qwen-long"),
        ),
        LlmProvider(
            id = "ark",
            displayName = "豆包（火山方舟）",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            keyHint = "console.volcengine.com/ark → API Key（模型可填 doubao-… 或接入点 ep-…）",
            suggestedModels = listOf("doubao-seed-1-6-250615", "doubao-1-5-pro-32k-250115"),
        ),
        LlmProvider(
            id = "hunyuan",
            displayName = "腾讯混元",
            baseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
            keyHint = "console.cloud.tencent.com/hunyuan → API Key",
            suggestedModels = listOf("hunyuan-turbos-latest", "hunyuan-large", "hunyuan-lite"),
        ),
        LlmProvider(
            id = "minimax",
            displayName = "MiniMax",
            baseUrl = "https://api.minimaxi.com/v1",
            keyHint = "platform.minimaxi.com → 接口密钥",
            suggestedModels = listOf("MiniMax-Text-01", "abab6.5s-chat"),
        ),
        LlmProvider(
            id = "siliconflow",
            displayName = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1",
            keyHint = "cloud.siliconflow.cn → API 密钥（聚合多家开源模型）",
            suggestedModels = listOf(
                "deepseek-ai/DeepSeek-V3",
                "Qwen/Qwen2.5-7B-Instruct",
                "Qwen/Qwen2.5-72B-Instruct",
            ),
        ),
    )

    fun find(id: String): LlmProvider? = builtIn.firstOrNull { it.id == id }
}
