package com.echo.recall.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DueDateTest {

    /** 固定时区，避免测试跑在别的时区上时结果漂移 */
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 2025-06-10 10:00（东八区） */
    private val now: Long = LocalDateTime.of(2025, 6, 10, 10, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun label(epochMs: Long): String = DueDate.label(epochMs, now, zone)

    @Test
    fun `today end of day is 23 59 the same date`() {
        val due = DueDate.todayEndOfDay(now, zone)

        assertEquals(at(2025, 6, 10, 23, 59), due)
        assertEquals("今天 23:59", label(due))
    }

    @Test
    fun `shift forward keeps the time of day and rolls the date`() {
        val tomorrow = DueDate.shiftDays(now, 1, now, zone)
        assertEquals(at(2025, 6, 11, 10, 0), tomorrow)
        assertEquals("明天 10:00", label(tomorrow))

        val dayAfter = DueDate.shiftDays(tomorrow, 1, now, zone)
        assertEquals("6月12日 10:00", label(dayAfter))
    }

    @Test
    fun `shift backward yields yesterday`() {
        assertEquals("昨天 10:00", label(DueDate.shiftDays(now, -1, now, zone)))
    }

    @Test
    fun `shift crosses month and year boundaries`() {
        val monthEnd = at(2025, 1, 31, 9, 0)
        assertEquals(
            at(2025, 2, 1, 9, 0),
            DueDate.shiftDays(monthEnd, 1, monthEnd, zone),
        )

        val yearEnd = at(2025, 12, 31, 9, 0)
        assertEquals(
            at(2026, 1, 1, 9, 0),
            DueDate.shiftDays(yearEnd, 1, yearEnd, zone),
        )
    }

    @Test
    fun `shift from null starts at end of today and keeps 23 59`() {
        assertEquals(at(2025, 6, 11, 23, 59), DueDate.shiftDays(null, 1, now, zone))
    }

    @Test
    fun `shift minutes keeps the hour for whole hour steps`() {
        assertEquals(at(2025, 6, 10, 11, 0), DueDate.shiftMinutes(now, 60, zone))
        assertEquals(at(2025, 6, 10, 14, 30), DueDate.shiftMinutes(now, 270, zone))
        assertEquals(at(2025, 6, 10, 8, 30), DueDate.shiftMinutes(now, -90, zone))
    }

    @Test
    fun `labels cover today tomorrow and yesterday`() {
        assertEquals("今天 10:00", label(now))
        assertEquals("明天 10:00", label(DueDate.shiftDays(now, 1, now, zone)))
        assertEquals("昨天 10:00", label(DueDate.shiftDays(now, -1, now, zone)))
    }

    @Test
    fun `labels include the year only for other years`() {
        assertEquals("9月1日 08:00", label(at(2025, 9, 1, 8, 0)))
        assertEquals("2024年9月1日 08:00", label(at(2024, 9, 1, 8, 0)))
    }

    @Test
    fun `overdue and today checks`() {
        assertTrue(DueDate.isOverdue(now - DueDate.MINUTE_MS, now))
        assertFalse(DueDate.isOverdue(now, now))
        assertFalse(DueDate.isOverdue(now + DueDate.MINUTE_MS, now))

        assertTrue(DueDate.isToday(at(2025, 6, 10, 23, 59), now, zone))
        assertFalse(DueDate.isToday(at(2025, 6, 11, 0, 0), now, zone))
    }

    @Test
    fun `daysUntil counts calendar days not 24h spans`() {
        assertEquals(0L, DueDate.daysUntil(at(2025, 6, 10, 23, 59), now, zone))
        assertEquals(1L, DueDate.daysUntil(at(2025, 6, 11, 0, 1), now, zone))
        assertEquals(-1L, DueDate.daysUntil(at(2025, 6, 9, 23, 59), now, zone))
        assertEquals(21L, DueDate.daysUntil(at(2025, 7, 1, 9, 0), now, zone))
    }
}
