# 回声 Echo — 产品与技术方案

> 一个「环境声环形录音 + 人声检测 + 本地转写 + 云端 LLM 总结问答」的 Android 记忆回溯 App。
> UI 参考 Apple iOS 设计语言，底部 Dock 切换大功能，支持液态玻璃效果开关（Kyant0 风格）。

---

## 1. 已确认的产品决策

| # | 决策项 | 结论 |
|---|--------|------|
| 1 | 平台 | **Android 原生**（Kotlin + Jetpack Compose），Windows 上完整开发，iOS 后续再说 |
| 2 | 回溯窗口口径 | **真实时间窗**：点「回溯」取按钮前 `a` 分钟内录到的人声片段（静默空档跳过、保留时间戳） |
| 3 | 未收藏记忆保留 | **7 天后自动清除**（连音频带文字）；收藏的永久保留 |
| 4 | 转写时机 | **点回溯时才转写**（平时只跑轻量 VAD，最省电；离线批转写精度更高） |
| 5 | 分发 | **自用侧载 APK**，不受商店审核约束 |
| 6 | 内置 LLM 厂商 | **8 家全内置** + 自定义供应商（全走 OpenAI 兼容接口 + 自动拉模型列表） |
| 7 | 名称 | **回声 Echo** |

其他理解（如有偏差请指出）：
- 「支付软件的设计」=「整个 App 的设计参考苹果 iOS 风格」（语音输入笔误）。
- 麦克风常驻后台：平时**静默监听**（只跑人声检测），检测到人声才写入环形缓冲；**不做长期保存**，只保证最近 `a` 分钟（30 秒~5 分钟可调，默认 3 分钟）。
- 点「回溯」后：取出窗口内录音 → **缓冲区清空（取走即消失）** → 生成一条「记忆」（音频 + 转写文字 + 可选 AI 总结），可收藏、可随时查看。
- VPN 端口 **7897** 仅用于**开发期**（拉仓库 / 下载模型 / Gradle 依赖）；App 用户侧用国内镜像。

---

## 2. 核心数据流

```
[麦克风 16kHz/mono]                                    ┌──────────────┐
        │ 32ms 帧连续读取                               │   云端 LLM    │
        ▼                                              │ 8家+自定义    │
[silero-vad 人声检测]  ──无人声──► 丢弃(只更新活动状态)   └──────▲───────┘
        │ 有人声                                             │ 仅发送文字
        ▼                                                   │ (转写文本/总结/追问/润色)
[语音片段 + 时间戳] ──► [内存环形缓冲 ≤ a 分钟] ──过期──► 丢弃  │
        │                                                   │
        │ 用户点「回溯」                                      │
        ▼                                                   │
[快照+清空缓冲] → [编码 AAC 音频] → [SenseVoice 本地批转写] ───┘
        │
        ▼
[记忆条目：音频+带时间戳文字] → 收藏⭐(永久) / 未收藏(7天自动清)
```

---

## 3. 功能规格

### 3.1 记忆引擎（后台录音管线）
- 前台服务（`foregroundServiceType="microphone"`）常驻，持续开启 AudioRecord（16kHz / 单声道 / PCM16）。
- 每帧过 silero-vad（仅 629KB 模型，CPU 占用极低）：**无人声 → 不写入**；有人声 → 写入环形缓冲。
- 片段状态机：约 250ms 人声确认开始（含 300ms 预滚防切字）→ 约 500ms 静默确认结束；单段上限 30 秒自动切分（转写更稳、进度更细）。
- 环形缓冲只在内存中，最多 `a` 分钟（5 分钟 PCM ≈ 9.6MB），窗口外片段滚动丢弃。
- 常驻通知：「回声正在聆听 · 窗口 5 分钟」，附快捷操作 **[回溯]** / **[暂停]**。

### 3.2 记忆回溯
- 点击大按钮（或通知栏「回溯」）：立即冲刷当前半开片段 → 快照窗口内全部片段 → **清空缓冲** → 生成记忆条目。
- 条目立即可见可播放；转写在后台跑，**按片段流式填充文字**（整窗 5 分钟预计 15~40 秒转完）。
- 窗口内无人声 → 友好空态提示（「最近 5 分钟没有人声被记录」），缓冲保留不清。
- 每条记忆含：时间范围、各片段时间戳文字、音频播放器（点字幕跳转播放）、SenseVoice 附赠的语种/情绪/音频事件标签。

