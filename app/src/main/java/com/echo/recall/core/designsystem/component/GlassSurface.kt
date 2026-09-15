package com.echo.recall.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echo.recall.core.designsystem.theme.LocalEchoColors

/**
 * 设置式卡片（对齐 ZhiweiMath 的 CardGroup / iOS Inset Grouped List）：
 * 实心表面色 + 发丝描边。
 *
 * ⚠ 卡片【不做玻璃】：卡片位于 GlassHost 的录制树内部，玻璃必须是被录制内容的
 * 兄弟节点（铁律 1）——否则滚动时卡片会折射出页面其他帧的内容（“两层”鬼影）。
 * 玻璃只出现在 overlay（Dock / 回溯按钮由 GlassHost overlay 承载）。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color? = null,
    borderWidth: Dp = 0.5.dp,
    elevation: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalEchoColors.current
    val base = tint ?: colors.surface
    Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, clip = false)
            .background(base.copy(alpha = 0.92f), shape)
            .border(width = borderWidth, color = colors.separator, shape = shape),
    ) {
        content()
    }
}
