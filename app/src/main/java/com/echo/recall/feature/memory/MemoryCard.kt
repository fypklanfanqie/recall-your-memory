package com.echo.recall.feature.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echo.recall.core.data.db.MemoryEntity
import com.echo.recall.core.data.db.TranscribeState
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.util.TimeFormat

/** 记忆卡片（首页与收藏页共用） */
@Composable
fun MemoryCard(
    memory: MemoryEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = TimeFormat.friendly(memory.createdAt),
                style = EchoType.headline,
                color = colors.label,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(TimeFormat.duration(memory.voicedMillis))
                    append(" 人声 · ")
                    append(TimeFormat.range(memory.wallStartAt, memory.wallEndAt))
                    if (memory.audioPath == null) append(" · 音频保存失败")
                    when (memory.transcribeState) {
                        TranscribeState.PENDING, TranscribeState.RUNNING -> append(" · 转写中")
                        TranscribeState.FAILED -> append(" · 转写失败")
                    }
                },
                style = EchoType.footnote,
                color = colors.secondaryLabel,
            )
            memory.transcript?.takeIf { it.isNotBlank() }?.let { text ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text,
                    style = EchoType.subhead,
                    color = colors.label,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            memory.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "AI 摘要：$summary",
                    style = EchoType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = if (memory.favorited) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "收藏",
                tint = if (memory.favorited) colors.warning else colors.tertiaryLabel,
            )
        }
    }
}