### 3.3 记忆库与收藏
- 记忆列表：卡片（时间范围 / 人声时长 / 文字预览 / 收藏星标 / AI 总结徽标）。
- 详情页：播放器 + 时间戳字幕、**AI 总结**（手动触发，云端）、**追问**（以转写文本为上下文的流式对话）、收藏、导出文字、删除。
- 收藏 = 永久保留；未收藏 7 天后由 WorkManager 每日清理（音频文件 + 记录一起删）。

### 3.4 待办 TodoList
- 创建/编辑/勾选完成/置顶；标题 + 备注 + 可选截止时间；分组：未完成/已完成。

### 3.5 备忘录
- 富文本纯 Markdown 文本编辑器；列表 + 详情。
- AI 能力：**一键总结**（多笔记要点提炼）、**一键润色**（保留原意、优化表达，改写前预览 diff、可撤销）。

### 3.6 AI 能力（云端 LLM）
- **记忆总结 / 追问**、**备忘录总结 / 润色**。发送的只有**文字**，音频永不上云。
- 流式输出（SSE），失败可重试，结果缓存到条目。

### 3.7 设置
- 录音：窗口时长（30s~5min 滑杆）、VAD 灵敏度（低/中/高）、通知开关。
- AI：供应商管理（见 §6.5）、默认模型、回溯后是否自动总结（默认关）。
- 外观：**液态玻璃开关**、主题（跟随系统/浅色/深色）、强调色。
- 通用：模型管理（下载/删除/本地导入）、保活引导、存储用量、隐私说明、关于。

---

## 4. 技术选型（已查证）

| 组件 | 选型 | 关键依据 |
|------|------|----------|
| 语言/UI | Kotlin 2.x + Jetpack Compose (BOM) + Material3 自定义 iOS 皮肤 | 原生性能 + 声明式 UI |
| 最低版本 | minSdk 26 (Android 8.0) / targetSdk 35 | 覆盖 ~98% 国内设备；玻璃效果按 API 级降级 |
| 录音 | AudioRecord 16kHz mono PCM16 + 前台服务 | 环形缓冲仅需 9.6MB 内存 |
| 人声检测 | **silero-vad**（k2-fsa 导出 onnx，629KB / int8 208KB，MIT） | 帧级检测 CPU 开销极低，Android 官方示例齐备 |
| 本地转写 | **sherpa-onnx** + **SenseVoice-small int8**（228MB，中/粤/英/日/韩） | 离线批转写 RTF≈0.1（中端机 5 分钟音频约 30s 转完）；`use_itn=1` 自带**标点+数字规范化+逐字时间戳+情绪/事件标签**；JitPack 依赖 `com.github.k2-fsa.sherpa-onnx:sherpa-onnx` 免自编译；官方 Android 示例「VAD+离线ASR」与本 App 架构完全一致 |
| 备选转写 | 流式 zipformer zh-en int8（边录边转，未来省电模式可切换）、whisper.cpp（模型过大弃用） | 架构上预留引擎抽象 |
| 液态玻璃 | **Backdrop（Kyant0/AndroidLiquidGlass）**，Maven Central `io.github.kyant0:backdrop`，Apache-2.0 | 官方即 Compose Multiplatform 液态玻璃库；示例含 LiquidBottomTabs/LiquidButton 可直接改造为 Dock 与回溯按钮 |
| 云端 LLM | OkHttp + kotlinx-serialization，OpenAI 兼容协议 + SSE 流式 | 8 家国内厂商全兼容 |
| 存储 | Room（条目/待办/备忘）+ DataStore（设置）+ EncryptedSharedPreferences（API Key，Android Keystore 加密） | — |
| 音频编码 | MediaCodec AAC-LC → .m4a（32kbps mono，5 分钟 ≈ 1.2MB） | AAC 编码器全机型强制可用 |
| 后台任务 | WorkManager（每日 7 天清理）+ 前台服务 + Quick Settings Tile | — |
| DI | Hilt | — |

