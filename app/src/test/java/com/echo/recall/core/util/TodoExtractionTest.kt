package com.echo.recall.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoExtractionTest {

    @Test
    fun `strips bullets numbering and dashes`() {
        val raw = """
            - 买牛奶
            * 给妈妈打电话
            1. 明天九点开会
            2、交房租
        """.trimIndent()

        assertEquals(
            listOf("买牛奶", "给妈妈打电话", "明天九点开会", "交房租"),
            TodoExtraction.parse(raw),
        )
    }

    @Test
    fun `drops the none marker and explanation lines`() {
        assertEquals(emptyList<String>(), TodoExtraction.parse("无"))
        assertEquals(emptyList<String>(), TodoExtraction.parse("暂无"))
        assertEquals(emptyList<String>(), TodoExtraction.parse("没有可提取的内容"))
        assertEquals(emptyList<String>(), TodoExtraction.parse("说明：以上是全部内容"))
        assertEquals(emptyList<String>(), TodoExtraction.parse(""))
        assertEquals(emptyList<String>(), TodoExtraction.parse(null))
    }

    @Test
    fun `removes wrapping quotes and trailing period`() {
        assertEquals(listOf("周五之前把报价发给老张"), TodoExtraction.parse("\"周五之前把报价发给老张。\""))
        assertEquals(listOf("记得带伞"), TodoExtraction.parse("「记得带伞」"))
    }

    @Test
    fun `deduplicates while keeping order`() {
        val raw = "买牛奶\n买牛奶\n取快递\n买牛奶"
        assertEquals(listOf("买牛奶", "取快递"), TodoExtraction.parse(raw))
    }

    @Test
    fun `caps the number of extracted items`() {
        val raw = (1..25).joinToString("\n") { "待办事项编号$it" }
        assertEquals(TodoExtraction.MAX_ITEMS, TodoExtraction.parse(raw).size)
    }

    @Test
    fun `keeps time and people wording intact`() {
        val items = TodoExtraction.parse("- 明天上午十点跟李工在会议室对需求")
        assertEquals("明天上午十点跟李工在会议室对需求", items.single())
    }

    @Test
    fun `clean rejects fragments and empty bullets`() {
        assertNull(TodoExtraction.clean(""))
        assertNull(TodoExtraction.clean("   "))
        assertNull(TodoExtraction.clean("-"))
        assertNull(TodoExtraction.clean("- 好"))
        assertNull(TodoExtraction.clean("无"))
    }

    @Test
    fun `parse handles numbered chinese lists`() {
        val raw = "第一、联系供应商\n第二、确认库存"
        val items = TodoExtraction.parse(raw)
        assertTrue(items.contains("联系供应商") || items.contains("第一、联系供应商"))
        assertEquals(2, items.size)
    }
}
