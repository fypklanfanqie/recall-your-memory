package com.echo.recall.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.asr.ModelState
import com.echo.recall.core.designsystem.component.EchoRow
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors

/** 本地语音模型管理：下载 / 导入 / 删除 / 补转写 */
@Composable
fun ModelScreen(
    onBack: () -> Unit,
    viewModel: ModelViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = LocalEchoColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var usedBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state) {
        usedBytes = viewModel.usedBytesAsync()
    }

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

        SectionLabel("状态")
        SectionCard {
            EchoRow(
                icon = Icons.Rounded.CloudDownload,
                title = when (val s = state) {
                    ModelState.Ready -> "SenseVoice 已就绪"
                    is ModelState.Downloading -> "正在下载 ${s.fileName}"
                    is ModelState.Failed -> "上次失败：${s.message}"
                    ModelState.NotReady -> "尚未下载模型"
                },
                subtitle = when (val s = state) {
                    ModelState.Ready -> "离线转写可用 · 占用 ${"%.0f".format(usedBytes / 1024.0 / 1024.0)} MB"
                    is ModelState.Downloading ->
                        "${"%.1f".format(s.fileBytes / 1024.0 / 1024.0)} / ${"%.1f".format(s.fileTotal / 1024.0 / 1024.0)} MB · 总进度 ${s.overallPercent}%"
                    else -> "SenseVoice-small int8 · 中/粤/英/日/韩 · 全离线"
                },
            )
            when (val s = state) {
                is ModelState.Downloading -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        LinearProgressIndicator(
                            progress = { s.overallPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.accent,
                        )
                    }
                }
                else -> Unit
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    ModelState.Ready -> {
                        TextButton(onClick = viewModel::transcribePending) { Text("补转写待处理记忆") }
                        TextButton(onClick = viewModel::delete) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = colors.danger)
                            Spacer(Modifier.padding(2.dp))
                            Text("删除模型", color = colors.danger)
                        }
                    }
                    is ModelState.Downloading -> {
                        TextButton(onClick = { }) { Text("下载中…请保持网络") }
                    }
                    else -> {
                        TextButton(onClick = viewModel::download) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = colors.accent)
                            Text("  下载模型（228 MB）")
                        }
                    }
                }
            }
        }

        SectionLabel("手动导入")
        SectionCard {
            EchoRow(
                icon = Icons.Rounded.Upload,
                title = "导入 model.int8.onnx",
                subtitle = "已自行下载模型时可选",
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
        }

        SectionLabel("关于这个模型")
        SectionCard {
            Text(
                text = "SenseVoice-small（阿里 FunAudioLLM 开源，Apache-2.0）以 int8 量化 ONNX 形式在手机本地运行，" +
                    "支持中文普通话、粤语、英语、日语、韩语，自动加标点与数字规范化。" +
                    "转写全程在本机完成，音频不会上传。" +
                    "下载源优先使用国内镜像（hf-mirror），失败会自动切换官方站点。",
                style = EchoType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
