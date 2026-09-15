package com.echo.recall.core.designsystem.glass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 壁纸规格：玻璃元素需要知道「窗口有多大 / 明暗 / 强调色」，
 * 才能把同一份渐变按自己的窗口坐标精确重绘（平面降级路径使用）。
 */
@Immutable
data class EchoWallpaperSpec(
    val windowOffset: Offset = Offset.Zero,
    val windowSize: Size = Size.Zero,
    val dark: Boolean = false,
    val accent: Color = Color(0xFF007AFF),
)

val LocalEchoWallpaper = staticCompositionLocalOf { EchoWallpaperSpec() }

/**
 * 应用背景层：
 * - 通过 [Modifier.layerBackdrop] 把内容记录进 [LayerBackdrop]，供所有玻璃组件折射；
 * - [backgroundImagePath] 非空时绘制用户自选图片（cover 填充），否则绘制默认渐变光斑。
 */
@Composable
fun EchoBackground(
    dark: Boolean,
    modifier: Modifier = Modifier,
    backgroundImagePath: String = "",
    content: @Composable () -> Unit,
) {
    val colors = LocalEchoColors.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Size.Zero) }

    val spec = remember(offset, size, dark, colors.accent) {
        EchoWallpaperSpec(
            windowOffset = offset,
            windowSize = size,
            dark = dark,
            accent = colors.accent,
        )
    }

    val image: ImageBitmap? = produceState<ImageBitmap?>(null, backgroundImagePath) {
        value = null
        if (backgroundImagePath.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                runCatching { decodeCover(backgroundImagePath, 1080, 2520) }.getOrNull()
            }
        }
    }.value

    androidx.compose.runtime.CompositionLocalProvider(LocalEchoWallpaper provides spec) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    offset = it.positionInWindow()
                    size = it.size.toSize()
                }
                .drawBehind {
                    val bmp = image
                    if (bmp != null) {
                        drawImageCover(bmp)
                    } else {
                        drawEchoWallpaper(spec)
                    }
                },
        ) {
            content()
        }
    }
}

private fun decodeCover(path: String, targetW: Int, targetH: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) {
        sample *= 2
    }
    val bmp: Bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    return bmp.asImageBitmap()
}

/** cover 模式铺满画布（等比缩放 + 居中裁切） */
private fun DrawScope.drawImageCover(image: ImageBitmap) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    if (iw <= 0f || ih <= 0f) return
    val scale = max(size.width / iw, size.height / ih)
    val dw = (iw * scale).roundToInt().coerceAtLeast(1)
    val dh = (ih * scale).roundToInt().coerceAtLeast(1)
    val dx = ((size.width - dw) / 2f).roundToInt()
    val dy = ((size.height - dh) / 2f).roundToInt()
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(dx, dy),
        dstSize = IntSize(dw, dh),
    )
}

/** 在整块画布上绘制壁纸（背景层用） */
fun DrawScope.drawEchoWallpaper(spec: EchoWallpaperSpec) {
    drawEchoWallpaperSlice(
        topLeft = Offset.Zero,
        sliceSize = size,
        spec = spec,
    )
}

/**
 * 绘制壁纸中与 [topLeft]/[sliceSize] 对应的一片（平面降级路径用）。
 * 光斑几何完全由 spec.windowSize 决定，因此任何元素重绘都能与背景严丝合缝。
 */
fun DrawScope.drawEchoWallpaperSlice(
    topLeft: Offset,
    sliceSize: Size,
    spec: EchoWallpaperSpec,
) {
    if (sliceSize.width <= 0f || sliceSize.height <= 0f) return
    val windowW = if (spec.windowSize.width > 0f) spec.windowSize.width else sliceSize.width
    val windowH = if (spec.windowSize.height > 0f) spec.windowSize.height else sliceSize.height
    val origin = spec.windowOffset

    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                if (spec.dark) Color(0xFF07080C) else Color(0xFFF7F8FC),
                if (spec.dark) Color(0xFF0E1117) else Color(0xFFEFF3FA),
            ),
            start = origin,
            end = origin + Offset(windowW, windowH),
        ),
        topLeft = topLeft,
        size = sliceSize,
    )

    val blobs = listOf(
        Blob(0.16f, 0.10f, 0.62f, spec.accent.copy(alpha = if (spec.dark) 0.30f else 0.24f)),
        Blob(0.88f, 0.26f, 0.55f, Color(0xFF7C5CFF).copy(alpha = if (spec.dark) 0.22f else 0.15f)),
        Blob(0.42f, 0.94f, 0.66f, Color(0xFF32D0C4).copy(alpha = if (spec.dark) 0.18f else 0.13f)),
        Blob(0.06f, 0.78f, 0.45f, Color(0xFFFFB84D).copy(alpha = if (spec.dark) 0.14f else 0.10f)),
    )

    val sliceRight = topLeft.x + sliceSize.width
    val sliceBottom = topLeft.y + sliceSize.height
    for (blob in blobs) {
        val cx = origin.x + windowW * blob.xFraction
        val cy = origin.y + windowH * blob.yFraction
        val radius = maxOf(windowW, windowH) * blob.radiusFraction
        val intersects = cx + radius > topLeft.x && cx - radius < sliceRight &&
            cy + radius > topLeft.y && cy - radius < sliceBottom
        if (!intersects) continue

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(blob.color, Color.Transparent),
                center = Offset(cx, cy),
                radius = radius,
            ),
            topLeft = topLeft,
            size = sliceSize,
        )
    }
}

private data class Blob(
    val xFraction: Float,
    val yFraction: Float,
    val radiusFraction: Float,
    val color: Color,
)
