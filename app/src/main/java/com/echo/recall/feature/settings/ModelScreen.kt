package com.echo.recall.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.asr.AsrModelSpec
import com.echo.recall.core.asr.ModelInstall
import com.echo.recall.core.asr.ModelState
import com.echo.recall.core.designsystem.component.EchoRow
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors

/**
 * 本地语音模型管理（v1.1 分级版）。
 *
 * 从 v1.0 的「单个下载按钮」改为**按设备能力分档推荐**的卡片列表：
 * 顶部显示本机探测结果与推荐理由，下面列出 L1/L2/L3 三档，可任选、可并存、可删除。
 */
@Composable
fun ModelScreen(
    onBack: () -> Unit,
    viewModel: ModelViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = LocalEchoColors.current
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { context.toast(it) }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importModel)
    }
    val tokensPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importTokens)
    }

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
                text = "本地语音模型",
                style = EchoType.title2,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
        }

        // ---- 设备探测 + 推荐 ----
        SectionLabel("你的设备")
        SectionCard {
            val recommended = screen.installs.firstOrNull { it.spec.id == screen.recommendedId }?.spec
            EchoRow(
                icon = Icons.Rounded.CheckCircle,
                title = "推荐：${recommended?.displayName ?: "检测中…"}",
                subtitle = screen.recommendReason.ifBlank { "正在探测设备能力…" },
            )
        }

        // ---- 三档模型列表 ----
        SectionLabel("可选模型（按性能分级）")
        SectionCard {
            screen.installs.forEachIndexed { index, install ->
                if (index > 0) {
                    Spacer(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.separator),
                    )
                }
                ModelTierRow(
                    install = install,
                    isSelected = install.spec.id == screen.selectedId,
                    isRecommended = install.spec.id == screen.recommendedId,
                    downloadState = screen.download,
                    onSelect = viewModel::select,
                    onDownload = viewModel::download,
                    onDelete = viewModel::delete,
                )
            }
        }

        // ---- 手动导入 ----
        SectionLabel("手动导入")
        SectionCard {
            EchoRow(
                icon = Icons.Rounded.Upload,
                title = "导入模型文件",
                subtitle = "已自行下载时可选，按当前选中模型校验哈希",
                showChevron = true,
                onClick = { modelPicker.launch(arrayOf("*/*")) },
            )
            EchoRow(
                icon = Icons.Rounded.Upload,
                title = "导入 tokens.txt",
                subtitle = "与模型文件配套",
                showChevron = true,
                onClick = { tokensPicker.launch(arrayOf("*/*")) },
            )
            if (screen.totalUsedBytes > 0) {
                EchoRow(
                    icon = Icons.Rounded.Delete,
                    title = "删除全部模型",
                    subtitle = "当前占用 ${"%.0f".format(screen.totalUsedBytes / 1024.0 / 1024.0)} MB",
                    showChevron = true,
                    onClick = viewModel::deleteAll,
                )
            }
        }

        SectionLabel("关于本地模型")
        SectionCard {
            Text(
                text = "所有模型均在本机离线运行，音频不会上传。" +
                    "下载源优先使用国内镜像（hf-mirror），国内网络无需 VPN；" +
                    "每个文件都做 SHA-256 校验，失败会自动切换备用镜像。" +
                    "档位越高体积与内存占用越大、准确率越好；低配机型建议用轻量档。",
                style = EchoType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModelTierRow(
    install: ModelInstall,
    isSelected: Boolean,
    isRecommended: Boolean,
    downloadState: ModelState,
    onSelect: (AsrModelSpec) -> Unit,
    onDownload: (AsrModelSpec) -> Unit,
    onDelete: (AsrModelSpec) -> Unit,
) {
    val colors = LocalEchoColors.current
    val spec = install.spec
    val isDownloading = (downloadState as? ModelState.Downloading)?.modelId == spec.id

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = install.installed) { onSelect(spec) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 档位徽标 + 名称 + 选中态
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "L${spec.tier.order}",
                style = EchoType.caption,
                color = colors.accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(1.dp, colors.accent, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.padding(4.dp))
            Text(
                text = spec.displayName,
                style = EchoType.body,
                color = colors.label,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Text("使用中", style = EchoType.caption, color = colors.accent)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "约 ${spec.totalMb()} MB · ${spec.languages} · 建议 ${spec.minCores} 核以上",
            style = EchoType.caption,
            color = colors.secondaryLabel,
        )

        Spacer(Modifier.height(2.dp))
        // 诚实标注的准确率/适用性说明
        Text(
            text = spec.accuracyNote,
            style = EchoType.footnote,
            color = colors.secondaryLabel,
        )

        if (isRecommended && !install.installed) {
            Spacer(Modifier.height(2.dp))
            Text("推荐给你的设备", style = EchoType.footnote, color = colors.accent)
        }

        if (isDownloading && downloadState is ModelState.Downloading) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { downloadState.overallPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = colors.accent,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${"%.1f".format(downloadState.fileBytes / 1024.0 / 1024.0)} / " +
                    "${"%.1f".format(downloadState.fileTotal / 1024.0 / 1024.0)} MB · " +
                    "总进度 ${downloadState.overallPercent}%",
                style = EchoType.footnote,
                color = colors.secondaryLabel,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isDownloading -> TextButton(onClick = { }) { Text("下载中…请保持网络") }

                install.installed -> {
                    if (!isSelected) {
                        TextButton(onClick = { onSelect(spec) }) { Text("使用这个") }
                    }
                    TextButton(onClick = { onDelete(spec) }) {
                        Text("删除", color = colors.danger)
                    }
                }

                else -> TextButton(onClick = { onDownload(spec) }) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = colors.accent)
                    Text("  下载（${spec.totalMb()} MB）")
                }
            }
        }
    }
}
