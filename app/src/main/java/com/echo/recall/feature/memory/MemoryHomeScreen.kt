package com.echo.recall.feature.memory

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.R
import com.echo.recall.core.audio.RecorderEngine
import com.echo.recall.core.designsystem.component.EchoEmptyState
import com.echo.recall.core.designsystem.component.EchoRow
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.component.HeroRecallButton
import com.echo.recall.core.designsystem.component.LiveDot
import com.echo.recall.core.designsystem.component.ScreenHeader
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.permission.EchoPermissions
import com.echo.recall.core.service.RecorderService
import com.echo.recall.core.util.TimeFormat

@Composable
fun MemoryHomeScreen(
    onOpenMemory: (String) -> Unit,
    viewModel: MemoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = LocalEchoColors.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            permissionDenied = false
            RecorderService.start(context)
        } else {
            permissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { context.toast(it) }
    }

    // 上次开启过聆听（进程被杀后回到前台）→ 自动恢复
    LaunchedEffect(status.state, settings.recordingEnabled) {
        if (status.state == RecorderEngine.State.IDLE &&
            settings.recordingEnabled &&
            EchoPermissions.hasRecordAudio(context)
        ) {
            RecorderService.start(context)
        }
    }

    val listening = status.state == RecorderEngine.State.LISTENING ||
        status.state == RecorderEngine.State.PAUSED

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.memory_title),
                subtitle = stringResource(R.string.memory_window, settings.windowLabel()),
            )
        }

        item {
            SectionCard {
                EchoRow(
                    icon = if (status.state == RecorderEngine.State.ERROR) {
                        Icons.Rounded.MicOff
                    } else {
                        Icons.Rounded.Mic
                    },
                    title = when {
                        status.state == RecorderEngine.State.ERROR -> "麦克风不可用"
                        status.state == RecorderEngine.State.LISTENING && status.speechDetected -> "正在记录人声"
                        status.state == RecorderEngine.State.LISTENING -> stringResource(R.string.memory_status_on)
                        status.state == RecorderEngine.State.PAUSED -> stringResource(R.string.memory_status_paused)
                        permissionDenied -> "需要麦克风权限"
                        else -> stringResource(R.string.memory_status_off)
                    },
                    subtitle = when {
                        status.state == RecorderEngine.State.ERROR -> status.message ?: "请检查权限设置"
                        listening -> "窗口内已录 ${TimeFormat.duration(status.voicedMillisInWindow)} 人声 · " +
                            (status.lastVoiceEndMs?.let { "最近一次 ${relativeTime(it)}" } ?: "尚未听到人声")
                        else -> "开启后会在后台静默聆听，只保留最近 ${settings.windowLabel()}"
                    },
                    trailing = {
                        LiveDot(
                            color = if (status.state == RecorderEngine.State.LISTENING) {
                                colors.live
                            } else {
                                colors.tertiaryLabel
                            },
                            active = status.state == RecorderEngine.State.LISTENING && status.speechDetected,
                        )
                    },
                )
            }
        }

        item {
            Spacer(Modifier.height(26.dp))
            HeroRecallButton(
                label = if (listening) stringResource(R.string.memory_recall) else "开启聆听",
                hint = if (listening) {
                    "取出点击前 ${settings.windowLabel()} 的声音（取走后缓冲清空）"
                } else {
                    "回声只在本机处理音频，不会自动上传"
                },
                onClick = {
                    when {
                        listening -> viewModel.recall()
                        EchoPermissions.hasRecordAudio(context) -> RecorderService.start(context)
                        else -> permissionLauncher.launch(EchoPermissions.requiredForListening())
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
        }

        if (listening) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    SmallAction(
                        text = if (status.state == RecorderEngine.State.PAUSED) "继续聆听" else "暂停聆听",
                    ) {
                        if (status.state == RecorderEngine.State.PAUSED) {
                            RecorderService.resume(context)
                        } else {
                            RecorderService.pause(context)
                        }
                    }
                    SmallAction(text = "清空缓冲") { viewModel.clearBuffer() }
                    SmallAction(text = "停止") { RecorderService.stop(context) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        item { SectionLabel("最近") }

        if (memories.isEmpty()) {
            item {
                EchoEmptyState(
                    icon = Icons.Rounded.GraphicEq,
                    text = stringResource(R.string.memory_empty),
                )
            }
        } else {
            items(memories, key = { it.id }) { memory ->
                MemoryCard(
                    memory = memory,
                    onClick = { onOpenMemory(memory.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(memory) },
                )
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    val colors = LocalEchoColors.current
    GlassSurface(
        shape = RoundedCornerShape(14.dp),
        elevation = 4.dp,
    ) {
        Text(
            text = text,
            style = EchoType.footnote,
            color = colors.accent,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private fun relativeTime(elapsedMs: Long): String =
    TimeFormat.relative(elapsedMs, SystemClock.elapsedRealtime())