参考链接：
- sherpa-onnx: https://github.com/k2-fsa/sherpa-onnx （Apache-2.0）
- SenseVoice 模型: https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html
- silero-vad: https://k2-fsa.github.io/sherpa/onnx/vad/silero-vad.html
- Backdrop: https://github.com/Kyant0/AndroidLiquidGlass · 文档 https://kyant.gitbook.io/backdrop
- Shapes（iOS 端设计参考，后续 iOS 版使用）: https://github.com/Kyant0/Shapes

---

## 5. 系统架构

单 `:app` 模块（单人开发期最务实），按包分层，后续可拆模块：

```
com.echo.recall/
├── App / MainActivity / EchoNavHost
├── core/
│   ├── audio/      RecorderEngine（AudioRecord 读取循环）
│   │               VadEngine（silero 包装）、SegmentAssembler（状态机+预滚）
│   │               RingBuffer（内存环形窗口）、AudioEncoder（PCM→AAC）
│   ├── asr/        AsrEngine 接口 + SenseVoiceAsrImpl（sherpa-onnx）
│   │               ModelManager（多镜像断点续传下载/校验/导入）
│   ├── llm/        OpenAiCompatClient（chat/models/SSE）
│   │               ProviderRegistry（8 家内置 + 自定义）、PromptTemplates
│   ├── data/       Room（Memory/Todo/Note）、DataStore 设置、Repositories
│   ├── service/    RecorderForegroundService、RecallTile（快捷磁贴）
│   │               RetentionWorker（7 天清理）
│   └── designsystem/ 主题（iOS 色板/字体层级）、GlassDock、GlassButton、
│                     IOSListRow、Sheet、回溯按钮动效
└── feature/
    ├── memory/     首页（状态卡+回溯按钮+列表）、详情（播放器/字幕/AI/追问）
    ├── favorites/  收藏页（记忆库筛选）
    ├── todos/      待办
    ├── notes/      备忘录（编辑器 + AI 动作 + diff 预览）
    └── settings/   录音/AI/外观/模型/保活引导/关于
```

### 录音服务生命周期
1. 引导页「开启回声」→（前台上下文）授权麦克风 → 启动 FGS(microphone)（Android 11+ while-in-use 语义满足）。
2. 服务持有 PARTIAL_WAKE_LOCK 保证灭屏持续采集；常驻通知可暂停/恢复。
3. 进程被杀 → START_STICKY 自拉起；若拉起时处于后台导致麦克风被系统拒绝 → 通知栏提示「点按恢复录音」（Android 15 起 BOOT 后 mic FGS 不能自启，属平台限制）。
4. 快捷磁贴「回声」一键恢复/暂停。

### 回溯时序（点按钮后）
```
UI/通知 → Service.recall()
  1. flush 半开片段 → snapshot 窗口内 segments → 清空 ring        (<10ms)
  2. 写 Room：MemoryEntry(status=TRANSCRIBING) → UI 立即出卡片
  3. PCM → AAC 编码落盘（后台协程，~1s 级）
  4. SenseVoice 逐片段转写（2~4 线程，进度回调 → Room 更新 → UI 流式出字）
  5. 完成 → status=READY；（可选）自动总结
```

---

## 6. 关键机制设计

### 6.1 环形缓冲（真实时间窗）
- 内存 `ArrayDeque<Segment>`，Segment = PCM 字节 + 起止时间戳（elapsedRealtime + UTC 双记录）。
- 滚动丢弃：`seg.end < now - a` 的整段丢弃（跨窗口边界的段保留整段，不切半句）。
- 上限保护：总字节数 > (5min × 32KB/s) 时强制淘汰最旧段（防御异常长语音）。

### 6.2 VAD 灵敏度
- 三档预设映射到 silero 阈值（低 0.35 / 中 0.5 / 高 0.65）+ 起判/收尾时长参数。
- 首页实时显示：当前是否检测到人声、窗口内累计人声时长、最近一次人声时间。

### 6.3 转写管线
- SenseVoice `language=auto, use_itn=1` → 输出带标点文字 + 逐字时间戳 + 情绪/事件标签。
- 片段串行转写（顺序保证），线程数 = 大核数（≤4）；转写期间进度常驻通知可关。
- 架构留 `AsrEngine` 接口：未来可切流式 zipformer 做「边录边转」省电折衷模式。

