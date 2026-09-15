package com.echo.recall.core.designsystem.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.designsystem.theme.LocalIsDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * 玻璃系统（照搬 ZhiweiMath 的成熟架构 + Kyant0/AndroidLiquidGlass vendored 源码）。
 *
 * ⚠ 崩溃/鬼影规避铁律（ZhiweiMath 实测总结，本项目真机复现验证）：
 * 1. 玻璃元素绝不能出现在 Modifier.layerBackdrop() 包裹的内容树内部——
 *    玻璃必须是被录制内容的【兄弟节点】（GlassHost 保证该布局）。
 *    本项目踩坑：页面卡片做玻璃 → 滚动时卡片折射出页面其他帧的内容（“两层”鬼影）。
 * 2. 玻璃上玻璃（dock 里的滑块）：父玻璃 drawBackdrop 传 exportedBackdrop，
 *    子玻璃采样它；绝不给父玻璃套 layerBackdrop。
 * 3. attach 阶段 scope.size 尚未测量，lens() 读 cornerRadii 会崩 → Lens.kt 已加
 *    size.isSpecified 守卫（vendored 适配）。
 */

/** 玻璃模式：LIQUID（真实折射，API 33+）/ FROSTED（毛玻璃，API 31+）/ PLAIN（半透明） */
enum class GlassMode(val label: String) {
    PLAIN("半透明"),
    FROSTED("毛玻璃"),
    LIQUID("液态玻璃");

    companion object {
        fun fromId(id: String): GlassMode =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: FROSTED
    }
}

/** 实际玻璃模式解析（API 分档降级） */
fun resolveGlassMode(userMode: GlassMode, sdkInt: Int = Build.VERSION.SDK_INT): GlassMode =
    when (userMode) {
        GlassMode.LIQUID -> if (sdkInt >= 33) GlassMode.LIQUID else if (sdkInt >= 31) GlassMode.FROSTED else GlassMode.PLAIN
        GlassMode.FROSTED -> if (sdkInt >= 31) GlassMode.FROSTED else GlassMode.PLAIN
        GlassMode.PLAIN -> GlassMode.PLAIN
    }

/** 液态参数（设置页可调） */
@Immutable
data class GlassParams(
    val mode: GlassMode = GlassMode.LIQUID,
    val blurRadiusDp: Float = 18f,
    val refractionHeightDp: Float = 24f,
    val refractionAmountDp: Float = 24f,
    val chromaticAberration: Boolean = true,
    val highlightAlpha: Float = 0.7f,
    val tintAlpha: Float = 0.25f,
    val vibrancy: Boolean = true,
) {
    companion object {
        const val MIN_BLUR = 0f
        const val MAX_BLUR = 40f
        const val MIN_REFRACTION = 0f
        const val MAX_REFRACTION = 40f
    }
}

/** 屏幕级 backdrop 录制层（GlassHost 提供；玻璃表面只读它，绝不嵌进内容树） */
val LocalEchoBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 当前玻璃模式与参数（AppRoot 级提供） */
val LocalGlassMode = staticCompositionLocalOf { GlassMode.FROSTED }
val LocalGlassParams = staticCompositionLocalOf { GlassParams() }

/**
 * 应用级玻璃上下文：只提供 CompositionLocals，不做任何录制——
 * 每屏自己的录制宿主负责录制，保证玻璃永远是被录制内容的兄弟节点（铁律 1）。
 */
@Composable
fun ProvideGlassContext(
    params: GlassParams,
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    val mode = resolveGlassMode(params.mode)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGlassMode provides mode,
        LocalGlassParams provides params.copy(mode = mode),
        LocalIsDarkTheme provides isDark,
    ) {
        content()
    }
}

/**
 * 屏幕级玻璃宿主（铁律 1 的结构性保证）：
 * - LIQUID/FROSTED：内容树挂 layerBackdrop 录制；overlay 里的玻璃采样 LocalEchoBackdrop。
 * - PLAIN：纯半透明，无录制。
 */
@Composable
fun GlassHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    when (LocalGlassMode.current) {
        GlassMode.LIQUID, GlassMode.FROSTED -> {
            val backdrop = rememberLayerBackdrop()
            androidx.compose.runtime.CompositionLocalProvider(LocalEchoBackdrop provides backdrop) {
                Box(modifier) {
                    Box(Modifier.layerBackdrop(backdrop)) { content() }
                    overlay()
                }
            }
        }

        else -> Box(modifier) { content(); overlay() }
    }
}

/**
 * 玻璃表面修饰符：
 * - LIQUID：drawBackdrop 真实折射（vibrancy → colorControls，blur → BlurEffect，
 *   lens → SDF 折射+色散；形状须为 CornerBasedShape，否则跳过 lens 只模糊）。
 * - FROSTED：仅 blur（毛玻璃）。
 * - PLAIN / 无录制层：scrim 兜底。
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    surfaceColor: Color,
    innerShadowRadius: Dp? = null,
    refraction: Boolean = true,
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    val mode = LocalGlassMode.current
    val params = LocalGlassParams.current
    val isDark = LocalIsDarkTheme.current
    val backdrop: Backdrop? = LocalEchoBackdrop.current
    val colors = LocalEchoColors.current
    val density = LocalDensity.current

    return when {
        mode == GlassMode.LIQUID && backdrop != null -> {
            val blurPx = with(density) { params.blurRadiusDp.dp.toPx() }
            this.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (!size.isSpecified) return@drawBackdrop
                    if (params.vibrancy) vibrancy()
                    if (blurPx > 0f) blur(blurPx)
                    if (refraction &&
                        params.refractionHeightDp > 0f &&
                        params.refractionAmountDp > 0f
                    ) {
                        lens(
                            refractionHeight = with(density) { params.refractionHeightDp.dp.toPx() },
                            refractionAmount = with(density) { params.refractionAmountDp.dp.toPx() },
                            chromaticAberration = params.chromaticAberration,
                        )
                    }
                },
                highlight = { Highlight.Default.copy(alpha = params.highlightAlpha) },
                shadow = { Shadow.Default },
                innerShadow = if (innerShadowRadius != null) {
                    { InnerShadow(radius = innerShadowRadius, alpha = 0.15f) }
                } else {
                    null
                },
                exportedBackdrop = exportedBackdrop,
                onDrawSurface = {
                    drawRect(colors.glassTint.copy(alpha = params.tintAlpha))
                },
            )
        }

        mode == GlassMode.FROSTED && backdrop != null -> {
            val blurPx = with(density) { params.blurRadiusDp.dp.toPx() }.coerceAtLeast(with(density) { 1.dp.toPx() })
            this.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (size.isSpecified) blur(blurPx)
                },
                onDrawSurface = {
                    drawRect(colors.glassTint.copy(alpha = (params.tintAlpha + 0.35f).coerceAtMost(0.8f)))
                },
            )
        }

        else -> {
            val base = if (isDark) Color(0xCC1C1C1E) else Color(0xCCF7F7FB)
            this
                .background(base, shape)
                .border(0.5.dp, colors.separator, shape)
        }
    }
}

