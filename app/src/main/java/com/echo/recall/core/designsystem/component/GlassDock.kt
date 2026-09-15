package com.echo.recall.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echo.recall.core.designsystem.glass.GlassMode
import com.echo.recall.core.designsystem.glass.LocalGlassMode
import com.echo.recall.core.designsystem.glass.LocalEchoBackdrop
import com.echo.recall.core.designsystem.glass.LocalGlassParams
import com.echo.recall.core.designsystem.glass.liquidGlassSurface
import com.echo.recall.core.designsystem.theme.EchoRadius
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.nav.EchoTab
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

/**
 * 液态玻璃 Dock（照搬 ZhiweiMath 的 LiquidDock / LiquidBottomTabs 模式）：
 * - 整条胶囊玻璃（vibrancy + blur + lens 真实折射），innerShadow 8dp；
 * - 选中滑块是【玻璃上玻璃】：dock 的 drawBackdrop 传 exportedBackdrop = subBackdrop，
 *   滑块采样 subBackdrop（铁律 2：绝不给 dock 套 layerBackdrop）；
 * - 选中图标放大 1.15 + 主色 tint；
 * - FROSTED 降级为毛玻璃胶囊；PLAIN 为半透明胶囊。
 */
@Composable
fun GlassDock(
    items: List<EchoTab>,
    currentRoute: String?,
    onSelect: (EchoTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    val params = LocalGlassParams.current
    val mode = LocalGlassMode.current
    val isLiquid = mode == GlassMode.LIQUID && LocalEchoBackdrop.current != null
    val dockSurface = colors.surface

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(62.dp),
    ) {
        val tabWidth = maxWidth / items.size.coerceAtLeast(1)
        val sliderWidth = (tabWidth - 10.dp).coerceAtMost(64.dp)
        val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
        val pillX by animateDpAsState(
            targetValue = tabWidth * selectedIndex + (tabWidth - sliderWidth) / 2,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
            label = "dockPill",
        )

        // ── dock 整条玻璃（玻璃上玻璃：导出 subBackdrop 给滑块采样）──
        val subBackdrop = rememberLayerBackdrop()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLiquid) {
                        Modifier.liquidGlassSurface(
                            shape = RoundedCornerShape(EchoRadius.dock),
                            surfaceColor = dockSurface,
                            innerShadowRadius = 8.dp,
                            exportedBackdrop = subBackdrop,
                        )
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(EchoRadius.dock))
                            .background(
                                if (mode == GlassMode.FROSTED) colors.surface.copy(alpha = 0.75f)
                                else colors.surface.copy(alpha = 0.92f),
                                RoundedCornerShape(EchoRadius.dock),
                            )
                    },
                ),
        ) {
            // 选中滑块（先声明 → 画在图标层之下）：玻璃上玻璃采样 subBackdrop
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillX)
                    .width(sliderWidth)
                    .height(46.dp)
                    .then(
                        if (isLiquid) {
                            Modifier.drawBackdrop(
                                backdrop = subBackdrop,
                                shape = { RoundedCornerShape(50) },
                                effects = {
                                    if (size.isSpecified) {
                                        lens(
                                            refractionHeight = params.refractionHeightDp.dp.toPx() * 0.6f,
                                            refractionAmount = params.refractionAmountDp.dp.toPx() * 0.6f,
                                            chromaticAberration = params.chromaticAberration,
                                        )
                                    }
                                },
                                highlight = { Highlight.Default.copy(alpha = params.highlightAlpha * 0.7f) },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.30f)) },
                            )
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(50))
                        },
                    ),
            )

            Row(Modifier.fillMaxSize()) {
                items.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                        label = "dockIcon",
                    )
                    val tint by animateColorAsState(
                        targetValue = if (selected) colors.accent else colors.secondaryLabel,
                        label = "dockTint",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = stringResource(tab.labelRes),
                                tint = tint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Text(
                                text = stringResource(tab.labelRes),
                                style = EchoType.dockLabel,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