### 6.4 模型分发（228MB 首启下载）
- APK 内置：silero-vad（629KB）+ tokens；SenseVoice **首次使用时下载**。
- 多镜像顺序尝试：GitHub Releases → HuggingFace → ModelScope / gitcode 国内镜像；断点续传 + SHA-256 校验；下载进度页。
- 提供「从本地文件导入模型」入口（用户自行下载 .onnx 放入）。
- 开发期我方下载走代理 `127.0.0.1:7897`。

### 6.5 LLM 供应商（8 家内置 + 自定义）
| 厂商 | Base URL（OpenAI 兼容） | 模型列表策略 |
|------|--------------------------|--------------|
| DeepSeek | `https://api.deepseek.com` | 动态 GET /models |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4` | 动态，失败→内置精选 |
| Kimi 月之暗面 | `https://api.moonshot.cn/v1` | 动态 GET /models |
| 通义千问(百炼) | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 动态 GET /models |
| 豆包(火山方舟) | `https://ark.cn-beijing.volces.com/api/v3` | 内置精选（doubao-seed 系列）+ 手动填接入点 |
| 腾讯混元 | `https://api.hunyuan.cloud.tencent.com/v1` | 动态，失败→内置精选 |
| MiniMax | `https://api.minimaxi.com/v1` | 动态，失败→内置精选 |
| 硅基流动 | `https://api.siliconflow.cn/v1` | 动态 GET /models |
| **自定义** | 用户填写 | 尝试 /models，支持手动填模型 |

- 统一 **三级模型列表**：动态拉取 → 内置精选兜底 → 手动输入，永不卡死。
- 配置流：选厂商 → 贴 API Key → [验证并拉取模型] → 选默认模型。
- Key 用 Android Keystore 加密存储；聊天走 SSE 流式；每家实测在 M3 验收。

### 6.6 数据保留
- WorkManager 每日 + 开机触发清理：`未收藏 && createdAt < now-7d` → 删 Room 行 + 音频文件。
- 收藏条目永不清理；存储不足提示手动清理。

### 6.7 续航策略
- 平时只有 AudioRecord 读取 + VAD 推理（无转写/无编码/无网络）。目标待机监听耗电 **< 2~3%/小时**（M5 实测校准）。
- 灭屏用 PARTIAL_WAKE_LOCK 维持采集；转写/编码只在回溯时突发执行。
- 设置页提供「耗电统计」展示实测均值；省电折衷模式（v2：仅 VAD + 音频、转写延后）。

### 6.8 保活策略
- FGS 常驻通知 + START_STICKY + 崩溃自动恢复。
- 引导用户「忽略电池优化」；**保活引导页**按品牌给步骤（MIUI 自启动 / HarmonyOS / OPPO/vivo 后台锁定等，参考 dontkillmyapp）。
- 快捷磁贴一键恢复；开机后首次需打开一次 App（Android 15 平台限制，如实告知）。

---

## 7. UI/UX 设计（iOS 风格 + 液态玻璃）

### 7.1 导航：底部 Dock 五个大功能
`记忆` · `收藏` · `待办` · `备忘` · `设置`
- Dock 用 Backdrop 液态玻璃悬浮条（参考其 LiquidBottomTabs 示例改造）；玻璃关闭时降级为半透明模糊。

### 7.2 液态玻璃降级阶梯
| 条件 | 效果 |
|------|------|
| 开关 ON + Android 13+ (AGSL) | 完整液态玻璃（折射/高光） |
| 开关 ON + Android 12L 及以下 | 模糊/半透明降级 |
| 开关 OFF | 平面半透明 iOS 风格 |

设置即时切换、全局生效；玻璃默认开启。

### 7.3 设计语言要点
- iOS 色板：浅色 #F2F2F7 背景 / 深色纯黑；系统蓝强调色（可换）；大标题滚动收起；分组圆角卡片列表（inset grouped）+ chevron；iOS 风格开关/分段控件/底部 Sheet。
- 首页：录音状态卡（呼吸灯动效 + 窗口填充环）+ 中央**液态玻璃回溯大按钮** + 记忆卡片流。
- 图标用 Material Symbols Rounded 保持圆润统一。

---

## 8. 数据模型（Room）

