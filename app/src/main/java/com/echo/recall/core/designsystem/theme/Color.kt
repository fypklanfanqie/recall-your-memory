package com.echo.recall.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * iOS 系统色板（浅色/深色）与语义 token。
 * 液态玻璃相关 token 供 GlassSurface 使用。
 */
@Immutable
data class EchoColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val surfaceElevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val live: Color,
)

// ---- iOS system colors ----
private val IosBlue = Color(0xFF007AFF)
private val IosBlueDark = Color(0xFF0A84FF)
private val IosGreen = Color(0xFF34C759)
private val IosGreenDark = Color(0xFF30D158)
private val IosOrange = Color(0xFFFF9500)
private val IosOrangeDark = Color(0xFFFF9F0A)
private val IosRed = Color(0xFFFF3B30)
private val IosRedDark = Color(0xFFFF453A)

val LightEchoColors = EchoColors(
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceSecondary = Color(0xFFE5E5EA),
    surfaceElevated = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x493C3C43),
    accent = IosBlue,
    onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0xCCF7F7FB),
    glassBorder = Color(0x40FFFFFF),
    glassHighlight = Color(0x66FFFFFF),
    success = IosGreen,
    warning = IosOrange,
    danger = IosRed,
    live = IosRed,
)

val DarkEchoColors = EchoColors(
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceSecondary = Color(0xFF2C2C2E),
    surfaceElevated = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x5A545458),
    accent = IosBlueDark,
    onAccent = Color(0xFFFFFFFF),
    glassTint = Color(0xB31C1C1E),
    glassBorder = Color(0x33FFFFFF),
    glassHighlight = Color(0x3DFFFFFF),
    success = IosGreenDark,
    warning = IosOrangeDark,
    danger = IosRedDark,
    live = IosRedDark,
)

/** 可选强调色（设置里可切换，M4 接入 UI） */
enum class AccentOption(val light: Color, val dark: Color, val label: String) {
    BLUE(Color(0xFF007AFF), Color(0xFF0A84FF), "蓝"),
    PURPLE(Color(0xFFAF52DE), Color(0xFFBF5AF2), "紫"),
    PINK(Color(0xFFFF2D55), Color(0xFFFF375F), "粉"),
    ORANGE(Color(0xFFFF9500), Color(0xFFFF9F0A), "橙"),
    GREEN(Color(0xFF34C759), Color(0xFF30D158), "绿"),
    TEAL(Color(0xFF30B0C7), Color(0xFF40C8E0), "青"),
}

val LocalEchoColors = staticCompositionLocalOf { LightEchoColors }
