package com.echo.recall.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** 转写状态（M2 起生效） */
object TranscribeState {
    const val NONE = 0
    const val PENDING = 1
    const val RUNNING = 2
    const val DONE = 3
    const val FAILED = 4
}

/**
 * 记忆条目：一次「回溯」的产物。
 * 音频默认保留 7 天，收藏后永久保留。
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    /** 墙钟时间（epoch ms）：这段记忆真实发生的时间范围 */
    val wallStartAt: Long,
    val wallEndAt: Long,
    /** 人声总时长（ms），不含静默空档 */
    val voicedMillis: Long,
    /** 音频文件绝对路径（编码失败为 null） */
    val audioPath: String?,
    /** 音频总时长（ms） */
    val audioDurationMs: Long,
    /** 片段列表 JSON（含音频内偏移 + 墙钟时间 + 转写文字） */
    val segmentsJson: String? = null,
    val transcript: String? = null,
    val language: String? = null,
    val emotion: String? = null,
    val summary: String? = null,
    val transcribeState: Int = TranscribeState.NONE,
    /** 本次转写/总结使用的模型标识，便于溯源 */
    val modelTag: String? = null,
    val favorited: Boolean = false,
)

/** 记忆内的一个人声片段 */
@Serializable
data class MemorySegment(
    /** 在音频文件中的起止（ms），用于点击字幕跳转播放 */
    val audioStartMs: Long,
    val audioEndMs: Long,
    /** 真实发生时间（epoch ms） */
    val wallStartAt: Long,
    val wallEndAt: Long,
    val text: String? = null,
)
