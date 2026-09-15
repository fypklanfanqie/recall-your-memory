package com.echo.recall.core.util

/**
 * 备忘录的纯文本处理（不依赖 Compose / Android，可直接单测）。
 *
 * 约定：正文的第一段非空文字就是「标题」，列表用它展示；
 * 若第一段是 Markdown 标题（`# 标题`），会剥掉井号。
 */
object NoteTextUtils {

    /** 标题最多取这么多字符（超出加省略号）。 */
    const val TITLE_MAX_CHARS = 40

    /** 列表预览最多取这么多字符。 */
    const val PREVIEW_MAX_CHARS = 80

    const val UNTITLED = "无标题备忘"

    /** 正文的第一段非空行；空正文返回 null。 */
    fun firstLine(content: String): String? =
        content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()

    /** 列表标题：第一行（去掉 Markdown 井号），空正文回落为「无标题备忘」。 */
    fun titleOf(content: String): String {
        val raw = firstLine(content)?.trimStart('#')?.trim().orEmpty()
        return raw.truncate(TITLE_MAX_CHARS).ifBlank { UNTITLED }
    }

    /** 标题那一行之后的内容，列表用它做两行预览。 */
    fun bodyAfterTitle(content: String): String {
        val lines = content.lines()
        val index = lines.indexOfFirst { it.isNotBlank() }
        if (index < 0) return ""
        return lines.drop(index + 1).joinToString("\n").trim()
    }

    /** 列表预览：去掉标题行后压平空白的单行摘要。 */
    fun previewOf(content: String): String {
        val body = bodyAfterTitle(content).ifBlank { "" }
        val flat = body.replace(Regex("\\s+"), " ").trim()
        return flat.truncate(PREVIEW_MAX_CHARS)
    }

    /**
     * 本地搜索匹配：标题或正文包含关键词即可（忽略大小写）。
     * 与 NoteDao.search 的 LIKE 语义保持一致，便于单测。
     */
    fun matches(query: String, title: String, content: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return title.contains(needle, ignoreCase = true) || content.contains(needle, ignoreCase = true)
    }

    /** 纯逻辑过滤，供 VM / 测试使用（DAO 侧走 SQL LIKE）。 */
    fun filter(query: String, items: List<Pair<String, String>>): List<Pair<String, String>> =
        items.filter { matches(query, it.first, it.second) }

    private fun String.truncate(max: Int): String =
        if (length <= max) this else take(max).trimEnd() + "…"
}
