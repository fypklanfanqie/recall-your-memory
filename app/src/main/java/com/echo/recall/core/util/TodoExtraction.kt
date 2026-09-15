package com.echo.recall.core.util

/**
 * 把 LLM 返回的待办清单解析成纯文本条目（纯逻辑，可单测）。
 *
 * 模型输出常见形态：`- 买牛奶`、`1. 明天九点开会`、`* 给妈妈打电话`、
 * 也可能夹带解释性句子或「无」。这里做保守清洗：
 * 去掉列表符号与序号、去引号、丢掉明显非条目的行、去重、限量。
 */
object TodoExtraction {

    /** 单次最多提取的条数，避免把整段转写都变成待办 */
    const val MAX_ITEMS = 10

    private val bulletPrefix = Regex("^\\s*(?:[-*•·—–]|\\d+[.、)）]|第[一二三四五六七八九十]+[、.)]?)\\s*")
    private val wrapperQuotes =
        Regex("^[\"'“”‘’《》「」『』\\[\\]()（）]+|[\"'“”‘’《》「」『』\\[\\]()（）]+$")
    private val nonItem = Regex("^(无|没有|暂无|none|n/?a|以上|总结|说明[:：]?).*$", RegexOption.IGNORE_CASE)

    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<String>()
        for (line in raw.lines()) {
            val cleaned = clean(line) ?: continue
            seen += cleaned
            if (seen.size >= MAX_ITEMS) break
        }
        return seen.toList()
    }

    /** 单行清洗；不是有效条目时返回 null */
    fun clean(line: String): String? {
        var text = line.trim()
        if (text.isEmpty()) return null
        text = text.replace(bulletPrefix, "")
        text = text.replace(wrapperQuotes, "").trim()
        if (text.length < 2) return null
        if (nonItem.matches(text)) return null
        // 去掉句末的总结性口吻，保留原文措辞
        text = text.removeSuffix("。").removeSuffix(".").trim()
        return text.takeIf { it.length >= 2 }
    }
}
