package com.echo.recall.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors

/**
 * 删除确认对话框（待办 / 备忘共用）。
 * 只在 [visible] 为 true 时渲染，关闭交给调用方。
 */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "删除",
    dismissLabel: String = "取消",
) {
    if (!visible) return
    val colors = LocalEchoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.label,
        textContentColor = colors.secondaryLabel,
        title = { Text(text = title, style = EchoType.headline) },
        text = {
            if (text.isBlank()) null else Text(
                text = text,
                style = EchoType.subhead,
                maxLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel, color = colors.accent)
            }
        },
    )
}