```kotlin
MemoryEntity(id, createdAt, windowStart, windowEnd, audioPath, audioDurationSec,
             status /*TRANSCRIBING|READY|FAILED*/, transcript, segmentsJson /*[{startMs,endMs,text,emotion,event}]*/,
             tagsJson /*lang/emotion*/, summary?, summaryModel?, chatJson?, favorited)
TodoEntity(id, title, notes, done, createdAt, completedAt, dueAt?, pinned)
NoteEntity(id, title, content, createdAt, updatedAt, pinned, aiVersionsJson?)
```
设置（DataStore）：windowMinutes(默认3)、vadSensitivity、liquidGlass、theme、autoSummary、serviceEnabled、onboardingDone。
供应商配置（加密存储）：`[{id, name, baseUrl, apiKey, models[], activeModel}]`。

---

## 9. 权限清单

| 权限 | 用途 |
|------|------|
| RECORD_AUDIO | 环境声监听 |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_MICROPHONE (API 34+) | 麦克风前台服务 |
| POST_NOTIFICATIONS (API 33+) | 常驻通知与回溯快捷操作 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 引导保活 |
| RECEIVE_BOOT_COMPLETED | 开机后提示恢复（非自启录音） |
| INTERNET | 仅 LLM 云端调用 |

隐私原则：**音频与转写默认 100% 本地**；只有用户主动点 AI 功能才发送**文字**到所选云端；常驻通知始终可见。

---

## 10. 里程碑与验收标准

| 阶段 | 内容 | 验收标准 |
|------|------|----------|
| **M0 脚手架** | 工程搭建、设计系统、Dock 五页导航、设置壳 | APK 可安装，五页可切换，深浅色/玻璃开关占位生效 |
| **M1 录音引擎** | FGS + VAD + 环形缓冲 + 回溯（纯音频）+ 播放 | 真机：说话→点回溯→能重听；锁屏 1h 仍监听；窗口外内容确实滚动消失 |
| **M2 本地转写** | 模型下载器（多镜像续传）+ SenseVoice 批转写 + 时间戳字幕 | 5 分钟窗口转写 ≤40s（中端机）；字幕可点跳播放 |
| **M3 云端 LLM** | 8 家供应商配置/验证/拉模型 + 记忆总结/追问 + 备忘录总结/润色 | ≥3 家真 Key 实测通过（DeepSeek/GLM/Kimi 优先）；SSE 流式；自定义供应商可用 |
| **M4 全功能 UI** | 收藏/待办/备忘完整交互 + 液态玻璃 UI + 7 天清理 | 玻璃开关即时切换；收藏/清理路径实测正确 |
| **M5 加固交付** | 保活引导、电量实测、崩溃恢复、soak 测试、签名 APK | 过夜 8h soak 无中断；出具电量报告；崩溃后自动恢复；交付 release APK |

---

## 11. 风险与对策

| 风险 | 对策 |
|------|------|
| OEM 杀后台（小米/华为等激进策略） | FGS + 白名单引导 + 磁贴恢复；如实告知无法 100% 根治（行业共识） |
| Android 15 禁止开机自启 mic FGS | 开机后打开一次 App / 磁贴一键恢复；通知引导 |
| 228MB 模型首启下载 | 多镜像 + 断点续传 + 本地导入；未下模型也能用纯音频回溯 |
| 远场/嘈杂环境识别率 | VAD 灵敏度三档 + ITN 标点提升可读性；预期 1 米内正常音量效果最佳 |
| Backdrop API 演进/机型兼容 | Apache-2.0 可随时 fork 内置；四级降级阶梯兜底 |
| 各家 LLM 接口差异（如方舟接入点） | 三级模型列表 + 手动填模型/接入点 |
| 隐私敏感（常驻麦克风） | 本地优先架构 + 常驻通知 + App 内隐私说明页 |

---

## 12. 开发环境（Windows）

