package com.echo.recall.feature.settings

import android.content.pm.PackageManager
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalEchoColors.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName} ($code)"
        }.getOrDefault("unknown")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
                text = "关于回声",
                style = EchoType.title2,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
        }

        SectionLabel("版本")
        SectionCard {
            Text(
                text = "回声 Echo $version\n环境声记忆回溯工具",
                style = EchoType.body,
                color = colors.label,
                modifier = Modifier.padding(16.dp),
            )
        }

        SectionLabel("隐私承诺")
        SectionCard {
            Text(
                text = "· 麦克风仅在「聆听」开启时工作，常驻通知始终可见，可一键暂停\n" +
                    "· 环境声只写入内存环形缓冲，静默时不记录；点「回溯」后编码存到本机\n" +
                    "· 语音转文字 100% 在本机完成（SenseVoice 离线模型），音频绝不上传\n" +
                    "· 只有你主动点 AI 功能时，才会把**转写文字**发送到你所选的云端供应商\n" +
                    "· API Key 使用 Android Keystore（AES-256-GCM）加密保存\n" +
                    "· 未收藏的记忆 7 天后自动删除（音频与文字一起删）",
                style = EchoType.subhead,
                color = colors.label,
                modifier = Modifier.padding(16.dp),
            )
        }

        SectionLabel("开源组件")
        SectionCard {
            Text(
                text = "· sherpa-onnx（Apache-2.0）— 端上语音识别运行时\n" +
                    "· SenseVoice-small（Apache-2.0，阿里 FunAudioLLM）— 中/粤/英/日/韩 识别模型\n" +
                    "· silero-vad（MIT）— 人声活动检测\n" +
                    "· Kotlin / Jetpack Compose / Room / Hilt / OkHttp",
                style = EchoType.subhead,
                color = colors.label,
                modifier = Modifier.padding(16.dp),
            )
        }

        SectionLabel("液态玻璃")
        SectionCard {
            Text(
                text = "界面参考 Apple iOS 与 Kyant0 的 Shapes / AndroidLiquidGlass 观感。" +
                    "由于 Backdrop 库要求 Kotlin 2.3+ / Compose 1.10+，本项目按同样思路自行实现：" +
                    "背景为自绘渐变光斑，玻璃层按窗口坐标重绘背景切片并施加 RenderEffect" +
                    "（Android 13+ 折射+色散，12 模糊，11 及以下平面）。",
                style = EchoType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
