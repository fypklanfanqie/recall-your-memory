package com.echo.recall.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 时间展示（纯 Kotlin，可单测） */
object TimeFormat {

    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val fullFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)

    private fun local(epochMs: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)

    /** "14:32" */
    fun clock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        local(epochMs, zone).format(clockFormatter)

    /** "今天 14:32" / "昨天 14:32" / "9月12日 14:32" / "2025年9月12日 14:32" */
    fun friendly(epochMs: Long, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        val target = local(epochMs, zone)
        val today = local(nowMs, zone).toLocalDate()
        val date = target.toLocalDate()
        val time = target.format(clockFormatter)
        return when {
            date == today -> "今天 $time"
            date == today.minusDays(1) -> "昨天 $time"
            date.year == today.year -> "${target.format(dateFormatter)} $time"
            else -> target.format(fullFormatter)
        }
    }

    /** "2 分 13 秒" */
    fun duration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes == 0L -> "$seconds 秒"
            seconds == 0L -> "$minutes 分"
            else -> "$minutes 分 $seconds 秒"
        }
    }

    /** "刚刚" / "12 秒前" / "5 分钟前" / "2 小时前"（基于单调时钟差值） */
    fun relative(elapsedMs: Long, nowElapsedMs: Long): String {
        val delta = (nowElapsedMs - elapsedMs).coerceAtLeast(0)
        return when {
            delta < 5_000 -> "刚刚"
            delta < 60_000 -> "${delta / 1000} 秒前"
            delta < 3_600_000 -> "${delta / 60_000} 分钟前"
            else -> "${delta / 3_600_000} 小时前"
        }
    }

    /** "14:30 – 14:35"（跨天补日期） */
    fun range(startMs: Long, endMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = local(startMs, zone)
        val end = local(endMs, zone)
        val prefix = if (start.toLocalDate() == LocalDate.now(zone)) "" else "${start.format(dateFormatter)} "
        return "$prefix${start.format(clockFormatter)} – ${end.format(clockFormatter)}"
    }
}
