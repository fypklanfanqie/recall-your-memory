@file:OptIn(ExperimentalLayoutApi::class)

package com.echo.recall.feature.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors

/** 追问：以该条记忆的转写为上下文的多轮对话 */
@Composable
fun MemoryChatScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    viewModel: MemoryChatViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = LocalEchoColors.current
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = colors.accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("追问这段记忆", style = EchoType.headline, color = colors.label)
                Text(
                    text = memory?.let { "上下文：${it.transcript?.length ?: 0} 字转写" } ?: "",
                    style = EchoType.caption,
                    color = colors.secondaryLabel,
                )
            }
        }

        if (!viewModel.configured) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "还没有可用的 AI 供应商",
                        style = EchoType.footnote,
                        color = colors.secondaryLabel,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenProviders) { Text("去配置") }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column {
                        Text(
                            text = "你可以这样问：",
                            style = EchoType.footnote,
                            color = colors.secondaryLabel,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            viewModel.suggestions().forEach { suggestion ->
                                GlassSurface(
                                    modifier = Modifier.androidClickable { input = suggestion },
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = 3.dp,
                                ) {
                                    Text(
                                        text = suggestion,
                                        style = EchoType.footnote,
                                        color = colors.accent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(messages) { message ->
                ChatBubble(message)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("问点什么…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.padding(4.dp))
            IconButton(
                onClick = {
                    val question = input
                    input = ""
                    viewModel.send(question)
                },
                enabled = !sending && input.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = "发送",
                    tint = if (sending || input.isBlank()) colors.tertiaryLabel else colors.accent,
                )
            }
        }
    }
}

private fun Modifier.androidClickable(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    val colors = LocalEchoColors.current
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        GlassSurface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = 4.dp,
            tint = if (isUser) colors.accent.copy(alpha = 0.18f) else null,
        ) {
            Text(
                text = when {
                    message.content.isNotBlank() -> message.content
                    message.streaming -> "正在思考…"
                    else -> "（无内容）"
                },
                style = EchoType.subhead,
                color = if (message.failed) colors.danger else colors.label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}