- Android Studio（或 cmdline-tools）+ JDK 17 + Android SDK 35 + adb 真机调试（需一台开启 USB 调试的 Android 手机；模拟器可先用宿主麦克风联调）。
- Gradle 代理（`gradle.properties`）：`systemProp.http.proxyHost=127.0.0.1` / `systemProp.http.proxyPort=7897`（https 同）；git 克隆走 `http.proxy=http://127.0.0.1:7897`。
- 项目根目录：`D:\ai\cc Programm\resume your memory\`（应用名「回声 Echo」，applicationId `com.echo.recall`，可改）。

## 13. 待定项 / v2 建议

- **从记忆一键提取待办/备忘**（LLM 结构化抽取，天然联动，强烈建议 v1.5）
- 桌面 Widget（一键回溯）、锁屏实时字幕（流式 zipformer 省电折衷模式）
- SenseVoice 情绪/事件标签的可视化时间轴；多语言 UI（英）；玻璃强度调节
- 记忆条目「回忆摘要日报」

---

## 14. 实现记录（与原计划的偏差，均为实测结论）

| 项 | 原计划 | 实际实现 | 原因 |
|----|--------|----------|------|
| 液态玻璃 | 直接用 Kyant0 **Backdrop** 库 | **自研** AGSL RuntimeShader + RenderEffect | Backdrop 1.0.6 需 Kotlin 2.3.10 + Compose 1.10.3，2.0.1 需 Kotlin 2.4.10 + Compose 1.12；本项目 Kotlin 2.0.21 / Compose 1.7.6，**Kotlin 元数据版本不兼容**（低版本编译器读不了高版本 metadata）。自研采用同样思路：背景自绘渐变光斑（EchoWallpaper），玻璃层按窗口坐标重绘同一份切片 + RenderEffect 折射/模糊；降级阶梯保持 API 33+ 折射 → 31-32 模糊 → 更低平面 |
| LLM 模型列表 | 三级策略（动态 → 内置 → 手填） | **8 家实测全部支持 `GET /models`**（无 Key 均返回标准 401，接口存在）→ 以动态拉取为主，失败回退内置建议列表，自定义供应商支持手填 | 实测优于预期 |
| 模型下载源 | GitHub → HF → ModelScope | **hf-mirror.com（国内镜像）→ huggingface.co**，均支持单文件直下（无需解压 tar.bz2）；另支持手动导入 | ModelScope 上未找到对应 ONNX 单文件路径；hf-mirror 实测可用 |
| 模型体积 | 预估 ~160MB | **228MB**（`model.int8.onnx` = 239,233,841 字节，SHA-256 已内置校验） | 以官方发布为准 |
| 转写时机 | 仅回溯时批转写 | 回溯时批转写 **+ 事后补转写**（.m4a 解码回 PCM 后按片段偏移切片） | 模型可能后下载；也支持「重新转写」重试 |
| 记忆问答记录 | 存库（chatJson） | **会话级**（离开页面即释放） | 降低库表复杂度；长期保存留待 v1.5 |

### 14.1 R1 UI/UX 与液态玻璃重构记录（2026-09）
| 项 | 结论 |
|----|------|
| 闪退修复（P0） | 「立即本地转写」闪退根因：`SenseVoiceEngine` 传了 `assetManager` 走 sherpa-onnx `newFromAsset`，把文件系统绝对路径当 APK assets 读 → native abort。改为 `OfflineRecognizer(config)`（文件版）后修复 |
| 工具链（P1） | Kotlin 2.0.21 → **2.4.10**、KSP 2.3.12（独立版本线）、AGP 9.1.1、Gradle 9.7.1（dist 已入 `.gradle-home/dist`）、Hilt 2.60.1、Room 2.8.5、**androidx Compose 1.12.1**（= JB Compose 1.12.0 对应版）、compileSdk 37。AGP 9 内置 Kotlin，不再应用 `kotlin-android` 插件；`kotlinOptions` DSL 迁移到 `compilerOptions` |
| 液态玻璃（P2） | 删除 vendored `com/kyant/backdrop/**`（37 文件），改用 Maven Central 正版 **`io.github.kyant0:backdrop-android:2.0.1` + `shapes-android:1.2.1`**（Apache-2.0）。Dock 重写为官方 LiquidBottomTabs 模式（`rememberCombinedBackdrop` 玻璃上玻璃）；`liquidGlassSurface` 移除 2.0 已无的 `exportedBackdrop`；全 App 换 Shapes 连续曲率圆角 |
| UI/UX（P3） | iOS 风格组件升级：EchoRow 图标芯片、EchoSegmented 滑动药丸、HeroRecallButton 重设计（径向渐变+强调色环+按压回弹）、记忆卡 AI 摘要胶囊、全局触感反馈（行点击/开关/分段/Dock/回溯）、导航 iOS push 转场 |
| 门禁 | `testDebugUnitTest`（69 项）+ `assembleDebug` + `assembleRelease`（R8）全绿 |

以下环境适配不属于产品偏差，但影响构建（已固化在 `build.ps1` 与 `.gradle-home/gradle.properties`）：

1. 运行环境禁止写工作区外目录 → `GRADLE_USER_HOME`、`ANDROID_USER_HOME`、`user.home` 全部重定向进仓库
2. 只设 `ANDROID_USER_HOME`，**不能**同时设 `ANDROID_PREFS_ROOT`（AGP 会拒绝启动：多个注入方式冲突）
3. Gradle 原生文件监视器在本环境不可用 → `org.gradle.vfs.watch=false`
4. Kotlin 编译守护进程无法启动（写不了 `%LOCALAPPDATA%\kotlin`）→ `kotlin.compiler.execution.strategy=in-process`
5. 内存受限（`-Xmx3072m` 触发过原生 OOM）→ 降到 1536m + 关闭并行 + workers.max=2
6. Windows Schannel 不可用（curl/.NET 的 HTTPS 全部失败）→ 用 JVM 工具（`tools/Download.java`、`tools/HeadProbe.java`）做下载与探测；Gradle 走 JVM JSSE 不受影响

### 14.2 v1.1 省电 / 触发 / 模型分级记录（2026-09）

详细方案见 **[docs/PLAN-OPTIMIZATION.md](docs/PLAN-OPTIMIZATION.md)**。用户决策：默认「均衡」省电档（零丢音）、只上 3 档模型（砍 L4）、加能量+过零率过滤、全部实施。

| 项 | 原计划 | 实际实现 | 原因 |
|----|--------|----------|------|
| 唤醒锁 | 灭屏用 PARTIAL_WAKE_LOCK 维持采集 | **只在「灭屏 + 聆听」时持有，且限时 10 分钟续租**；亮屏不持锁 | v1.0 无条件持锁 → AP 永远无法 suspend，是最伤的单项耗电。音频采集本身是唤醒源，无需常驻持锁 |
| 省电折衷模式 | v2 计划「仅 VAD + 音频、转写延后」 | 实现为 **VAD_SKIP**：静默 >30s 跳过 VAD 推理，音频仍入原始环形缓冲，能量触发后**回灌**给 VAD | 回灌方案**零丢音**，只省推理算力 —— 比原计划「丢音频」的折衷更好，因此可以做成可选档而不牺牲体验 |
| 触发门 | 单级（直接喂 VAD） | **两级**：自适应能量门（不达标不喂 VAD）+ 过零率/能量平稳度过滤 | 静默期 VAD 推理是主要 CPU 开销；两级门同时降误报 |
| 非人声过滤强度 | 「滤掉电视/音乐」 | **有意做成召回优先**：中频纯音（440Hz，ZCR≈0.055）落在人声带内，**不拒绝**；只拒绝低频嗡鸣与宽带稳态噪声 | 靠过零率无法分开 440Hz 纯音与语音，强行调低阈值会误杀元音。误杀 = 用户记忆永久丢失，代价远大于漏过滤。更激进的过滤应在**转写后**用 SenseVoice 的 `event` 标签回标 |
| 句首预滚 | 未计划 | **新增**：VAD 报出起点时补上起点前 400ms（`RawAudioRing` + `isSpeechDetected()` 上升沿抓取） | 修掉「第一个字被切掉」的体感问题 |
| 模型档位 | 单模型 228MB 硬编码 | **3 档**：L1 Zipformer-14M **29MB** / L2 Paraformer-small **78MB** / L3 SenseVoice **228MB**；按 CPU 核心数 + **设备总内存**自动推荐 | 低端机下不动也用不了 228MB 模型 |
| 模型下载源 | hf-mirror → huggingface | **hf-mirror → modelscope → huggingface** | 实测 ModelScope 上 `csukuangfj`/`k2-fsa`/`pkufool` 命名空间全部 404，但存在**第三方社区转存**且**逐字节一致**；作为独立境内兜底源，由强制 SHA-256 校验兜底供应链风险 |
| 转写线程数 | 写死 2 | 随核心数伸缩（≤4核→2、6核→3、≥8核→4），**封顶 4** | 依据官方 RK3588 实测 RTF：1线程 0.436 → 4线程 0.175，之后收益递减而功耗线性上升 |
| 引擎生命周期 | 每次转写新建 → 立即 release | **LRU 缓存 1 个引擎**，`onTrimMemory` 时释放 | 228MB 模型反复加载是秒级开销 |
| 升级兼容 | — | v1.0 目录 `models/sense-voice` 与 v1.1 的 L3 `dirName` **完全相同** → 老用户已下载的 228MB 模型直接沿用，并**优先选中 L3** | 避免让老用户白下第二个模型 |
| 门禁 | — | `testDebugUnitTest`（**146 项**）+ `assembleDebug` + `assembleRelease`（R8）全绿 | 单测从 69 → 146 项 |

**未做 / 诚实边界**：麦克风硬件功耗无法通过上述手段降低（`AudioRecord` 活动即麦克风供电域开启），真正的大头是唤醒锁改造；省电百分比为设计目标，**需真机实测校准**，未实测前不写入 README 具体数字。

### 14.3 v1.1 真机验证记录（2026-09，Xiaomi 2410DPN6CC · 8 核 · 15.5GB）

连真机 adb 实测，发现并修掉 4 个问题（其中 1 个是 v1.0 就存在的**核心功能失效**）：

| # | 严重度 | 问题 | 根因 | 修法 |
|---|--------|------|------|------|
| 1 | **P0 核心功能失效** | 点首页最大的「回溯记忆」按钮生成的记忆**永远不会转写**，UI 一直卡在「转写中」 | v1.0 里回溯流程有**两份实现**：`RecorderService.performRecall()` 含转写，`MemoryViewModel.recall()` **漏了转写**；而首页大按钮走的正是后者（通知栏「回溯」按钮才走前者） | 抽出 `RecallCoordinator` 作为**唯一实现**，服务与 ViewModel 都只调它 —— 两份实现必然漂移 |
| 2 | P1 推荐错误 | 8 核 / 15.5GB 的高配机被推荐 L2（78MB）而非 L3（228MB） | 判据用了 `ActivityManager.getMemoryClass()` = **Java 堆上限**（该机仅 256MB），而 onnxruntime 模型是 **native 常驻、不受堆上限约束** | 改用 `MemoryInfo.totalMem`（设备总内存）：L1 <3GB；L3 需 ≥6GB 且核心 ≥8 |
| 3 | P2 误导 UI | 未下载的模型也显示「使用中」 | 只判断了 `isSelected`，没判断 `installed` | 未下载时显示「已选 · 待下载」 |
| 4 | P2 功能回退 | 重写模型页时**丢了** v1.0 的「补转写待处理记忆」按钮 | 我重写 `ModelScreen` 时的遗漏 | 补回（归入「维护」分组）；它同时是修复 #1 遗留卡住记忆的恢复入口 |

**真机验证通过的能力**（此前只有单测、无真机证据）：

- **L1 模型国内直连下载 + SHA-256 校验**：从 `hf-mirror.com` 下载 4 个文件约 24s，全部哈希校验通过、体积精确吻合 —— 证明本仓库自行计算的 SHA-256 与镜像地址有效
- **新增的 `StreamingAsrEngine`（OnlineRecognizer）**：真机构造成功、**无 native abort**，`threads=4`（8 核自适应生效），产出真实中文
- **引擎 LRU 缓存**：连续转写两条记忆**只构造了一次引擎**（v1.0 每次都要重载 228MB）
- **端到端**：说话 → VAD 触发 → 缓冲 → 回溯 → 落库 → 本地转写 → UI 出字，全链路真机跑通
- **识别质量抽验**：用 sherpa-onnx 官方中文测试音频（实际内容「开放时间早上9点至下午5点」）识别出「早上九点去上午十点」，确认 L1 档确实能识别中文
- 两级触发门在真机上正常放行真实人声（窗口内累计人声秒数正常增长）

**仍未验证（如实记录）**：省电百分比需过夜 soak 实测（`tools/verify-device.ps1 -SoakHours 8`）；L2（Paraformer）档未在真机下载验证；`VAD_SKIP` 极致省电档的边界未实测。
