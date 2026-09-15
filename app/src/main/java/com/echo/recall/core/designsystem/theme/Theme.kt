package com.echo.recall.core.designsystem.theme

import com.kyant.shapes.RoundedRectangle
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** 当前是否深色（壁纸与玻璃需要知道明暗）。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** 当前是否深色（玻璃着色用）。 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** iOS 连续曲率（G2 squircle）圆角体系 —— 来自 Kyant0/Shapes */
object EchoRadius {
    val row = 12.dp
    val card = 16.dp
    val sheet = 22.dp
    val dock = 30.dp
    val hero = 44.dp
}

@Composable
fun EchoTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val echoColors = if (darkTheme) DarkEchoColors else LightEchoColors
    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = echoColors.accent,
            onPrimary = echoColors.onAccent,
            background = echoColors.background,
            onBackground = echoColors.label,
            surface = echoColors.surface,
            onSurface = echoColors.label,
            surfaceVariant = echoColors.surfaceSecondary,
            onSurfaceVariant = echoColors.secondaryLabel,
            outline = echoColors.separator,
            error = echoColors.danger,
        )
    } else {
        lightColorScheme(
            primary = echoColors.accent,
            onPrimary = echoColors.onAccent,
            background = echoColors.background,
            onBackground = echoColors.label,
            surface = echoColors.surface,
            onSurface = echoColors.label,
            surfaceVariant = echoColors.surfaceSecondary,
            onSurfaceVariant = echoColors.secondaryLabel,
            outline = echoColors.separator,
            error = echoColors.danger,
        )
    }

    // Material3 默认形状（M3 内部组件用）；业务组件统一用 Shapes 库的连续曲率圆角
    val echoShapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(EchoRadius.row),
        medium = RoundedCornerShape(EchoRadius.card),
        large = RoundedCornerShape(EchoRadius.sheet),
        extraLarge = RoundedCornerShape(EchoRadius.dock),
    )

    CompositionLocalProvider(
        LocalEchoColors provides echoColors,
        LocalIsDarkTheme provides darkTheme,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = EchoTypography,
            shapes = echoShapes,
            content = content,
        )
    }
}
