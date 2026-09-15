package com.echo.recall.core.designsystem.component

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echo.recall.core.designsystem.theme.EchoRadius
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle


/** 大标题页头（iOS large title） */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = EchoType.largeTitle, color = colors.label)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = EchoType.footnote, color = colors.secondaryLabel)
            }
        }
        actions()
    }
}

/** 分组标题（小号灰色标签） */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val colors = LocalEchoColors.current
    Text(
        text = text,
        style = EchoType.footnote,
        color = colors.secondaryLabel,
        modifier = modifier.padding(start = 32.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** iOS inset grouped 卡片 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedRectangle(EchoRadius.card),
        elevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun RowDivider(startPadding: Int = 16) {
    val colors = LocalEchoColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding.dp)
            .height(0.5.dp)
            .background(colors.separator),
    )
}

/** 通用行：图标芯片 + 标题 + 副标题 + 右侧内容（iOS 设置样式） */
@Composable
fun EchoRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    subtitle: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = LocalEchoColors.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // iOS 设置式图标芯片：圆角方块 + 染色底
            val tint = iconTint ?: colors.accent
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedRectangle(8.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = EchoType.body, color = colors.label)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = EchoType.footnote, color = colors.secondaryLabel)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (showChevron) {
            Text(text = "›", style = EchoType.title3, color = colors.tertiaryLabel)
        }
    }
}

@Composable
fun EchoSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    val colors = LocalEchoColors.current
    val haptic = LocalHapticFeedback.current
    EchoRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = colors.surfaceSecondary,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        },
    )
}

@Composable
fun EchoSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    steps: Int = 0,
) {
    val colors = LocalEchoColors.current
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = EchoType.body, color = colors.label)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = subtitle, style = EchoType.footnote, color = colors.secondaryLabel)
                }
            }
            Text(text = valueLabel, style = EchoType.callout, color = colors.accent, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        EchoSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/** iOS 风格滑杆：连续轨道 + 白色圆钮（自绘，替换 M3 1.4 的新式外观） */
@Composable
private fun EchoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    val colors = LocalEchoColors.current
    val density = LocalDensity.current
    val latestValue by rememberUpdatedState(value)
    val range = valueRange.endInclusive - valueRange.start
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return clamped
        val stepSize = range / (steps + 1)
        return valueRange.start + Math.round((clamped - valueRange.start) / stepSize) * stepSize
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val usable = maxWidth - 22.dp
        val usablePx = with(density) { usable.toPx() }
        val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(Capsule())
                .background(colors.surfaceSecondary),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(Capsule())
                .background(colors.accent),
        )
        Box(
            Modifier
                .offset(x = usable * fraction)
                .size(22.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(0.5.dp, colors.separator, CircleShape)
                .pointerInput(valueRange, steps) {
                    var gestureStart = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { gestureStart = latestValue },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val delta = amount / usablePx * range
                            gestureStart = (gestureStart + delta).coerceIn(valueRange.start, valueRange.endInclusive)
                            onValueChange(snap(gestureStart))
                        },
                    )
                }
                .pointerInput(valueRange, steps) {
                    detectTapGestures { offset ->
                        val f = (offset.x.coerceIn(0f, usablePx) / usablePx)
                        onValueChange(snap(valueRange.start + f * range))
                    }
                },
        )
    }
}

/** 标题在上、分段控件在下的整行设置行（iOS 分组列表样式） */
@Composable
fun EchoSegmentedRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    subtitle: String? = null,
) {
    val colors = LocalEchoColors.current
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                val tint = iconTint ?: colors.accent
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedRectangle(8.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = EchoType.body, color = colors.label)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = subtitle, style = EchoType.footnote, color = colors.secondaryLabel)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        EchoSegmented(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** iOS 分段控件（滑动的白色选中药丸） */
@Composable
fun EchoSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedRectangle(9.dp))
            .background(colors.surfaceSecondary)
            .padding(2.dp)
            .height(32.dp),
    ) {
        val segWidth = maxWidth / options.size.coerceAtLeast(1)
        val pillX by animateDpAsState(
            targetValue = segWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
            label = "segmentedPill",
        )
        Box(
            modifier = Modifier
                .offset(x = pillX)
                .width(segWidth)
                .fillMaxHeight()
                .shadow(2.dp, RoundedRectangle(7.dp), clip = false)
                .clip(RoundedRectangle(7.dp))
                .background(colors.surface),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = EchoType.footnote,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) colors.label else colors.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 空态 */
@Composable
fun EchoEmptyState(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.tertiaryLabel,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = text,
            style = EchoType.callout,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

/** 首页英雄按钮：回溯记忆（径向渐变 + 强调色环 + 按压回弹） */
@Composable
fun HeroRecallButton(
    label: String,
    hint: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalEchoColors.current
    val haptic = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "heroPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (enabled) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "heroPulseValue",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.955f
            enabled -> pulse
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "heroScale",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .scale(scale)
                .shadow(
                    elevation = 26.dp,
                    shape = CircleShape,
                    ambientColor = colors.accent.copy(alpha = 0.30f),
                    spotColor = colors.accent.copy(alpha = 0.38f),
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = if (enabled) 0.16f else 0.06f),
                            colors.surface,
                        ),
                    ),
                )
                .border(
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(colors.glassHighlight, colors.separator))),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = if (enabled) 0.15f else 0.07f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = if (enabled) colors.accent else colors.tertiaryLabel,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = label,
                    style = EchoType.headline,
                    color = if (enabled) colors.label else colors.tertiaryLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = hint,
                style = EchoType.footnote,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

/** 呼吸指示点（聆听状态） */
@Composable
fun LiveDot(color: Color, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val alpha by transition.animateFloat(
        initialValue = if (active) 1f else 0.35f,
        targetValue = if (active) 0.25f else 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveDotAlpha",
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
