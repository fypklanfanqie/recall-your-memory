@file:OptIn(ExperimentalLayoutApi::class)

package com.echo.recall.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.core.designsystem.component.EchoRow
import com.echo.recall.core.designsystem.component.GlassSurface
import com.echo.recall.core.designsystem.component.SectionCard
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.core.designsystem.component.toast
import com.echo.recall.core.designsystem.theme.EchoType
import com.echo.recall.core.designsystem.theme.LocalEchoColors

/** AI 供应商配置：8 家国内大厂 + 自定义 */
@Composable
fun ProvidersScreen(
    onBack: () -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val colors = LocalEchoColors.current
    val states by viewModel.states.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val custom by viewModel.custom.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { context.toast(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = colors.accent)
            }
            Text(
                text = "AI 供应商",
                style = EchoType.title2,
                color = colors.label,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "只上传文字（转写内容），音频始终留在本机。API Key 用系统密钥库加密保存。",
            style = EchoType.footnote,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        SectionLabel("内置厂商")
        states.forEach { state ->
            ProviderCard(
                state = state,
                active = activeId == state.provider.id,
                onToggle = { viewModel.toggle(state.provider.id) },
                onKeyChange = { viewModel.onKeyChange(state.provider.id, it) },
                onVerify = { viewModel.verify(state.provider.id) },
                onSelectModel = { viewModel.selectModel(state.provider.id, it) },
                onSetActive = { viewModel.setActive(state.provider.id) },
                onRemove = { viewModel.remove(state.provider.id) },
            )
        }

        SectionLabel("自定义供应商")
        val customConfigs = states.mapNotNull { it.config }.filter { it.custom }
        if (customConfigs.isEmpty() && !custom.visible) {
            SectionCard {
                EchoRow(
                    title = "添加自定义供应商",
                    subtitle = "任何 OpenAI 兼容接口（自建网关 / 中转 / 其他厂商）",
                    showChevron = true,
                    onClick = viewModel::showCustomForm,
                )
            }
        }

        customConfigs.forEach { config ->
            SectionCard {
                EchoRow(
                    title = config.displayName,
                    subtitle = "${config.baseUrl} · ${config.model ?: "未选模型"}",
                    trailing = {
                        IconButton(onClick = { viewModel.remove(config.providerId) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = colors.danger)
                        }
                    },
                    onClick = { viewModel.setActive(config.providerId) },
                )
            }
        }

        if (custom.visible) {
            SectionCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("新增自定义供应商", style = EchoType.headline, color = colors.label)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = custom.name,
                        onValueChange = { viewModel.onCustomChange(name = it) },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = custom.baseUrl,
                        onValueChange = { viewModel.onCustomChange(baseUrl = it) },
                        label = { Text("Base URL（如 https://api.example.com/v1）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = custom.apiKey,
                        onValueChange = { viewModel.onCustomChange(apiKey = it) },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = custom.model,
                        onValueChange = { viewModel.onCustomChange(model = it) },
                        label = { Text("模型名（可留空自动拉取）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = viewModel::saveCustom) { Text("保存并使用") }
                        TextButton(onClick = viewModel::hideCustomForm) { Text("取消") }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProviderCard(
    state: ProviderUiState,
    active: Boolean,
    onToggle: () -> Unit,
    onKeyChange: (String) -> Unit,
    onVerify: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSetActive: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalEchoColors.current
    SectionCard {
        EchoRow(
            title = state.provider.displayName + if (active) "  · 当前" else "",
            subtitle = when {
                !state.configured -> state.provider.keyHint
                else -> "已配置 · ${state.model ?: "未选模型"}"
            },
            showChevron = !state.expanded,
            onClick = onToggle,
            trailing = if (active) {
                { Icon(Icons.Rounded.Check, contentDescription = "当前", tint = colors.success) }
            } else {
                null
            },
        )

        if (state.expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = state.keyInput,
                    onValueChange = onKeyChange,
                    label = { Text(if (state.configured) "替换 API Key（留空则保持不变）" else "API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = EchoType.footnote, color = colors.danger)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onVerify, enabled = !state.verifying) {
                        if (state.verifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.accent,
                            )
                            Spacer(Modifier.padding(4.dp))
                        }
                        Text(if (state.configured) "重新验证并拉取模型" else "验证并拉取模型")
                    }
                    if (state.configured) {
                        TextButton(onClick = onSetActive) { Text("设为当前") }
                        TextButton(onClick = onRemove) { Text("删除", color = colors.danger) }
                    }
                }

                if (state.modelsToShow.isNotEmpty()) {
                    Text("选择模型", style = EchoType.footnote, color = colors.secondaryLabel)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.modelsToShow.take(24).forEach { model ->
                            ModelChip(
                                label = model,
                                selected = model == state.model,
                                onClick = { onSelectModel(model) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ModelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalEchoColors.current
    GlassSurface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = if (selected) 8.dp else 2.dp,
        tint = if (selected) colors.accent.copy(alpha = 0.22f) else null,
    ) {
        Text(
            text = label,
            style = EchoType.caption,
            color = if (selected) colors.accent else colors.label,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
