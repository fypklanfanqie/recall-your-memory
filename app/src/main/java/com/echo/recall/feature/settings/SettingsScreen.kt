package com.echo.recall.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.echo.recall.R
import com.echo.recall.core.data.settings.EchoSettings
import com.echo.recall.core.data.settings.PowerProfile
import com.echo.recall.core.data.settings.ThemeMode
import com.echo.recall.core.data.settings.VadSensitivity
import com.echo.recall.core.designsystem.component.EchoRow
import com.echo.recall.core.designsystem.component.EchoSegmented
import com.echo.recall.core.designsystem.component.EchoSegmentedRow
import com.echo.recall.core.designsystem.component.EchoSliderRow
import com.echo.recall.core.designsystem.component.EchoSwitchRow
import com.echo.recall.core.designsystem.component.ScreenHeader
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.glass.GlassMode
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.service.RecorderService
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: EchoSettings,
    viewModel: AppStateViewModel,
    onOpenModels: () -> Unit = {},
    onOpenProviders: () -> Unit = {},
    onOpenKeepAlive: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenWallpaperCrop: (Uri) -> Unit = {},
) {
    val colors = LocalEchoColors.current
    val context = LocalContext.current
    val vadLabels = listOf(
        stringResource(R.string.settings_vad_low),
        stringResource(R.string.settings_vad_medium),
        stringResource(R.string.settings_vad_high),
    )
    val themeLabels = listOf(
        stringResource(R.string.settings_theme_system),
        stringResource(R.string.settings_theme_light),
        stringResource(R.string.settings_theme_dark),
    )
    val powerLabels = listOf("均衡", "极致省电")
    val glassModeLabels = listOf("半透明", "毛玻璃", "液态玻璃")
    val resolved = com.echo.recall.core.designsystem.glass.resolveGlassMode(
        com.echo.recall.core.designsystem.glass.GlassMode.fromId(settings.glassMode),
    )
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onOpenWallpaperCrop) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(bottom = 96.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title))

        SectionLabel(stringResource(R.string.settings_section_recording))
        SectionCard {
            EchoSwitchRow(
                icon = Icons.Rounded.Mic,
                title = "开启声音记录",
                subtitle = if (settings.recordingEnabled) "麦克风正在监听环境声" else "关闭后麦克风完全静默",
                checked = settings.recordingEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setRecordingEnabled(enabled)
                    if (enabled) RecorderService.start(context) else RecorderService.stop(context)
                },
            )
            EchoSliderRow(
                title = stringResource(R.string.settings_window),
                subtitle = stringResource(R.string.settings_window_desc),
                value = settings.windowMinutes,
                valueRange = EchoSettings.MIN_WINDOW_MINUTES..EchoSettings.MAX_WINDOW_MINUTES,
                steps = 8,
                valueLabel = settings.windowLabel(),
                onValueChange = viewModel::setWindowMinutes,
            )
            EchoSegmentedRow(
                title = stringResource(R.string.settings_vad),
                subtitle = "人声检测灵敏度越高越容易触发记录",
                options = vadLabels,
                selectedIndex = settings.vadSensitivity.ordinal,
                onSelect = { viewModel.setVadSensitivity(VadSensitivity.entries[it]) },
            )
            EchoSegmentedRow(
                icon = Icons.Rounded.BatterySaver,
                title = "省电模式",
                subtitle = if (settings.powerProfile == PowerProfile.SAVER) {
                    "静默期跳过人声推理，最省电；音频仍保留，人声一响立刻恢复"
                } else {
                    "亮屏不占用唤醒锁 · 灭屏限时持有；静默期零丢音"
                },
                options = powerLabels,
                selectedIndex = settings.powerProfile.ordinal,
                onSelect = { viewModel.setPowerProfile(PowerProfile.entries[it]) },
            )
            EchoSwitchRow(
                icon = Icons.Rounded.Vibration,
                title = "检测到人声时轻震动",
                subtitle = "让你确知正在聆听；会略微增加耗电",
                checked = settings.triggerHaptic,
                onCheckedChange = viewModel::setTriggerHaptic,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_ai))
        SectionCard {
            EchoRow(
                icon = Icons.Rounded.Cloud,
                title = stringResource(R.string.settings_providers),
                subtitle = stringResource(R.string.settings_providers_desc),
                showChevron = true,
                onClick = onOpenProviders,
            )
            EchoRow(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.settings_models),
                subtitle = stringResource(R.string.settings_models_desc),
                showChevron = true,
                onClick = onOpenModels,
            )
            EchoSwitchRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "回溯后自动 AI 总结",
                subtitle = "需要联网并消耗 token",
                checked = settings.autoSummary,
                onCheckedChange = viewModel::setAutoSummary,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_appearance))
        SectionCard {
            EchoSegmentedRow(
                icon = Icons.Rounded.WaterDrop,
                title = stringResource(R.string.settings_liquid_glass),
                subtitle = "半透明最省电 · 毛玻璃仅模糊 · 液态玻璃完整折射",
                options = glassModeLabels,
                selectedIndex = GlassMode.entries.indexOf(resolved),
                onSelect = { viewModel.setGlassMode(GlassMode.entries[it].name) },
            )
            if (resolved != GlassMode.PLAIN) {
                EchoSliderRow(
                    title = "模糊半径",
                    subtitle = "背景磨砂程度",
                    value = settings.glassBlurDp,
                    valueRange = 0f..40f,
                    valueLabel = "${settings.glassBlurDp.roundToInt()} dp",
                    onValueChange = viewModel::setGlassBlur,
                )
            }
            if (resolved == GlassMode.LIQUID) {
                EchoSliderRow(
                    title = "折射高度",
                    subtitle = "边缘多大范围发生折射",
                    value = settings.glassRefractionHeightDp,
                    valueRange = 0f..40f,
                    valueLabel = "${settings.glassRefractionHeightDp.roundToInt()} dp",
                    onValueChange = viewModel::setGlassRefractionHeight,
                )
                EchoSliderRow(
                    title = "折射强度",
                    subtitle = "边缘弯曲程度",
                    value = settings.glassRefractionAmountDp,
                    valueRange = 0f..40f,
                    valueLabel = "${settings.glassRefractionAmountDp.roundToInt()} dp",
                    onValueChange = viewModel::setGlassRefractionAmount,
                )
                EchoSwitchRow(
                    title = "色散",
                    subtitle = "玻璃边缘的红蓝光谱分离",
                    checked = settings.glassChromatic,
                    onCheckedChange = viewModel::setGlassChromatic,
                )
            }
            EchoSliderRow(
                title = "高光强度",
                value = settings.glassHighlight,
                valueRange = 0f..1f,
                valueLabel = "${(settings.glassHighlight * 100).roundToInt()}%",
                onValueChange = viewModel::setGlassHighlight,
            )
            EchoSliderRow(
                title = "染色浓度",
                value = settings.glassTint,
                valueRange = 0f..1f,
                valueLabel = "${(settings.glassTint * 100).roundToInt()}%",
                onValueChange = viewModel::setGlassTint,
            )
            EchoSegmentedRow(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.settings_theme),
                options = themeLabels,
                selectedIndex = settings.themeMode.ordinal,
                onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
            )
        }

        SectionLabel("背景")
        SectionCard {
            EchoRow(
                icon = Icons.Rounded.Image,
                title = "选择背景图片",
                subtitle = "玻璃会折射这张图，液态玻璃效果更明显",
                showChevron = true,
                onClick = { imagePicker.launch(arrayOf("image/*")) },
            )
            EchoRow(
                icon = Icons.Rounded.RestartAlt,
                title = "删除壁纸（恢复默认渐变）",
                subtitle = "删除当前壁纸文件并回到内置的柔和光斑背景",
                showChevron = true,
                onClick = viewModel::clearBackgroundImage,
            )
        }

        SectionLabel(stringResource(R.string.settings_section_general))
        SectionCard {
            EchoRow(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.settings_keepalive),
                subtitle = "让回声在后台不被系统清理",
                showChevron = true,
                onClick = onOpenKeepAlive,
            )
            EchoRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.settings_about),
                subtitle = "音频与转写全部本地处理",
                showChevron = true,
                onClick = onOpenAbout,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "回声 Echo · 记忆回溯",
            style = EchoType.caption,
            color = colors.tertiaryLabel,
            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 24.dp),
        )
    }
}

