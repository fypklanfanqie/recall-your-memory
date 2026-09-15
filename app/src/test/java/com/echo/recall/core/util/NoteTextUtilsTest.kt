package com.echo.recall.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextUtilsTest {

    @Test
    fun `title comes from the first non blank line`() {
        assertEquals("买牛奶", NoteTextUtils.titleOf("\n\n买牛奶\n记得买两盒"))
        assertEquals("只有一行", NoteTextUtils.titleOf("只有一行"))
    }

    @Test
    fun `markdown heading hashes are stripped`() {
        assertEquals("会议纪要", NoteTextUtils.titleOf("## 会议纪要\n- 结论"))
    }

    @Test
    fun `blank content falls back to the untitled label`() {
        assertEquals(NoteTextUtils.UNTITLED, NoteTextUtils.titleOf(""))
        assertEquals(NoteTextUtils.UNTITLED, NoteTextUtils.titleOf("   \n  \n"))
    }

    @Test
    fun `long first lines are truncated with an ellipsis`() {
        val long = "标".repeat(120)

        val title = NoteTextUtils.titleOf(long)

        assertEquals(NoteTextUtils.TITLE_MAX_CHARS + 1, title.length)
        assertTrue(title.endsWith("…"))
        assertEquals(NoteTextUtils.TITLE_MAX_CHARS, title.dropLast(1).length)
    }

    @Test
    fun `body after title drops the title line`() {
        assertEquals("第二行\n第三行", NoteTextUtils.bodyAfterTitle("标题\n第二行\n第三行"))
        assertEquals("", NoteTextUtils.bodyAfterTitle("标题"))
    }

    @Test
    fun `preview flattens whitespace and skips the title line`() {
        val content = "标题\n\n第一段   有   很多空白\n第二段"

        val preview = NoteTextUtils.previewOf(content)

        assertEquals("第一段 有 很多空白 第二段", preview)
    }

    @Test
    fun `preview is capped`() {
        val content = "标题\n" + "内".repeat(300)

        val preview = NoteTextUtils.previewOf(content)

        assertEquals(NoteTextUtils.PREVIEW_MAX_CHARS + 1, preview.length)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun `preview is empty when there is only a title`() {
        assertEquals("", NoteTextUtils.previewOf("只有标题"))
    }

    @Test
    fun `firstLine returns null for blank content`() {
        assertNull(NoteTextUtils.firstLine(""))
        assertNull(NoteTextUtils.firstLine("\n\n"))
    }

    @Test
    fun `search matches title or content case insensitively`() {
        assertTrue(NoteTextUtils.matches("会议", "会议纪要", "讨论了排期"))
        assertTrue(NoteTextUtils.matches("排期", "会议纪要", "讨论了排期"))
        assertTrue(NoteTextUtils.matches("TODO", "todo list", "nothing"))
        assertTrue(NoteTextUtils.matches("TODO", "x", "a todo item"))
        assertFalse(NoteTextUtils.matches("出差", "会议纪要", "讨论了排期"))
    }

    @Test
    fun `blank query matches everything`() {
        assertTrue(NoteTextUtils.matches("", "任意", "任意"))
        assertTrue(NoteTextUtils.matches("   ", "任意", "任意"))
    }

    @Test
    fun `filter keeps only matching pairs`() {
        val items = listOf(
            "会议" to "讨论排期",
            "购物" to "牛奶、鸡蛋",
            "读书" to "《人月神话》这本书讲排期",
        )

        assertEquals(
            listOf("会议" to "讨论排期", "读书" to "《人月神话》这本书讲排期"),
            NoteTextUtils.filter("排期", items),
        )
        assertEquals(items, NoteTextUtils.filter("", items))
        assertEquals(emptyList<Pair<String, String>>(), NoteTextUtils.filter("不存在", items))
    }
}
