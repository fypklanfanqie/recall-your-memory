package com.echo.recall.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 截止时间的小工具（纯逻辑，可单测）。
 *
 * 界面上的「+1 天 / -1 天」以及时间微调都基于这里，
 * 借 [LocalDate] 加减可以自动跨月跨年，也顺带绕开夏令时问题。
 */
object DueDate {

    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 60 * MINUTE_MS
    const val DAY_MS = 24 * HOUR_MS

    /** 今天 23:59 —— 新建待办时默认的截止时间。 */
    fun todayEndOfDay(nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long =
        atDayOffset(nowMs, 0, zone, hour = 23, minute = 59)

    /**
     * 在 [baseMs] 的基础上整日加减，保留原本的时分。
     * [baseMs] 为 null 时从今天 23:59 起算。
     */
    fun shiftDays(
        baseMs: Long?,
        days: Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val hours = baseMs?.let { localHour(it, zone) } ?: 23
        val minutes = baseMs?.let { localMinute(it, zone) } ?: 59
        val start = baseMs ?: nowMs
        return atDayOffset(start, days, zone, hours, minutes)
    }

    /** 以分钟为单位微调（秒归零）。 */
    fun shiftMinutes(
        baseMs: Long,
        minutes: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val zoned = Instant.ofEpochMilli(baseMs).atZone(zone)
            .withSecond(0)
            .withNano(0)
            .plusMinutes(minutes)
        return zoned.toInstant().toEpochMilli()
    }

    /** 「今天 23:59」/「明天 09:00」/「昨天 18:30」/「9月12日 14:00」 */
    fun label(
        dueMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val target = toLocalDate(dueMs, zone)
        val today = toLocalDate(nowMs, zone)
        val days = ChronoUnit.DAYS.between(today, target)
        val time = TimeFormat.clock(dueMs, zone)
        return when (days) {
            0L -> "今天 $time"
            1L -> "明天 $time"
            -1L -> "昨天 $time"
            else -> if (target.year == today.year) {
                "${target.monthValue}月${target.dayOfMonth}日 $time"
            } else {
                "${target.year}年${target.monthValue}月${target.dayOfMonth}日 $time"
            }
        }
    }

    /** 是否已过期（严格早于现在）。 */
    fun isOverdue(dueMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean = dueMs < nowMs

    /** 是否今天到期。 */
    fun isToday(dueMs: Long, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        toLocalDate(dueMs, zone) == toLocalDate(nowMs, zone)

    /** 距今天还有几天（0 = 今天，1 = 明天，-1 = 昨天）。 */
    fun daysUntil(
        dueMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = ChronoUnit.DAYS.between(toLocalDate(nowMs, zone), toLocalDate(dueMs, zone))

    private fun atDayOffset(
        epochMs: Long,
        days: Long,
        zone: ZoneId,
        hour: Int,
        minute: Int,
    ): Long {
        val date = toLocalDate(epochMs, zone).plusDays(days)
        return date.atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun toLocalDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun localHour(epochMs: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(epochMs).atZone(zone).hour

    private fun localMinute(epochMs: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(epochMs).atZone(zone).minute
}
