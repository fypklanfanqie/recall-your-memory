package com.echo.recall.core.designsystem.glass

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * 液态玻璃渲染效果（自研，API 级别降级）：
 *
 *  - API 33+ (AGSL)：折射 + 边缘透镜 + 色散
 *  - API 31-32     ：高斯模糊（RenderEffect.blur）
 *  - API < 31      ：无效果，退化为半透明平面
 *
 * 说明：Kyant0 的 Backdrop 库（AndroidLiquidGlass）需要 Kotlin 2.3.10 + Compose 1.10.3，
 * 与本项目 Kotlin 2.0.21 / Compose 1.7.6 的元数据不兼容，因此按同样思路自行实现：
 * 玻璃层内部重绘同一份背景（[EchoWallpaper]），再用 RenderEffect 做模糊与折射，
 * 视觉上与「采样背后内容」等价，因为背景是我们自己绘制的确定性渐变。
 */
enum class GlassLevel {
    /** 无玻璃效果：平面半透明 */
    NONE,

    /** 模糊（API 31+） */
    BLUR,

    /** 折射 + 模糊（API 33+） */
    REFRACTION,
}

object GlassSupport {

    val level: GlassLevel
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> GlassLevel.REFRACTION
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassLevel.BLUR
            else -> GlassLevel.NONE
        }

    @RequiresApi(Build.VERSION_CODES.S)
    fun blurEffect(radiusPx: Float): RenderEffect? = runCatching {
        android.graphics.RenderEffect
            .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }.getOrNull()

    /** 折射：把玻璃层内部的内容按边缘透镜形变采样 */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun refractionEffect(sizePx: FloatArray, refractionPx: Float, chromatic: Boolean): RenderEffect? =
        runCatching {
            val shader = RuntimeShader(SHADER)
            shader.setFloatUniform("size", sizePx[0], sizePx[1])
            shader.setFloatUniform("refraction", refractionPx)
            shader.setFloatUniform("chromatic", if (chromatic) 1f else 0f)
            android.graphics.RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }.getOrNull()

    /**
     * 边缘透镜：靠近边缘时向内折射并轻微放大，中心几乎不动 —— iOS 26 液态玻璃的观感。
     * 坐标 coord 为该层像素坐标，content 为层内容。
     */
    private const val SHADER = """
        uniform shader content;
        uniform float2 size;
        uniform float refraction;
        uniform float chromatic;

        half4 main(float2 coord) {
            float2 halfSize = max(size * 0.5, float2(1.0));
            float2 p = coord - halfSize;
            float2 nd = abs(p) / halfSize;
            float edge = max(nd.x, nd.y);
            float band = smoothstep(1.0 - clamp(refraction / max(size.y, 1.0), 0.05, 0.6), 1.0, edge);
            float2 dir = p / max(length(p), 1.0);
            float2 offset = dir * band * refraction * 0.55;
            float2 uv = clamp(coord - offset, float2(0.0), size - float2(1.0));

            half4 base = content.eval(uv);
            if (chromatic > 0.5) {
                // 轻微色散：红蓝通道反向偏移，模拟玻璃边缘的光谱分离
                half r = content.eval(clamp(uv + dir * band * 0.8, float2(0.0), size - float2(1.0))).r;
                half b = content.eval(clamp(uv - dir * band * 0.8, float2(0.0), size - float2(1.0))).b;
                base = half4(r, base.g, b, base.a);
            }
            return base;
        }
    """
}
