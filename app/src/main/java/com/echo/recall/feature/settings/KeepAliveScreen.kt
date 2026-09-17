package com.echo.recall.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.designsystem.component.EchoRow
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors

/** 后台保活引导：状态 + 一键跳转 + 机型逐步说明 */
@Composable
fun KeepAliveScreen(
    onBack: () -> Unit,
    viewModel: KeepAliveViewModel = hiltViewModel(),
) {
    val colors = LocalEchoColors.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(bottom = 96.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = colors.accent)
            }
            Text(
                text = "后台保活",
                style = EchoType.title2,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
        }

        SectionLabel("状态")
        SectionCard {
            EchoRow(
                icon = if (ui.ignoringBatteryOptimizations) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                iconTint = if (ui.ignoringBatteryOptimizations) colors.success else colors.warning,
                title = if (ui.ignoringBatteryOptimizations) "已忽略电池优化" else "尚未忽略电池优化",
                subtitle = if (ui.ignoringBatteryOptimizations) {
                    "系统不会因为省电策略中断聆听"
                } else {
                    "点此授权：系统可能随时杀掉后台聆听"
                },
                showChevron = !ui.ignoringBatteryOptimizations,
                onClick = if (ui.ignoringBatteryOptimizations) null else viewModel::requestIgnoreBatteryOptimizations,
            )
            EchoRow(
                icon = Icons.Rounded.Notifications,
                iconTint = if (ui.notificationsEnabled) colors.success else colors.danger,
                title = if (ui.notificationsEnabled) "通知已开启" else "通知被关闭",
                subtitle = if (ui.notificationsEnabled) {
                    "常驻通知是聆听可被系统允许的前提"
                } else {
                    "点此开启通知，否则无法后台聆听"
                },
                showChevron = !ui.notificationsEnabled,
                onClick = if (ui.notificationsEnabled) null else viewModel::openNotificationSettings,
            )
            EchoRow(
                icon = Icons.Rounded.BatteryFull,
                iconTint = if (status.state == com.echo.recall.core.audio.RecorderEngine.State.LISTENING) {
                    colors.success
                } else {
                    colors.secondaryLabel
                },
                title = "当前状态",
                subtitle = when (status.state) {
                    com.echo.recall.core.audio.RecorderEngine.State.LISTENING -> "正在聆听"
                    com.echo.recall.core.audio.RecorderEngine.State.PAUSED -> "已暂停"
                    com.echo.recall.core.audio.RecorderEngine.State.ERROR -> status.message ?: "出错"
                    else -> "已停止"
                },
            )
        }

        SectionLabel("你的机型：${ui.brand}")
        SectionCard {
            Column(modifier = Modifier.padding(16.dp)) {
                ui.steps.forEachIndexed { index, step ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "${index + 1}.",
                            style = EchoType.subhead,
                            color = colors.accent,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(text = step, style = EchoType.subhead, color = colors.label)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "不同系统版本菜单名称可能略有差异，核心是把「回声」的自启动、后台运行、" +
                        "电池策略三项都放开。",
                    style = EchoType.caption,
                    color = colors.secondaryLabel,
                )
            }
        }

        SectionLabel("快捷恢复")
        SectionCard {
            EchoRow(
                icon = Icons.Rounded.RestartAlt,
                title = "下拉通知栏添加「回声」磁贴",
                subtitle = "编辑快捷设置 → 找到「回声」→ 拖到常用区域；一键恢复/停止聆听",
            )
            EchoRow(
                icon = Icons.Rounded.Storage,
                title = "存储占用",
                subtitle = "记忆音频 ${viewModel.formatBytes(ui.audioBytes)} · 语音模型 ${viewModel.formatBytes(ui.modelBytes)}",
            )
            EchoRow(
                icon = Icons.Rounded.Warning,
                iconTint = colors.warning,
                title = "重启手机后需要手动恢复",
                subtitle = "Android 15 起系统禁止 App 开机自动开启麦克风；回声会发一条通知，点按即可恢复",
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
