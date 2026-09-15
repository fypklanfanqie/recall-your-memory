package com.echo.recall.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * iOS 字号层级（大标题 / 正文 17 / 脚注 13 …），中文走系统字体。
 */
object EchoType {
    val largeTitle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = 0.37.sp)
    val title1 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.36.sp)
    val title2 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = -0.26.sp)
    val title3 = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = -0.45.sp)
    val headline = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = -0.41.sp)
    val body = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = -0.41.sp)
    val callout = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = -0.32.sp)
    val subhead = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = -0.24.sp)
    val footnote = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = -0.08.sp)
    val caption = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val dockLabel = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp)
}

val EchoTypography = Typography(
    displayLarge = EchoType.largeTitle,
    headlineLarge = EchoType.title1,
    headlineMedium = EchoType.title2,
    headlineSmall = EchoType.title3,
    titleLarge = EchoType.headline,
    titleMedium = EchoType.callout,
    titleSmall = EchoType.subhead,
    bodyLarge = EchoType.body,
    bodyMedium = EchoType.callout,
    bodySmall = EchoType.footnote,
    labelLarge = EchoType.headline,
    labelMedium = EchoType.subhead,
    labelSmall = EchoType.caption,
)
