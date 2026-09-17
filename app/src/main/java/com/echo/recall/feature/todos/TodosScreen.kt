package com.echo.recall.feature.todos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.data.db.TodoEntity
import com.echo.recall.core.designsystem.component.ConfirmDialog
import com.echo.recall.core.designsystem.component.EchoEmptyState
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.component.RowDivider
import com.echo.recall.core.designsystem.component.ScreenHeader
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoRadius
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.util.DueDate
import com.echo.recall.core.util.TodoGroupCounts
import com.echo.recall.core.util.TodoGrouping

@Composable
fun TodosScreen(viewModel: TodosViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val todos by viewModel.todos.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()

    var completedExpanded by rememberSaveable { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<TodoEntity?>(null) }
    var quickAdd by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { context.toast(it) }
    }

    // 编辑器在页面内部切换，不使用 NavController
    val current = editing
    if (current != null) {
        TodoEditScreen(
            todo = current,
            onClose = viewModel::closeEditor,
            onSave = { viewModel.update(it); viewModel.closeEditor() },
            onDelete = { viewModel.delete(it) },
        )
        ConfirmDialog(
            visible = pendingDelete != null,
            title = "删除这条待办？",
            text = pendingDelete?.title.orEmpty(),
            confirmLabel = "删除",
            onConfirm = {
                pendingDelete?.let { viewModel.delete(it) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
        return
    }

    val submitQuickAdd = {
        val text = quickAdd
        quickAdd = ""
        viewModel.add(text)
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 108.dp)) {
        item { ScreenHeader(title = "待办", subtitle = tally(counts)) }

        item {
            QuickAddRow(
                value = quickAdd,
                onValueChange = { quickAdd = it },
                onSubmit = submitQuickAdd,
            )
        }

        if (todos.isEmpty()) {
            item {
                EchoEmptyState(
                    icon = Icons.Rounded.CheckCircle,
                    text = "还没有待办\n在上方输入框添加第一条",
                )
            }
        }

        if (pinned.isNotEmpty()) {
            item { SectionLabel(TodoGrouping.TITLE_PINNED) }
            item {
                SectionCard {
                    pinned.forEachIndexed { index, todo ->
                        if (index > 0) RowDivider()
                        TodoRow(
                            todo = todo,
                            onToggleDone = { viewModel.toggleDone(todo) },
                            onTogglePinned = { viewModel.togglePinned(todo) },
                            onDelete = { pendingDelete = todo },
                            onOpen = { viewModel.openEditor(todo.id) },
                        )
                    }
                }
            }
        }

        if (active.isNotEmpty()) {
            item { SectionLabel(TodoGrouping.TITLE_ACTIVE) }
            item {
                SectionCard {
                    active.forEachIndexed { index, todo ->
                        if (index > 0) RowDivider()
                        TodoRow(
                            todo = todo,
                            onToggleDone = { viewModel.toggleDone(todo) },
                            onTogglePinned = { viewModel.togglePinned(todo) },
                            onDelete = { pendingDelete = todo },
                            onOpen = { viewModel.openEditor(todo.id) },
                        )
                    }
                }
            }
        }

        if (completed.isNotEmpty()) {
            item {
                CompletedHeader(
                    count = completed.size,
                    expanded = completedExpanded,
                    onToggle = { completedExpanded = !completedExpanded },
                )
            }
            item {
                AnimatedVisibility(visible = completedExpanded) {
                    SectionCard {
                        completed.forEachIndexed { index, todo ->
                            if (index > 0) RowDivider()
                            TodoRow(
                                todo = todo,
                                onToggleDone = { viewModel.toggleDone(todo) },
                                onTogglePinned = { viewModel.togglePinned(todo) },
                                onDelete = { pendingDelete = todo },
                                onOpen = { viewModel.openEditor(todo.id) },
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = "删除这条待办？",
        text = pendingDelete?.title.orEmpty(),
        confirmLabel = "删除",
        onConfirm = {
            pendingDelete?.let { viewModel.delete(it) }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )
}

private fun tally(counts: TodoGroupCounts): String = buildString {
    if (counts.total == 0) return@buildString
    append("${counts.active} 项未完成")
    if (counts.completed > 0) append(" · ${counts.completed} 项已完成")
    if (counts.pinned > 0) append(" · ${counts.pinned} 项置顶")
}

/** 顶部快速添加：回车即提交 */
@Composable
private fun QuickAddRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("添加待办…", style = EchoType.body, color = colors.tertiaryLabel) },
            textStyle = EchoType.body,
            singleLine = true,
            shape = RoundedCornerShape(EchoRadius.card),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
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
        Spacer(Modifier.size(10.dp))
        GlassSurface(
            shape = RoundedCornerShape(12.dp),
            elevation = 4.dp,
        ) {
            Text(
                text = "添加",
                style = EchoType.callout,
                color = if (value.isBlank()) colors.tertiaryLabel else colors.accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(enabled = value.isNotBlank(), onClick = onSubmit)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** 可折叠的「已完成 (n)」标题行 */
@Composable
private fun CompletedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalEchoColors.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "completedChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 32.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = TodoGrouping.completedLabel(count),
            style = EchoType.footnote,
            color = colors.secondaryLabel,
        )
        Spacer(Modifier.size(4.dp))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "折叠" else "展开",
            tint = colors.tertiaryLabel,
            modifier = Modifier.size(16.dp).rotate(rotation),
        )
    }
}

@Composable
private fun TodoRow(
    todo: TodoEntity,
    onToggleDone: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = LocalEchoColors.current
    val subtitle = todoSubtitle(todo)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // iOS 风格复选框
        IconButton(onClick = onToggleDone) {
            Icon(
                imageVector = if (todo.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (todo.done) "标记为未完成" else "标记为已完成",
                tint = if (todo.done) colors.success else colors.accent,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = EchoType.body,
                color = if (todo.done) colors.secondaryLabel else colors.label,
                textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = EchoType.footnote,
                    color = if (isOverdue(todo)) colors.danger else colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onTogglePinned) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = if (todo.pinned) "取消置顶" else "置顶",
                tint = if (todo.pinned) colors.warning else colors.tertiaryLabel,
                modifier = Modifier.size(19.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "删除",
                tint = colors.danger,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** 副标题：截止时间 + 备注摘要 */
internal fun todoSubtitle(todo: TodoEntity): String? {
    val parts = buildList {
        todo.dueAt?.let { due ->
            add(
                if (todo.done) {
                    DueDate.label(due)
                } else if (isOverdue(todo)) {
                    "已过期 · ${DueDate.label(due)}"
                } else {
                    DueDate.label(due)
                },
            )
        }
        todo.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            val flat = notes.replace(Regex("\\s+"), " ").trim()
            add(if (flat.length <= 40) flat else flat.take(40).trimEnd() + "…")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun isOverdue(todo: TodoEntity): Boolean =
    !todo.done && todo.dueAt?.let { DueDate.isOverdue(it) } == true

