package com.echo.recall.core.designsystem.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
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

/**
 * 液态参数（设置页可调）。
 *
 * ⚠ 折射用「**相对尺寸的比例**」而不是绝对 dp —— 这是照搬官方 Playground 的做法：
 * ```
 * lens(
 *     refractionHeight = fraction * minDimension * 0.5f,
 *     refractionAmount = fraction * minDimension,
 *     depthEffect = true,
 *     chromaticAberration = <开关>,
 * )
 * ```
 * 官方文档要求 `refractionHeight ∈ [0, shape.minCornerRadius]`、
 * `refractionAmount ∈ [0, size.minDimension]`。用比例恰好天然满足这两个上界，
 * 且效果随元素尺寸自适应。
 *
 * 反面教材（v1.0 的实际 bug）：折射用绝对 dp（用户设成了 3dp），
 * 于是折射带只有 ~2dp 宽，色散的红蓝分离落在亚像素级别 → **完全看不见**。
 */
@Immutable
data class GlassParams(
    val mode: GlassMode = GlassMode.LIQUID,
    val blurRadiusDp: Float = 18f,
    /** 折射高度比例：refractionHeight = fraction × minDimension × 0.5 */
    val refractionHeightFraction: Float = DEFAULT_REFRACTION_FRACTION,
    /** 折射强度比例：refractionAmount = fraction × minDimension */
    val refractionAmountFraction: Float = DEFAULT_REFRACTION_FRACTION,
    val chromaticAberration: Boolean = true,
    val highlightAlpha: Float = 0.7f,
    val tintAlpha: Float = 0.25f,
    val vibrancy: Boolean = true,
) {
    companion object {
        const val MIN_BLUR = 0f
        const val MAX_BLUR = 40f
        const val MIN_REFRACTION = 0f
        const val MAX_REFRACTION = 1f

        /** 与官方 Playground 一致的默认折射比例 */
        const val DEFAULT_REFRACTION_FRACTION = 0.2f

        /**
         * 旧版（绝对 dp）→ 新版（比例）的换算基准。
         * 旧默认 24dp 对上新默认 0.2 → 每 0.2 比例 = 24dp，即 120dp 对应 1.0。
         */
        const val LEGACY_DP_PER_FRACTION = 120f

        /** 旧版 dp 值迁移成新版比例 */
        fun legacyDpToFraction(dp: Float): Float =
            (dp / LEGACY_DP_PER_FRACTION).coerceIn(MIN_REFRACTION, MAX_REFRACTION)
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
 * 玻璃表面修饰符（基于正版 Kyant0 backdrop-android 2.0.1）。
 *
 * **这是全 App 唯一的玻璃实现**，效果调用顺序与语义严格照搬官方示例
 * （`GlassPlaygroundContent` / `LiquidButton` / `LiquidBottomTabs`）：
 *
 * ```
 * effects = {
 *     vibrancy()                       // ① color filter（官方：顺序必须 color filter ⇒ blur ⇒ lens）
 *     blur(blurPx)                     // ② blur
 *     lens(                            // ③ lens（折射 + 可选色散）
 *         refractionHeight  = fracH * size.minDimension * 0.5f,
 *         refractionAmount  = fracA * size.minDimension,
 *         depthEffect       = true,
 *         chromaticAberration = params.chromaticAberration,
 *     )
 * }
 * ```
 *
 * 关键点（对照官方仓库）：
 * - 折射用**比例 × 元素尺寸**，天然满足官方的上界约束；绝对 dp 会让小元素失去效果。
 * - `depthEffect = true`（官方 Playground 传的就是 true；v1.0 从来没传）。
 * - `chromaticAberration` 会切换到折射+色散着色器（`RefractionWithDispersion`），
 *   只有折射带足够宽时才看得见红蓝分离。
 * - `lens` 只在 API 33+ 且形状受支持时生效（官方文档：需 RuntimeShader）。
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    surfaceColor: Color,
    innerShadowRadius: Dp? = null,
    refraction: Boolean = true,
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
                    // ① color filter
                    if (params.vibrancy) vibrancy()
                    // ② blur
                    if (blurPx > 0f) blur(blurPx)
                    // ③ lens：折射 + 色散（比例 × 尺寸，保证可见性）
                    if (refraction && isLensShapeSupported(shape) &&
                        params.refractionHeightFraction > 0f &&
                        params.refractionAmountFraction > 0f
                    ) {
                        val minDimension = size.minDimension
                        lens(
                            refractionHeight = params.refractionHeightFraction * minDimension * 0.5f,
                            refractionAmount = params.refractionAmountFraction * minDimension,
                            depthEffect = true,
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
                onDrawSurface = {
                    drawRect(surfaceColor.copy(alpha = params.tintAlpha))
                },
            )
        }

        mode == GlassMode.FROSTED && backdrop != null -> {
            // 毛玻璃档 = 只有模糊（官方：RenderEffect 需 API 31+）。
            // 同样「所见即所得」：模糊为 0 就不模糊。
            val blurPx = with(density) { params.blurRadiusDp.dp.toPx() }
            this.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (size.isSpecified && blurPx > 0f) blur(blurPx)
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

/**
 * `lens` 只支持 [CornerBasedShape] 与 Kyant0 的 `RoundedRectangularShape`；
 * 其他形状库会直接抛异常。这里显式判断，避免换形状时崩在 draw 阶段。
 */
private fun isLensShapeSupported(shape: Shape): Boolean =
    shape is com.kyant.shapes.RoundedRectangularShape || shape is CornerBasedShape

