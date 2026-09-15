package com.echo.recall.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.data.db.NoteEntity
import com.echo.recall.core.designsystem.component.ConfirmDialog
import com.echo.recall.core.designsystem.component.EchoEmptyState
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.component.ScreenHeader
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoRadius
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.util.NoteTextUtils
import com.echo.recall.core.util.TimeFormat

@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val others by viewModel.others.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<NoteEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { context.toast(it) }
    }

    // 编辑器在页面内部切换，不使用 NavController
    if (editing != null) {
        NoteEditorScreen(viewModel = viewModel)
        ConfirmDialog(
            visible = pendingDelete != null,
            title = "删除这条备忘？",
            text = NoteTextUtils.titleOf(pendingDelete?.content.orEmpty()),
            confirmLabel = "删除",
            onConfirm = {
                pendingDelete?.let { viewModel.delete(it) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
        return
    }

    val empty = pinned.isEmpty() && others.isEmpty()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
        item {
            ScreenHeader(
                title = "备忘",
                subtitle = if (pinned.size + others.size == 0) null else "${pinned.size + others.size} 条备忘",
                actions = {
                    IconButton(onClick = viewModel::create) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建备忘", tint = LocalEchoColors.current.accent)
                    }
                },
            )
        }

        item {
            NewNoteButton(onClick = viewModel::create)
        }

        item {
            SearchRow(
                value = query,
                onValueChange = viewModel::setQuery,
                onClear = { viewModel.setQuery("") },
            )
        }

        when {
            empty && query.isNotBlank() -> item {
                EchoEmptyState(
                    icon = Icons.Rounded.Search,
                    text = "没有匹配「$query」的备忘",
                )
            }

            empty -> item {
                EchoEmptyState(
                    icon = Icons.Rounded.EditNote,
                    text = "还没有备忘\n点上方「新建备忘」写下第一条",
                )
            }

            else -> {
                if (pinned.isNotEmpty()) {
                    item { SectionLabel("置顶") }
                    items(pinned, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { viewModel.openEditor(note.id) },
                            onTogglePin = { viewModel.togglePinned(note) },
                            onDelete = { pendingDelete = note },
                        )
                    }
                }
                if (others.isNotEmpty()) {
                    item { SectionLabel(if (pinned.isEmpty()) "全部备忘" else "其他") }
                    items(others, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { viewModel.openEditor(note.id) },
                            onTogglePin = { viewModel.togglePinned(note) },
                            onDelete = { pendingDelete = note },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = "删除这条备忘？",
        text = NoteTextUtils.titleOf(pendingDelete?.content.orEmpty()),
        confirmLabel = "删除",
        onConfirm = {
            pendingDelete?.let { viewModel.delete(it) }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )
}

/** 新建入口（页面顶部的玻璃按钮，等同于 FAB） */
@Composable
private fun NewNoteButton(onClick: () -> Unit) {
    val colors = LocalEchoColors.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(EchoRadius.card),
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(19.dp))
            Text(
                text = "新建备忘",
                style = EchoType.callout,
                color = colors.accent,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SearchRow(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalEchoColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        placeholder = { Text("搜索标题或正文", style = EchoType.body, color = colors.tertiaryLabel) },
        textStyle = EchoType.body,
        singleLine = true,
        shape = RoundedCornerShape(EchoRadius.card),
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.tertiaryLabel, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "清空搜索", tint = colors.tertiaryLabel, modifier = Modifier.size(18.dp))
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceElevated,
            unfocusedContainerColor = colors.surfaceElevated,
            focusedIndicatorColor = colors.accent,
            unfocusedIndicatorColor = colors.separator,
            cursorColor = colors.accent,
            focusedTextColor = colors.label,
            unfocusedTextColor = colors.label,
        ),
    )
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalEchoColors.current
    val title = note.title.ifBlank { NoteTextUtils.titleOf(note.content) }
    val preview = NoteTextUtils.previewOf(note.content)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(EchoRadius.card),
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.pinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.size(5.dp))
                    }
                    Text(
                        text = title,
                        style = EchoType.headline,
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (preview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preview,
                        style = EchoType.subhead,
                        color = colors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "更新于 ${TimeFormat.friendly(note.updatedAt)}",
                    style = EchoType.caption,
                    color = colors.tertiaryLabel,
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (note.pinned) "取消置顶" else "置顶",
                    tint = if (note.pinned) colors.warning else colors.tertiaryLabel,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = colors.danger,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
