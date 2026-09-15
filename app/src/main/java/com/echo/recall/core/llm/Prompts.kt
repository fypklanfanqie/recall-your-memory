package com.echo.recall.core.llm

/**
 * 提示词模板（中文）。
 * 原则：只依据转写文本作答、不编造、不确定就明说。
 */
object Prompts {

    private const val MEMORY_SUMMARY_SYSTEM = """你是「回声」的记忆整理助手。
用户给你的是一段环境录音的语音转写：可能口语化、有识别错别字、缺上下文、多人对话混在一起。
请输出简洁的中文摘要，严格按以下结构：
一句话：发生了什么（不超过 30 字）
要点：
- 3~5 条，每条不超过 20 字
提到的事项：
- 逐条列出出现的时间、地点、人名、金额、约定或待办，格式「类型：内容」；没有就写「无」
存疑：
- 转写明显可能出错或无法确定的地方，一句话说明；没有就写「无」
要求：绝不编造转写里没有的信息；不确定就写「未提及」。总长度控制在 250 字以内。"""

    private const val MEMORY_ASK_SYSTEM = """你是「回声」的记忆问答助手。
你只能依据下面提供的转写内容回答问题。规则：
1. 若转写里没有相关信息，直接回答「这段记录里没有提到」，不要猜测或补充常识。
2. 回答简短、口语化，涉及原文时用引号直接引用原话。
3. 涉及时间、金额、人名时照抄原文数字与称呼。"""

    private const val NOTE_SUMMARY_SYSTEM = """请用 3~5 条要点总结下面的备忘录。
只保留原文信息，不新增内容；若有需要跟进的事项，用「待办：」标出。输出仅包含要点列表。"""

    private const val NOTE_POLISH_SYSTEM = """你是中文写作助手。请润色下面的备忘录：
1. 保留原意与全部事实信息，不新增、不删减信息；
2. 修正语病、冗余与过于口语化的表达，让文字干净利落；
3. 保持原有段落划分；
4. 只输出润色后的正文，不要任何解释或前后缀。"""

    private const val TODO_EXTRACT_SYSTEM = """从下面这段环境录音的转写里，提取「需要有人去做的事」。
规则：
1. 每行只输出一条，不要编号、不要列表符号、不要任何解释或前后缀；
2. 尽量保留原文措辞，以及其中提到的时间、地点、人名、数量；
3. 只提取明确的待办、约定、请求或承诺；闲聊、陈述、感想不要提取；
4. 没有可提取的内容时，只输出两个字：无
5. 最多 10 条。"""

    fun extractTodos(transcript: String, timeRange: String): List<ChatMessage> = listOf(
        ChatMessage(role = "system", content = TODO_EXTRACT_SYSTEM),
        ChatMessage(role = "user", content = "时间范围：$timeRange\n\n转写内容：\n$transcript"),
    )

    fun memorySummary(transcript: String, timeRange: String): List<ChatMessage> = listOf(
        ChatMessage(role = "system", content = MEMORY_SUMMARY_SYSTEM),
        ChatMessage(
            role = "user",
            content = "时间范围：$timeRange\n\n转写内容：\n$transcript",
        ),
    )

    fun memoryAsk(
        transcript: String,
        timeRange: String,
        history: List<ChatMessage>,
        question: String,
    ): List<ChatMessage> = buildList {
        add(ChatMessage(role = "system", content = MEMORY_ASK_SYSTEM))
        add(
            ChatMessage(
                role = "user",
                content = "以下是这段记忆的转写（时间范围：$timeRange）：\n$transcript",
            ),
        )
        add(ChatMessage(role = "assistant", content = "好的，我已读完这段转写，请提问。"))
        addAll(history)
        add(ChatMessage(role = "user", content = question))
    }

    fun noteSummary(content: String): List<ChatMessage> = listOf(
        ChatMessage(role = "system", content = NOTE_SUMMARY_SYSTEM),
        ChatMessage(role = "user", content = content),
    )

    fun notePolish(content: String): List<ChatMessage> = listOf(
        ChatMessage(role = "system", content = NOTE_POLISH_SYSTEM),
        ChatMessage(role = "user", content = content),
    )
}
