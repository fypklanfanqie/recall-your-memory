package com.echo.recall.feature.guide

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echo.recall.core.designsystem.component.EchoRow
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors

/**
 * 使用指南：分点介绍本 App 的功能。
 *
 * 内容按「怎么用」组织，而不是按技术模块 —— 用户是从首页右上角进来的，
 * 关心的是「我该点哪里、会发生什么」。
 */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val colors = LocalEchoColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        // ---- 顶部返回 ----
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
                text = "使用指南",
                style = EchoType.title2,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "回声 = 环境声环形录音 + 人声检测 + 本地离线转写。\n" +
                "下面按「怎么用」分点说明。",
            style = EchoType.footnote,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        // ---- 快速上手 ----
        SectionLabel("快速上手（3 步）")
        SectionCard {
            StepRow(1, "开启聆听", "设置 → 开启声音记录（或首页直接点「开启聆听」）。开始后麦克风常驻监听。")
            StepRow(2, "说话", "有人说话才会被记录，静默不占空间。窗口内累计人声会在首页实时显示。")
            StepRow(3, "点「回溯记忆」", "取出点击前 N 分钟的声音生成一条记忆，随后自动本地转写。")
        }

        // ---- 功能分点 ----
        GuideSection(
            icon = Icons.Rounded.Mic,
            title = "回声聆听（后台常驻）",
            bullets = listOf(
                "后台常驻监听，只有检测到人声才写入内存，静默期不占空间。",
                "只保留最近 0.5–5 分钟（设置里可调）；点回溯取走后缓冲即清空。",
                "常驻通知始终可见，随时可暂停 / 停止。",
                "两级触发门：静默帧先用能量门挡掉、不跑人声推理，再用过零率过滤稳态噪声，省电又不误杀真人声。",
                "省电模式两档：均衡（默认，零丢音）/ 极致省电（静默期跳过推理，同样不丢音）。",
            ),
        )

        GuideSection(
            icon = Icons.Rounded.History,
            title = "一键回溯",
            bullets = listOf(
                "点首页「回溯记忆」＝取出点击前 N 分钟的人声，生成一条「记忆」。",
                "会自动补上句首前 400ms，不会切掉第一个字。",
                "生成同时自动开始本地转写，文字逐段实时上屏。",
                "音频默认保留 7 天；「收藏」后永久保留。",
            ),
        )

        GuideSection(
            icon = Icons.Rounded.Translate,
            title = "本地离线转写",
            bullets = listOf(
                "转写全程在本机完成，音频不上传、可离线。",
                "3 档模型按你的设备自动推荐：轻量约 29MB / 均衡约 78MB / 高精度约 228MB。",
                "国内网络可直连下载（hf-mirror），无需 VPN；每个文件都做 SHA-256 校验。",
                "档位越高越准、体积与内存占用越大；低配机型建议用轻量档。",
                "设置 → 本地语音模型 → 维护 → 「补转写待处理记忆」：把卡在「转写中」的记忆重跑一遍。",
            ),
        )

        GuideSection(
            icon = Icons.Rounded.AutoAwesome,
            title = "AI 联动（可选，需自填 API Key）",
            bullets = listOf(
                "记忆总结、追问（流式输出），一键从记忆里提取待办。",
                "内置 8 家国内大模型供应商，也支持自定义 OpenAI 兼容接口。",
                "只有「文字」会被发送到你选择的云端；音频永远不会离开本机。",
                "API Key 用 Android Keystore 加密存储。",
            ),
        )

        GuideSection(
            icon = Icons.Rounded.BookmarkBorder,
            title = "收藏 / 待办 / 备忘",
            bullets = listOf(
                "收藏：永久保留，不受 7 天清理影响。",
                "待办：支持截止时间、分组与置顶；可从记忆一键提取。",
                "备忘：支持 AI 润色与总结。",
            ),
        )

        GuideSection(
            icon = Icons.Rounded.WaterDrop,
            title = "外观（液态玻璃）",
            bullets = listOf(
                "三档：液态玻璃（实时折射）/ 毛玻璃（仅模糊）/ 半透明，最省电是半透明。",
                "可调：模糊半径、折射高度、折射强度、色散、高光强度、染色浓度。",
                "折射用「占元素尺寸的比例」：调到 0 即为完全清晰，调到 0 就是真的 0，不会偷偷加模糊。",
                "色散（边缘红蓝分离）需要折射高度 / 强度足够大才看得见 —— 太小会落到亚像素级别。调乱了可一键「恢复官方默认参数」。",
                "可换自定义壁纸（内置拖动 / 双指缩放裁剪），玻璃会折射这张图。",
            ),
        )

        GuideSection(
            icon = Icons.Rounded.BatterySaver,
            title = "续航与保活",
            bullets = listOf(
                "亮屏不占用唤醒锁，只在「灭屏 + 聆听」时限时持有，让系统有机会进入低功耗。",
                "前台服务 + 清后台自愈 + 快捷磁贴 + 通知一键恢复。",
                "各品牌 ROM 的额外设置（自启动、后台锁定等）见 设置 → 后台保活引导。",
                "Android 15 起平台限制开机自动录音：开机后需打开一次 App 或用磁贴恢复。",
            ),
        )

        // ---- 隐私 ----
        SectionLabel("隐私原则")
        SectionCard {
            EchoRow(
                icon = Icons.Rounded.Lock,
                title = "音频与转写 100% 本地",
                subtitle = "录音只进内存环形缓冲，取走即消失，绝不自动上传",
            )
            EchoRow(
                icon = Icons.Rounded.Shield,
                title = "只有你主动用 AI 时才会联网",
                subtitle = "此时发送的是文字，不发音频",
            )
        }

        // ---- 常见问题 ----
        SectionLabel("常见问题")
        SectionCard {
            FaqRow("记忆一直显示「转写中」？", "多半是模型没下完，或这次转写被中断。去「本地语音模型 → 维护」点补转写。")
            FaqRow("下载进度卡住 / 报「无法写入」？", "同一时间只会跑一个下载，重复点击会被挡下。若仍失败，重试即可（支持断点续传）。")
            FaqRow("把模糊调到 0 还是磨砂感？", "检查「染色浓度」是不是很高（它是覆盖在玻璃上的一层色），以及折射是否还开着。")
            FaqRow("后台一段时间就不录了？", "系统或厂商 ROM 杀了进程。按保活引导放行，或下拉通知栏用磁贴一键恢复。")
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepRow(step: Int, title: String, body: String) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$step",
                style = EchoType.footnote,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = title, style = EchoType.headline, color = colors.label)
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = EchoType.footnote, color = colors.secondaryLabel)
        }
    }
}

/**
 * 一个功能分节：分节标题（图标 + 标题）+ 卡片内的要点列表。
 *
 * 标题只出现一次（放在卡片外当分节头），避免和卡内标题重复。
 */
@Composable
private fun GuideSection(
    icon: ImageVector,
    title: String,
    bullets: List<String>,
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = EchoType.footnote,
            color = colors.secondaryLabel,
            fontWeight = FontWeight.SemiBold,
        )
    }
    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            bullets.forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                BulletRow(line)
            }
        }
    }
}

/** 单条要点：小圆点 + 文本 */
@Composable
private fun BulletRow(text: String) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.85f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = EchoType.subhead,
            color = colors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 常见问题：问 + 答 */
@Composable
private fun FaqRow(question: String, answer: String) {
    val colors = LocalEchoColors.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Checklist,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = question,
                style = EchoType.subhead,
                color = colors.label,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = answer,
            style = EchoType.footnote,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}
