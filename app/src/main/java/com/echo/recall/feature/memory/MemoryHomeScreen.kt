package com.echo.recall.feature.memory

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.R
import com.echo.recall.core.audio.RecorderEngine
import com.echo.recall.core.designsystem.component.EchoEmptyState
import com.echo.recall.core.designsystem.component.EchoRow
import com.echo.recall.core.designsystem.component.HeroRecallButton
import com.echo.recall.core.designsystem.component.LiveDot
import com.echo.recall.core.designsystem.component.ScreenHeader
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.glass.GlassHost
import com.echo.recall.core.designsystem.glass.LocalEchoBackdrop
import com.echo.recall.core.designsystem.glass.LocalGlassParams
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.permission.EchoPermissions
import com.echo.recall.core.service.RecorderService
import com.echo.recall.core.util.TimeFormat
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

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

    GlassHost(
        modifier = Modifier.fillMaxSize(),
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 108.dp),
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

                item { Spacer(Modifier.height(212.dp)) }

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
        },
        overlay = {
            // 固定悬浮的液态玻璃回溯按钮（官方 LiquidButton 模式）
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 218.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassHero(
                    label = if (listening) stringResource(R.string.memory_recall) else "开启聆听",
                    enabled = true,
                    onClick = {
                        when {
                            listening -> viewModel.recall()
                            EchoPermissions.hasRecordAudio(context) -> RecorderService.start(context)
                            else -> permissionLauncher.launch(EchoPermissions.requiredForListening())
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (listening) {
                        "取出点击前 ${settings.windowLabel()} 的声音（取走后缓冲清空）"
                    } else {
                        "回声只在本机处理音频，不会自动上传"
                    },
                    style = EchoType.footnote,
                    color = colors.secondaryLabel,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
            // 聆听中的悬浮液态玻璃操作药丸（Dock 上方）
            if (listening) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 94.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassPill(text = if (status.state == RecorderEngine.State.PAUSED) "继续聆听" else "暂停聆听") {
                        if (status.state == RecorderEngine.State.PAUSED) {
                            RecorderService.resume(context)
                        } else {
                            RecorderService.pause(context)
                        }
                    }
                    GlassPill(text = "清空缓冲") { viewModel.clearBuffer() }
                    GlassPill(text = "停止") { RecorderService.stop(context) }
                }
            }
        },
    )
}

/** 悬浮液态玻璃回溯大按钮（按压时折射浮现，无常驻动画以省电） */
@Composable
private fun GlassHero(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalEchoColors.current
    val backdrop = LocalEchoBackdrop.current
    val params = LocalGlassParams.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "heroScale",
    )
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.size(172.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(172.dp)
                .scale(scale)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { androidx.compose.foundation.shape.CircleShape },
                            effects = {
                                if (!size.isSpecified) return@drawBackdrop
                                vibrancy()
                                blur(params.blurRadiusDp.dp.toPx())
                                lens(
                                    params.refractionHeightDp.dp.toPx(),
                                    params.refractionAmountDp.dp.toPx(),
                                    chromaticAberration = params.chromaticAberration,
                                )
                            },
                            highlight = {
                                com.kyant.backdrop.highlight.Highlight.Default.copy(
                                    alpha = if (pressed) params.highlightAlpha else params.highlightAlpha * 0.55f,
                                )
                            },
                            onDrawSurface = {
                                drawRect(colors.accent.copy(alpha = params.tintAlpha * 0.35f))
                                drawRect(colors.surface.copy(alpha = 0.22f))
                            },
                        )
                    } else {
                        Modifier
                            .background(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    listOf(colors.accent.copy(alpha = 0.14f), colors.surface),
                                ),
                                androidx.compose.foundation.shape.CircleShape,
                            )
                    },
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = if (enabled) colors.accent else colors.tertiaryLabel,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = label,
                    style = EchoType.headline,
                    color = if (enabled) colors.label else colors.tertiaryLabel,
                )
            }
        }
    }
}

/** 悬浮液态玻璃药丸（嵌套 GlassHost：玻璃采样本屏录制层，兄弟节点不鬼影） */
@Composable
private fun GlassPill(text: String, onClick: () -> Unit) {
    val colors = LocalEchoColors.current
    val backdrop = LocalEchoBackdrop.current
    val params = LocalGlassParams.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            if (!size.isSpecified) return@drawBackdrop
                            vibrancy()
                            blur(params.blurRadiusDp.dp.toPx() * 0.6f)
                            lens(
                                params.refractionHeightDp.dp.toPx() * 0.6f,
                                params.refractionAmountDp.dp.toPx() * 0.6f,
                                chromaticAberration = params.chromaticAberration,
                            )
                        },
                        onDrawSurface = { drawRect(colors.surface.copy(alpha = 0.28f + params.tintAlpha * 0.2f)) },
                    )
                } else {
                    Modifier
                        .background(colors.surface.copy(alpha = 0.92f), Capsule())
                },
            )
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(
            text = text,
            style = EchoType.subhead,
            color = colors.accent,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun relativeTime(elapsedMs: Long): String =
    TimeFormat.relative(elapsedMs, SystemClock.elapsedRealtime())
