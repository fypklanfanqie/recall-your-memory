package com.echo.recall.feature.todos

import android.app.TimePickerDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Remove
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echo.recall.core.data.db.TodoEntity
import com.echo.recall.core.designsystem.component.ConfirmDialog
import com.echo.recall.core.designsystem.component.EchoSwitchRow
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.theme.EchoRadius
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors
import com.echo.recall.core.util.DueDate
import com.echo.recall.core.util.TimeFormat
import java.time.Instant
import java.time.ZoneId

/**
 * 待办编辑器：只在 TodosScreen 内部切换，不用 NavController。
 *
 * 返回 = 保存后关闭；系统返回键绑在同一个动作上，避免改完丢失。
 */
@Composable
internal fun TodoEditScreen(
    todo: TodoEntity,
    onClose: () -> Unit,
    onSave: (TodoEntity) -> Unit,
    onDelete: (TodoEntity) -> Unit,
) {
    val colors = LocalEchoColors.current
    val context = LocalContext.current

    var title by remember(todo.id) { mutableStateOf(todo.title) }
    var notes by remember(todo.id) { mutableStateOf(todo.notes.orEmpty()) }
    var dueAt by remember(todo.id) { mutableStateOf(todo.dueAt) }
    var pinned by remember(todo.id) { mutableStateOf(todo.pinned) }
    var confirmDelete by remember { mutableStateOf(false) }

    val snapshot = {
        todo.copy(
            title = title,
            notes = notes.takeIf { it.isNotBlank() },
            dueAt = dueAt,
            pinned = pinned,
        )
    }
    val save = { onSave(snapshot()) }

    BackHandler { save() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(bottom = 96.dp)
            .padding(bottom = 28.dp),
    ) {
        // ---- 页头：返回 / 标题 / 删除 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = save) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = colors.accent)
            }
            Text(
                text = "编辑待办",
                style = EchoType.headline,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = colors.danger)
            }
        }

        // ---- 标题 ----
        SectionLabel("标题")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("要做什么？", style = EchoType.body, color = colors.tertiaryLabel) },
            textStyle = EchoType.body,
            shape = RoundedCornerShape(EchoRadius.card),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = todoFieldColors(),
        )

        // ---- 备注 ----
        SectionLabel("备注")
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 120.dp),
            placeholder = { Text("补充细节（可选）", style = EchoType.body, color = colors.tertiaryLabel) },
            textStyle = EchoType.body,
            minLines = 4,
            shape = RoundedCornerShape(EchoRadius.card),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            keyboardActions = KeyboardActions(),
            colors = todoFieldColors(),
        )

        // ---- 截止时间 ----
        SectionLabel("截止时间")
        SectionCard {
            EchoSwitchRow(
                title = "设置截止时间",
                subtitle = dueAt?.let { DueDate.label(it) } ?: "未设置",
                icon = Icons.Rounded.Event,
                checked = dueAt != null,
                onCheckedChange = { on -> dueAt = if (on) DueDate.todayEndOfDay() else null },
            )
        }

        if (dueAt != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepButton(
                    label = "-1 天",
                    onClick = { dueAt = DueDate.shiftDays(dueAt, -1) },
                )
                StepButton(
                    label = "+1 天",
                    onClick = { dueAt = DueDate.shiftDays(dueAt, 1) },
                )
                StepButton(
                    label = TimeFormat.clock(dueAt!!),
                    onClick = {
                        val current = dueAt ?: return@StepButton
                        val zoned = Instant.ofEpochMilli(current).atZone(ZoneId.systemDefault())
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                dueAt = zoned
                                    .withHour(hour)
                                    .withMinute(minute)
                                    .withSecond(0)
                                    .withNano(0)
                                    .toInstant()
                                    .toEpochMilli()
                            },
                            zoned.hour,
                            zoned.minute,
                            true,
                        ).show()
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "共 ${DueDate.daysUntil(dueAt!!)} 天后到期",
                style = EchoType.caption,
                color = colors.tertiaryLabel,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ---- 置顶 + 保存 ----
        Spacer(Modifier.height(18.dp))
        SectionCard {
            EchoSwitchRow(
                title = "置顶",
                subtitle = "置顶的待办永远排在最上面",
                icon = Icons.Rounded.PushPin,
                checked = pinned,
                onCheckedChange = { pinned = it },
            )
        }

        Spacer(Modifier.height(20.dp))
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(EchoRadius.card),
            elevation = 8.dp,
        ) {
            Text(
                text = "保存",
                style = EchoType.headline,
                color = if (title.isBlank()) colors.tertiaryLabel else colors.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = title.isNotBlank(), onClick = save)
                    .padding(vertical = 14.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(14.dp))
        if (todo.done) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = { onSave(snapshot().copy(done = false, completedAt = null)) }) {
                    Text("恢复为未完成", color = colors.accent)
                }
            }
        }

        Text(
            text = "创建于 ${TimeFormat.friendly(todo.createdAt)}" +
                (todo.completedAt?.let { " · 完成于 ${TimeFormat.friendly(it)}" } ?: ""),
            style = EchoType.caption,
            color = colors.tertiaryLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }

    ConfirmDialog(
        visible = confirmDelete,
        title = "删除这条待办？",
        text = todo.title,
        confirmLabel = "删除",
        onConfirm = {
            confirmDelete = false
            onDelete(todo)
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun todoFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = LocalEchoColors.current.surfaceElevated,
    unfocusedContainerColor = LocalEchoColors.current.surfaceElevated,
    focusedIndicatorColor = LocalEchoColors.current.accent,
    unfocusedIndicatorColor = LocalEchoColors.current.separator,
    cursorColor = LocalEchoColors.current.accent,
    focusedTextColor = LocalEchoColors.current.label,
    unfocusedTextColor = LocalEchoColors.current.label,
)

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val colors = LocalEchoColors.current
    GlassSurface(
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                label.startsWith("+") -> Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp),
                )

                label.startsWith("-") -> Icon(
                    Icons.Rounded.Remove,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                style = EchoType.footnote,
                color = colors.accent,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}


