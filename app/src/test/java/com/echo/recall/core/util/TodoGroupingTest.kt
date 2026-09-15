package com.echo.recall.core.util

import com.echo.recall.core.data.db.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoGroupingTest {

    private fun todo(
        id: String,
        done: Boolean = false,
        pinned: Boolean = false,
        createdAt: Long = 0L,
    ) = TodoEntity(
        id = id,
        title = "待办 $id",
        done = done,
        pinned = pinned,
        createdAt = createdAt,
    )

    @Test
    fun `groups pinned first then active then completed`() {
        val list = listOf(
            todo("pinned", pinned = true, createdAt = 3),
            todo("active", createdAt = 1),
            todo("done", done = true, createdAt = 2),
        )

        val sections = TodoGrouping.group(list)

        assertEquals(
            listOf(TodoGroupKind.PINNED, TodoGroupKind.ACTIVE, TodoGroupKind.COMPLETED),
            sections.map { it.kind },
        )
        assertEquals(listOf("pinned"), sections[0].todos.map { it.id })
        assertEquals(listOf("active"), sections[1].todos.map { it.id })
        assertEquals(listOf("done"), sections[2].todos.map { it.id })
    }

    @Test
    fun `pinned section keeps done items deliberately`() {
        val sections = TodoGrouping.group(
            listOf(todo("p1", pinned = true), todo("p2", pinned = true, done = true)),
        )

        assertEquals(1, sections.size)
        assertEquals(TodoGroupKind.PINNED, sections[0].kind)
        assertEquals(listOf("p1", "p2"), sections[0].todos.map { it.id })
    }

    @Test
    fun `empty buckets are dropped`() {
        assertTrue(TodoGrouping.group(emptyList()).isEmpty())

        val onlyActive = TodoGrouping.group(listOf(todo("a")))
        assertEquals(1, onlyActive.size)
        assertEquals(TodoGroupKind.ACTIVE, onlyActive[0].kind)

        val onlyDone = TodoGrouping.group(listOf(todo("d", done = true)))
        assertEquals(1, onlyDone.size)
        assertEquals(TodoGroupKind.COMPLETED, onlyDone[0].kind)
    }

    @Test
    fun `group preserves the incoming order inside each bucket`() {
        val list = listOf(
            todo("a", createdAt = 30),
            todo("b", createdAt = 20),
            todo("c", createdAt = 10),
        )

        assertEquals(listOf("a", "b", "c"), TodoGrouping.group(list)[0].todos.map { it.id })
    }

    @Test
    fun `split and counts agree`() {
        val list = listOf(
            todo("p", pinned = true),
            todo("a1"),
            todo("a2"),
            todo("d", done = true),
        )

        val (active, completed) = TodoGrouping.split(list)
        assertEquals(listOf("a1", "a2"), active.map { it.id })
        assertEquals(listOf("d"), completed.map { it.id })

        val counts = TodoGrouping.counts(list)
        assertEquals(1, counts.pinned)
        assertEquals(2, counts.active)
        assertEquals(1, counts.completed)
        assertEquals(4, counts.total)
    }

    @Test
    fun `completed label carries the count`() {
        assertEquals("已完成 (3)", TodoGrouping.completedLabel(3))
    }
}
