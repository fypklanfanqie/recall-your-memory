package com.echo.recall.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.echo.recall.core.designsystem.glass.GlassMode
import com.echo.recall.core.designsystem.glass.LocalEchoBackdrop
import com.echo.recall.core.designsystem.glass.LocalGlassMode
import com.echo.recall.core.designsystem.glass.LocalGlassParams
import com.echo.recall.core.designsystem.glass.liquidGlassSurface
import com.echo.recall.core.designsystem.theme.EchoRadius
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.designsystem.theme.LocalIsDarkTheme
import com.echo.recall.nav.EchoTab
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/**
 * 液态玻璃 Dock（正版 Kyant0 backdrop 2.0，照搬官方 LiquidBottomTabs 模式）：
 * - 整条胶囊玻璃（vibrancy + blur + lens 真实折射）；
 * - 「玻璃上玻璃」官方做法：tabsBackdrop 录制一份 alpha=0 的染色图标行，
 *   选中滑块采样 rememberCombinedBackdrop(屏幕, 染色行)；
 * - 选中图标放大 1.12 + 主色 tint；
 * - FROSTED/PLAIN 降级为实心胶囊 + 白色滑块。
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
    val screenBackdrop = LocalEchoBackdrop.current
    val isLiquid = mode == GlassMode.LIQUID && screenBackdrop != null

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(64.dp),
    ) {
        val count = items.size.coerceAtLeast(1)
        val tabWidth = maxWidth / count
        val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
        val sliderProgress by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
            label = "dockSlider",
        )
        // 按住选中 tab 时滑块折射浮现（官方行为：静止柔和，按压时液态感出现）
        var pressingSelected by remember { mutableStateOf(false) }
        val pressProgress by animateFloatAsState(
            targetValue = if (pressingSelected) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
            label = "dockPress",
        )

        if (isLiquid && screenBackdrop != null) {
            val dockShape = RoundedCornerShape(EchoRadius.dock)
            // 厚霜玻璃：模糊取设置值与 22dp 的较大者，表面更实，避免「截断」的生硬透视
            val dockBlur = maxOf(params.blurRadiusDp, 22f)
            val containerColor = colors.surface.copy(alpha = 0.45f)
            val tabsBackdrop = rememberLayerBackdrop()

            // 1) 可见的玻璃 Dock 行（承载点击）
            Row(
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = screenBackdrop,
                        shape = { dockShape },
                        effects = {
                            if (!size.isSpecified) return@drawBackdrop
                            vibrancy()
                            blur(dockBlur.dp.toPx())
                            lens(
                                params.refractionHeightDp.dp.toPx() * 0.5f,
                                params.refractionAmountDp.dp.toPx() * 0.5f,
                            )
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { tab -> DockTab(tab, currentRoute, onSelect, onPressChange = { pressingSelected = it }) }
            }

            // 2) 隐形染色副本（供滑块折射出主色图标；alpha(0) 只作用于上屏合成，
            //    layerBackdrop 录制的是图层内容本身——官方技巧）
            Row(
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .drawBackdrop(
                        backdrop = screenBackdrop,
                        shape = { dockShape },
                        effects = {
                            if (!size.isSpecified) return@drawBackdrop
                            vibrancy()
                            blur(params.blurRadiusDp.dp.toPx() * 0.5f)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .padding(4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(colors.accent)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { tab -> DockTab(tab, currentRoute, onSelect, clickable = false) }
            }

            // 3) 选中滑块（玻璃上玻璃，画在最上层）。
            //    官方行为：静止柔和；可水平拖动，拖到哪个 tab 就切到哪个；按压时折射浮现。
            val density = LocalDensity.current
            val isLight = !LocalIsDarkTheme.current
            val tabWidthPx = with(density) { tabWidth.toPx() }
            var dragTabs by remember { mutableStateOf(0f) }
            val visualPosition = (sliderProgress + dragTabs).coerceIn(0f, (count - 1).toFloat())
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = with(density) { (tabWidth * visualPosition + 4.dp).toPx() }
                    }
                    .height(56.dp)
                    .width(tabWidth - 8.dp)
                    .pointerInput(count) {
                        detectHorizontalDragGestures(
                            onDragStart = { pressingSelected = true },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                dragTabs = (dragTabs + amount / tabWidthPx)
                                    .coerceIn(-selectedIndex.toFloat(), (count - 1 - selectedIndex).toFloat())
                            },
                            onDragEnd = {
                                val target = (sliderProgress + dragTabs)
                                    .fastRoundToInt()
                                    .coerceIn(0, count - 1)
                                dragTabs = 0f
                                pressingSelected = false
                                val route = items.getOrNull(target)?.route
                                if (route != null && route != currentRoute) onSelect(items[target])
                            },
                            onDragCancel = {
                                dragTabs = 0f
                                pressingSelected = false
                            },
                        )
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(screenBackdrop, tabsBackdrop),
                        shape = { Capsule() },
                        effects = {
                            if (!size.isSpecified) return@drawBackdrop
                            lens(
                                10f.dp.toPx() * pressProgress,
                                14f.dp.toPx() * pressProgress,
                                chromaticAberration = params.chromaticAberration,
                            )
                        },
                        highlight = { Highlight.Default.copy(alpha = pressProgress) },
                        shadow = { Shadow(alpha = pressProgress * 0.35f) },
                        innerShadow = { InnerShadow(radius = 8.dp * pressProgress, alpha = pressProgress * 0.6f) },
                        onDrawSurface = {
                            drawRect(
                                (if (isLight) Color.Black else Color.White).copy(alpha = 0.10f * (1f - pressProgress))
                            )
                            drawRect(Color.Black.copy(alpha = 0.04f * pressProgress))
                        },
                    ),
            )
        } else {
            // FROSTED / PLAIN：实心胶囊 + 白色滑块
            val dockShape = RoundedCornerShape(EchoRadius.dock)
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(dockShape)
                    .background(
                        if (mode == GlassMode.FROSTED) colors.surface.copy(alpha = 0.75f)
                        else colors.surface.copy(alpha = 0.92f),
                        dockShape,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = tabWidth * sliderProgress + 4.dp)
                        .width(tabWidth - 8.dp)
                        .height(56.dp)
                        .clip(Capsule())
                        .background(Color.White.copy(alpha = 0.35f), Capsule()),
                )
                Row(
                    Modifier.fillMaxSize().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEach { tab -> DockTab(tab, currentRoute, onSelect) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DockTab(
    tab: EchoTab,
    currentRoute: String?,
    onSelect: (EchoTab) -> Unit,
    clickable: Boolean = true,
    onPressChange: ((Boolean) -> Unit)? = null,
) {
    val colors = LocalEchoColors.current
    val selected = currentRoute == tab.route
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed, selected) {
        if (selected) onPressChange?.invoke(pressed)
    }
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else if (pressed) 0.92f else 1f,
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
            .then(
                if (clickable) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { onSelect(tab) }
                } else {
                    Modifier
                }
            ),
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
