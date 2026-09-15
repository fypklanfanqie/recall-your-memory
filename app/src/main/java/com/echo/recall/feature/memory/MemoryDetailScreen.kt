package com.echo.recall.feature.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.data.db.MemorySegment
import com.echo.recall.core.data.db.TranscribeState
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.util.TimeFormat

@Composable
fun MemoryDetailScreen(
    onBack: () -> Unit,
    onOpenChat: () -> Unit = {},
    onOpenProviders: () -> Unit = {},
    viewModel: MemoryDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = LocalEchoColors.current
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val transcribeProgress by viewModel.transcribeProgress.collectAsStateWithLifecycle()
    val summaryState by viewModel.summaryState.collectAsStateWithLifecycle()
    val todoUi by viewModel.todoUi.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MemoryDetailViewModel.DetailEvent.Message -> context.toast(event.text)
                MemoryDetailViewModel.DetailEvent.Deleted -> onBack()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = colors.accent)
                }
                Text(
                    text = memory?.let { TimeFormat.friendly(it.createdAt) } ?: "记忆详情",
                    style = EchoType.headline,
                    color = colors.label,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (memory?.favorited == true) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "收藏",
                        tint = if (memory?.favorited == true) colors.warning else colors.tertiaryLabel,
                    )
                }
                IconButton(onClick = viewModel::delete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = colors.danger)
                }
            }
        }

        memory?.let { entity ->
            item {
                SectionCard {
                    InfoLine("发生时间", TimeFormat.range(entity.wallStartAt, entity.wallEndAt))
                    InfoLine("人声时长", TimeFormat.duration(entity.voicedMillis))
                    InfoLine("片段", "${segments.size} 段")
                    InfoLine(
                        label = "音频",
                        value = if (entity.audioPath == null) "保存失败" else TimeFormat.duration(entity.audioDurationMs.toLong()),
                    )
                }
            }

            item {
                SectionLabel("播放")
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val totalDurationMs =
                            (if (player.durationMs > 0) player.durationMs.toLong() else entity.audioDurationMs)
                                .coerceAtLeast(1L)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = viewModel::togglePlay,
                                enabled = entity.audioPath != null,
                            ) {
                                Icon(
                                    imageVector = if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (player.playing) "暂停" else "播放",
                                    tint = if (entity.audioPath != null) colors.accent else colors.tertiaryLabel,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "${TimeFormat.duration(player.positionMs.toLong())} / " +
                                    TimeFormat.duration(totalDurationMs),
                                style = EchoType.footnote,
                                color = colors.secondaryLabel,
                            )
                        }
                        Slider(
                            value = player.positionMs.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toInt()) },
                            valueRange = 0f..totalDurationMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.surfaceSecondary,
                            ),
                        )
                    }
                }
            }

            item {
                SectionLabel("转写文字")
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when {
                            transcribeProgress != null -> {
                                Text(
                                    text = "正在本地转写… ${transcribeProgress}%",
                                    style = EchoType.footnote,
                                    color = colors.secondaryLabel,
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (transcribeProgress ?: 0) / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.accent,
                                )
                            }

                            entity.transcript.isNullOrBlank() -> {
                                Text(
                                    text = when (entity.transcribeState) {
                                        TranscribeState.FAILED -> "上次转写失败，可重试"
                                        TranscribeState.PENDING -> "尚未转写（需要先下载本地模型）"
                                        else -> "这条记忆没有识别到文字"
                                    },
                                    style = EchoType.subhead,
                                    color = colors.secondaryLabel,
                                )
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = viewModel::retranscribe) { Text("立即本地转写") }
                            }

                            else -> {
                                Text(
                                    text = entity.transcript.orEmpty(),
                                    style = EchoType.body,
                                    color = colors.label,
                                )
                                if (!entity.language.isNullOrBlank() || !entity.emotion.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = listOfNotNull(
                                            entity.language?.let { "语种 $it" },
                                            entity.emotion?.let { "语气 $it" },
                                        ).joinToString(" · "),
                                        style = EchoType.caption,
                                        color = colors.tertiaryLabel,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                TextButton(onClick = viewModel::retranscribe) { Text("重新转写") }
                            }
                        }
                    }
                }
            }
        }

        item { SectionLabel("AI 处理") }
        memory?.let { entity ->
            item {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (val state = summaryState) {
                            SummaryUi.Loading -> {
                                Text("正在总结…", style = EchoType.footnote, color = colors.secondaryLabel)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.accent,
                                )
                            }

                            is SummaryUi.Error -> {
                                Text(state.message, style = EchoType.subhead, color = colors.danger)
                                TextButton(onClick = viewModel::summarize) { Text("重试") }
                            }

                            SummaryUi.Idle -> {
                                val summary = entity.summary
                                if (summary.isNullOrBlank()) {
                                    Text(
                                        text = "还没有 AI 总结。总结只会上传这段转写文字，音频不出本机。",
                                        style = EchoType.footnote,
                                        color = colors.secondaryLabel,
                                    )
                                } else {
                                    Text(summary, style = EchoType.body, color = colors.label)
                                    entity.modelTag?.let { tag ->
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "由 $tag 生成",
                                            style = EchoType.caption,
                                            color = colors.tertiaryLabel,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    TextButton(onClick = viewModel::summarize) {
                                        Text(if (summary.isNullOrBlank()) "AI 总结" else "重新总结")
                                    }
                                    TextButton(onClick = onOpenChat) { Text("追问这段记忆 →") }
                                    if (!viewModel.llmConfigured) {
                                        TextButton(onClick = onOpenProviders) { Text("配置供应商") }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = viewModel::extractTodos,
                                        enabled = todoUi !is TodoExtractUi.Loading,
                                    ) {
                                        Text(
                                            when (todoUi) {
                                                TodoExtractUi.Loading -> "提取中…"
                                                is TodoExtractUi.Done -> "再提取一次"
                                                else -> "提取待办"
                                            },
                                        )
                                    }
                                    when (val extract = todoUi) {
                                        is TodoExtractUi.Done -> Text(
                                            text = "已添加 ${extract.count} 条，去「待办」页查看",
                                            style = EchoType.caption,
                                            color = colors.success,
                                        )

                                        is TodoExtractUi.Error -> Text(
                                            text = extract.message,
                                            style = EchoType.caption,
                                            color = colors.danger,
                                        )

                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionLabel("片段时间轴") }

        if (segments.isEmpty()) {
            item {
                Text(
                    text = "这条记忆没有片段信息",
                    style = EchoType.footnote,
                    color = colors.tertiaryLabel,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                )
            }
        } else {
            items(segments) { segment ->
                SegmentRow(
                    segment = segment,
                    onClick = { viewModel.seekTo(segment.audioStartMs.toInt()) },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = EchoType.body, color = colors.secondaryLabel)
        Text(text = value, style = EchoType.body, color = colors.label)
    }
}

@Composable
private fun SegmentRow(segment: MemorySegment, onClick: () -> Unit) {
    val colors = LocalEchoColors.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TimeFormat.clock(segment.wallStartAt),
                style = EchoType.footnote,
                color = colors.accent,
                modifier = Modifier.padding(end = 12.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = segment.text?.takeIf { it.isNotBlank() }
                        ?: "（${TimeFormat.duration(segment.audioEndMs - segment.audioStartMs)} 人声，未转写）",
                    style = if (segment.text.isNullOrBlank()) EchoType.footnote else EchoType.subhead,
                    color = if (segment.text.isNullOrBlank()) colors.tertiaryLabel else colors.label,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
