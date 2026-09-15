package com.echo.recall.feature.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.designsystem.component.ConfirmDialog
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoRadius
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.util.NoteTextUtils
import com.echo.recall.core.util.TimeFormat

/**
 * 备忘编辑器：返回箭头 + 正文大输入区 + 底部 AI 动作行 + 内联 AI 结果面板。
 *
 * 保存策略：打字停 800ms 自动落盘（见 [NotesViewModel.onContentChange]），
 * 返回时再 flush 一次，保证不丢编辑。
 */
@Composable
internal fun NoteEditorScreen(viewModel: NotesViewModel) {
    val colors = LocalEchoColors.current
    val context = LocalContext.current
    val note by viewModel.editing.collectAsStateWithLifecycle()
    val content by viewModel.editorContent.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf(false) }

    val close = { viewModel.closeEditor() }
    BackHandler { close() }

    val entity = note
    if (entity == null) {
        // 条目已被删除：直接退出编辑器
        Spacer(Modifier.fillMaxSize())
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 页头 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = close) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = colors.accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "备忘",
                    style = EchoType.headline,
                    color = colors.label,
                    maxLines = 1,
                )
                Text(
                    text = "更新于 ${TimeFormat.friendly(entity.updatedAt)} · 自动保存",
                    style = EchoType.caption,
                    color = colors.tertiaryLabel,
                )
            }
            IconButton(onClick = { viewModel.togglePinned(entity) }) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (entity.pinned) "取消置顶" else "置顶",
                    tint = if (entity.pinned) colors.warning else colors.tertiaryLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = colors.danger)
            }
        }

        // ---- 正文 ----
        OutlinedTextField(
            value = content,
            onValueChange = viewModel::onContentChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            placeholder = {
                Text(
                    text = "写下第一条备忘…\n第一行会作为标题",
                    style = EchoType.body,
                    color = colors.tertiaryLabel,
                )
            },
            textStyle = EchoType.body,
            shape = RoundedCornerShape(EchoRadius.card),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceElevated,
                unfocusedContainerColor = colors.surfaceElevated,
                focusedIndicatorColor = colors.separator,
                unfocusedIndicatorColor = colors.separator,
                cursorColor = colors.accent,
                focusedTextColor = colors.label,
                unfocusedTextColor = colors.label,
            ),
        )

        AiResultPanel(
            state = aiState,
            onApply = { text, append -> viewModel.applyAi(text, append) },
            onCopy = { text -> context.copyToClipboard(text) },
            onDismiss = viewModel::dismissAi,
        )

        // ---- 底部动作行 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val busy = aiState is AiUi.Loading
            AiActionButton(
                label = AiAction.SUMMARIZE.label,
                enabled = !busy && content.isNotBlank(),
                onClick = { viewModel.summarize(content) },
            )
            AiActionButton(
                label = AiAction.POLISH.label,
                enabled = !busy && content.isNotBlank(),
                onClick = { viewModel.polish(content) },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${content.length} 字",
                style = EchoType.caption,
                color = colors.tertiaryLabel,
            )
        }
    }

    ConfirmDialog(
        visible = confirmDelete,
        title = "删除这条备忘？",
        text = NoteTextUtils.titleOf(content),
        confirmLabel = "删除",
        onConfirm = {
            confirmDelete = false
            viewModel.delete(entity)
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun AiActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalEchoColors.current
    GlassSurface(
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = if (enabled) colors.accent else colors.tertiaryLabel,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = EchoType.footnote,
                color = if (enabled) colors.accent else colors.tertiaryLabel,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** 内联 AI 结果面板：总结 / 润色（原文与润色稿并排） */
@Composable
private fun AiResultPanel(
    state: AiUi,
    onApply: (String, Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalEchoColors.current
    if (state is AiUi.Idle) return

    SectionLabel(
        when (state) {
            is AiUi.Polished -> "润色对比"
            is AiUi.Result -> state.action.label
            is AiUi.Error -> "AI 出错"
            else -> "AI 处理中"
        },
    )
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(EchoRadius.card),
        elevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (state) {
                AiUi.Idle -> Unit

                AiUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在请求模型…", style = EchoType.footnote, color = colors.secondaryLabel)
                }

                is AiUi.Error -> Column {
                    Text(state.message, style = EchoType.subhead, color = colors.danger)
                    TextButton(onClick = onDismiss) { Text("知道了", color = colors.accent) }
                }

                is AiUi.Result -> Column(
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(state.text, style = EchoType.subhead, color = colors.label)
                    ActionRow(
                        primaryLabel = "应用到正文",
                        onPrimary = { onApply(state.text, false) },
                        onCopy = { onCopy(state.text) },
                        onDismiss = onDismiss,
                    )
                }

                is AiUi.Polished -> Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("原文", style = EchoType.caption, color = colors.tertiaryLabel)
                    Spacer(Modifier.height(4.dp))
                    Text(state.original, style = EchoType.subhead, color = colors.secondaryLabel)
                    Spacer(Modifier.height(12.dp))
                    Text("润色稿", style = EchoType.caption, color = colors.accent)
                    Spacer(Modifier.height(4.dp))
                    Text(state.polished, style = EchoType.subhead, color = colors.label)
                    ActionRow(
                        primaryLabel = "应用润色稿",
                        onPrimary = { onApply(state.polished, false) },
                        onCopy = { onCopy(state.polished) },
                        onDismiss = onDismiss,
                        secondaryLabel = "追加到正文",
                        onSecondary = { onApply(state.polished, true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrimary) { Text(primaryLabel, color = colors.accent) }
        TextButton(onClick = onCopy) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("复制", color = colors.accent)
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondaryLabel, color = colors.accent) }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "放弃", tint = colors.tertiaryLabel)
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (manager == null) {
        toast("复制失败")
        return
    }
    manager.setPrimaryClip(ClipData.newPlainText("echo-note", text))
    toast("已复制")
}
