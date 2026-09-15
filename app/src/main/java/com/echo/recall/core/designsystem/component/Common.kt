package com.echo.recall.core.designsystem.component

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echo.recall.core.designsystem.theme.EchoRadius
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors


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
        shape = RoundedCornerShape(EchoRadius.card),
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

/** 通用行：图标 + 标题 + 副标题 + 右侧内容 */
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: colors.secondaryLabel,
                modifier = Modifier.size(21.dp),
            )
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
    EchoRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.surfaceSecondary,
            ),
        )
    }
}

/** iOS 分段控件 */
@Composable
fun EchoSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surfaceSecondary)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = EchoType.footnote,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.label else colors.secondaryLabel,
                )
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

/** 首页英雄按钮：回溯记忆 */
@Composable
fun HeroRecallButton(
    label: String,
    hint: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalEchoColors.current
    val transition = rememberInfiniteTransition(label = "heroPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (enabled) 1.04f else 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "heroPulseValue",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassSurface(
            modifier = Modifier
                .size(148.dp)
                .scale(if (enabled) pulse else 1f)
                .clickable(enabled = enabled, onClick = onClick),
            shape = CircleShape,
            elevation = 22.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = if (enabled) colors.accent else colors.tertiaryLabel,
                    modifier = Modifier.size(38.dp),
                )
                Spacer(Modifier.height(8.dp))
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

