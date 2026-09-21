# 回声 Echo — 优化方案（省电 / 触发 / 本地模型分级）

> 目标版本 **v1.1**，三个子任务：A 省电、B 语音触发优化、C 本地转写模型分级与推荐。
> 现状基线：v1.0.0（releases/v1.0.0）。本方案全部基于仓库当前真实代码，不预设尚未实现的能力。

---

## 0. 已确认的决策（用户拍板）

| 议题 | 决策 |
|------|------|
| 省电力度 | 默认「均衡」（**零丢音**），「极致省电」作为可选开关 |
| 模型档位 | 只上 **3 档**（L1/L2/L3），砍掉原方案的 L4（232MB，与 L3 几乎同体积、提升有限） |
| 防误触发 | 加轻量的**能量 + 过零率**启发式过滤 |
| 执行范围 | 全部实施：省电 + 触发 + 模型分级 + UI |

## 0.1 实施结果（本仓库已完成）

| 子任务 | 交付 | 关键文件 |
|--------|------|---------|
| A 省电 | 唤醒锁按需持有 + 采集去锁化/零分配 + 静默期降频 + 自适应线程 | `RecorderService.kt`、`RecorderEngine.kt`、`SpeechRingBuffer.kt` |
| B 触发 | 两级触发门（能量门 + 过零率/能量平稳度）+ 句首预滚 + 三档标定 | `SpeechGate.kt`、`RawAudioRing.kt`、`VadEngine.kt` |
| C 模型 | 引擎抽象 + 3 档目录 + 设备推荐 + 引擎 LRU 缓存 + 分级 UI | `AsrEngine.kt`、`AsrEngines.kt`、`ModelCatalog.kt`、`ModelManager.kt`、`ModelScreen.kt` |

> **重要修正**：方案初稿称 ModelScope 是「官方源头镜像」，**实测为错**。ModelScope 上
> `csukuangfj`、`k2-fsa`、`pkufool` 命名空间全部 404；可用的只是**第三方社区转存**
> （已实测逐字节一致）。已按此修正代码注释与镜像清单，并保留强制 SHA-256 校验兜底。

---

## 1. 现状诊断（读代码得出的事实）

| 位置 | 现状 | 问题 |
|------|------|------|
| `RecorderEngine.captureLoop` | `AudioRecord.read(512 采样/帧)` 阻塞读，每帧 `synchronized(audioLock)` + 分配 `FloatArray` | 每 32ms 一次锁竞争 + 一次堆分配 = 每秒 31 次。GC 压力与 CPU 唤醒次数都偏高 |
| `RecorderEngine` | capture 线程 `Thread.MAX_PRIORITY` | 抢占大核，静态功耗上升 |
| `RecorderEngine.publishStatusLocked` | 每帧都跑；状态间隔 250ms 限流，但**锁内**仍在做 `ring.voicedMillis()` 全表遍历 | 环形缓冲里段数一多就是 O(n) 每 32ms |
| `RecorderService.acquireWakeLock` | **无条件**持 `PARTIAL_WAKE_LOCK`，无超时、无灭屏降级 | AP 无法进入 suspend，是最大单项耗电源 |
| `RecorderEngine.startTicker` | 每秒 tick，即使无人声也在跑协程 | 静默期无谓唤醒 |
| `RecorderEngine` | 无人声时仍全速读取 + VAD 推理 | 静默期（一天中 >90% 时间）功耗没被压低 |
| `VadSensitivityPreset` | 三档只改阈值，**不改行为** | 「低功耗」档并不省电 |
| `AsrModels` | 单一 SenseVoice int8，**228MB** 硬编码 | 低端机下载/内存/速度都吃力，无法选 |
| `SenseVoiceEngine` | `DEFAULT_THREADS = 2` 写死 | 不随 SoC 大核数伸缩 |
| `TranscriptionCoordinator` | 每次转写 `SenseVoiceEngine(...)` 新建 → `engine.release()` | 228MB 模型反复加载/卸载，单次开销秒级，且大块内存反复申请 |

**结论**：省电的关键不在于「少读一点音频」，而在于 ① 灭屏时不要把 AP 钉死、② 静默期降频、③ 砍掉每帧的锁与分配。

---

## 1. 子任务 A：省电优化

### A1. 关键修正 —— WakeLock 改为「按需 + 定时」

现状无条件持锁是最伤的。方案：

```
聆听中 + 屏幕亮   → 不持锁（屏幕亮时 AP 本就活跃）
聆听中 + 屏幕灭   → 持 PARTIAL_WAKE_LOCK，但带 timeout 续租（如 10 分钟一轮，
                     由 AlarmManager/setExactAndAllowWhileIdle 续租）
静默 > N 分钟     → 释放唤醒锁，降级到「低频监听」（见 A3）
暂停/停止         → 立即释放（已实现，保留）
```

新增 `ScreenStateObserver`（`ACTION_SCREEN_ON/OFF` 广播，动态注册，不要在 manifest 静态注册）。
**收益**：这是唯一能让 SoC 在没人声时真正进入低功耗态的改动，预期占整体省电收益的 50%+。

### A2. 采集循环去锁化 + 零分配

- 预分配一个 `FloatArray(FRAME_SAMPLES)` 复用缓冲，不再每帧新建（当前每帧一次 `PcmUtils.shortsToFloats` 分配）。
- 把「读 + VAD 推理」移出 `audioLock`：VAD 只在 capture 线程被调用，本身不需要锁；锁只用于保护 `ring` 与 `status`。
- `publishStatusLocked` 不再每帧遍历 `ring.voicedMillis()`；改为增量计数器（add/trim 时维护），O(1)。

**收益**：静默期 CPU 占用下降，GC 停顿消失（回溯时的卡顿也会改善）。

### A3. 静默期降频（Adaptive Duty Cycle）

核心思路：**人声窗口是「稀疏事件」，静默期没必要 100% 占空比**。

```
状态机：ACTIVE  ──(连续静默 > 3s)──▶  COOLDOWN  ──(连续静默 > 30s)──▶  IDLE_PROBE
          ▲                                │                                 │
          └────────(VAD 命中人声)──────────┴─────────────────────────────────┘
```

| 状态 | 行为 | 代价 |
|------|------|------|
| ACTIVE | 全速读取 + VAD（现状） | 基线 |
| COOLDOWN | 全速读取 + VAD，但**关闭状态发布 ticker**，线程优先级降到 NORMAL | 极低延迟，省掉每秒唤醒 |
| IDLE_PROBE | 按 1:4 占空比读取（读 512ms / 停 1536ms），停读期间用 `AudioRecord` 的硬件缓冲兜住 | **会丢静默期音频** |

> ⚠️ **IDLE_PROBE 有取舍，必须给用户开关**：丢的是「静默期的环境音」，人声起点靠 VAD 前置能量检测兜底（在停读前先读一小段做能量预判，命中就立刻回 ACTIVE）。
> 默认档位建议 **COOLDOWN**（零丢音），IDLE_PROBE 放进「极致省电」档并明确告知用户。

### A4. VAD 灵敏度与省电解耦

现在「灵敏度」既管误触发又暗示功耗，概念混在一起。拆成两个维度：

- **灵敏度**（LOW/MEDIUM/HIGH）：只管误报率，行为不变。
- **功耗档**（新增 `PowerProfile`）：`PERFORMANCE` / `BALANCED`(默认) / `SAVER`，决定 A1 唤醒锁策略 + A3 状态机档位。

### A5. 线程与线程数伸缩

- capture 线程优先级 `MAX_PRIORITY` → `THREAD_PRIORITY_AUDIO`（-16），足够且不抢大核。
- `SenseVoiceEngine.DEFAULT_THREADS` 改为 `Runtime.getRuntime().availableProcessors()` 映射：`≤4 核 → 2 线程`，`6 核 → 3`，`≥8 核 → 4`。
  （官方 RK3588 实测：1 线程 RTF 0.436，4 线程 0.175，加速比明显但要权衡功耗，故下沉到转写突发期才用多线程。）

### A6. 耗电可视化（兑现 PLAN.md §6.7 的承诺）

新增「耗电统计」卡片：今日聆听时长、人声时长占比、回溯次数、转写耗时。数据用现有 Room 加一张 `ListenStatEntity` 即可，不引入新依赖。
让用户能自己验证省电效果 —— 这也是唯一能证明优化有效的办法。

### A7. 预期收益（需真机校准，这里是量级判断）

| 项目 | 现状 | 优化后 | 依据 |
|------|------|--------|------|
| 灭屏静默待机 | 基线 | **-40% ~ -60%** | 释放唤醒锁让 AP 进 suspend |
| 灭屏有人声 | 基线 | -10% ~ -20% | 去锁化 + 去分配 |
| 亮屏使用 | 基线 | -5% ~ -10% | ticker 降频、线程优先级 |
| 低端机转写 | 228MB/2线程 | 81MB 模型 + 自适应线程，**-50% 内存** | 见子任务 C |

> 这些数字**必须真机实测校准**后再写进 README，不能直接当结论发布。仓库已有 `tools/verify-device.ps1`，扩一个电量采集脚本。

---

## 2. 子任务 B：语音触发功能优化

「触发」= 人声被 VAD 判定并写入环形缓冲。当前痛点与改法：

### B1. 双门限（能量 → VAD）两级触发，省 CPU 又降误报

```
帧 → [RMS 能量门限] → 不过 → 直接丢弃，不喂 VAD（省一次 onnx 推理）
                    → 过   → 喂 VAD → 判定人声
```

- 能量门限自适应：维护静默期噪声底（EMA），门限 = 噪声底 × 系数。
- **收益**：静默期 VAD 推理次数大幅下降（这是 CPU 上真正的开销），同时比固定阈值更能适应嘈杂/安静环境。
- 注意：silero-vad 本身有内部去噪，加前置门限要以**不降低召回**为验收标准（用真实录音回归）。

### B2. 触发延迟与尾音丢失

当前 `flush()` 只在回溯时调用，若用户话说到一半点回溯，靠 `drain` 的时间戳自愈逻辑补。问题：
- `minSilenceDuration` 决定「多久算说完」，MEDIUM=0.5s → 回溯源滞后约 0.5s。
- 回溯时应**优先用预滚缓冲**：保留最后 300ms 音频在 VAD 之外，避免切掉句首。

改法：环形缓冲入队时向前预滚 `PREROLL_MS = 300`，回溯时间窗向前多取 300ms。

### B3. 灵敏度档位重新标定

| 档 | threshold | minSpeech | minSilence | 适用 |
|----|-----------|-----------|------------|------|
| 低（安静房间） | 0.35 | 0.35 | 0.70 | 会议/书房，防误触发 |
| 中（默认） | 0.50 | 0.25 | 0.50 | 通用 |
| 高（嘈杂/远场） | 0.65 | 0.15 | 0.35 | 街头/厨房 |

数值沿用现有常量，但**配合 B1 的自适应能量门限**，实际行为会明显不同 —— 需要真机三档对比测试。

### B4. 防误触发：非人声事件过滤

SenseVoice 已经返回 `event` 标签（Speech/BGM/Applause/Laughter…）。VAD 阶段可加入轻量过滤：连续判定为纯噪声（`event != Speech`）的片段不入库，避免「电视声/音乐」把缓冲塞满。
> 该标签在**转写阶段**才有，VAD 阶段拿不到。所以这条只能做「事后过滤」或不做 —— **标为待定，需要用户决策**（见第 4 节问题 3）。

### B5. 触发反馈

现在通知只在「正在聆听 / 正在记录人声」间切。建议加：
- 触发时轻震动（可选，默认关）—— 让用户确知系统在听，是「体验不牺牲」的关键。
- 首页状态卡呼吸灯已存在，补一个「最近一次触发时间」。

---

## 3. 子任务 C：本地转写模型分级与推荐

### C1. 候选模型（**全部已实测 hf-mirror 可达，国内无需 VPN**）

下面每个 URL 都用 `tools/HeadProbe.java` 实探过，返回 `206 Partial Content` 并给出精确字节数：

| # | 模型 | 文件与体积 | 实测下载地址（国内直连） |
|---|------|-----------|------------------------|
| 1 | **SenseVoice-small int8**（中英日韩粤 + 情感/事件标签） | `model.int8.onnx` **228 MiB** + `tokens.txt` 308 KiB | [`hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17`](https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17) |
| 2 | **Paraformer-zh-small int8** | `model.int8.onnx` **78 MiB** (81,828,675 B) + `tokens.txt` 74 KiB | [`hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09`](https://hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09) |
| 3 | **Paraformer-zh int8**（大） | `model.int8.onnx` **232 MiB** (243,371,218 B) + `tokens.txt` 74 KiB | [`hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-2023-09-14`](https://hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-2023-09-14) |
| 4 | **Streaming Zipformer zh-14M int8**（超轻，流式） | `encoder…int8.onnx` **21 MiB** (21,621,684 B) + decoder/joiner/tokens | [`hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23`](https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23) |
| 5 | **Streaming Zipformer bilingual zh-en int8**（流式，中英） | encoder **173 MiB** (181,895,032 B) + decoder 13 MiB + joiner 3 MiB | [`…streaming-zipformer-bilingual-zh-en-2023-02-20`](https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20) |
| 6 | **Whisper-tiny int8** | encoder **12 MiB** (12,937,772 B) + decoder | [`hf-mirror.com/csukuangfj/sherpa-onnx-whisper-tiny`](https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-tiny) |
| 7 | **Whisper-base int8** | encoder **28 MiB** (29,120,534 B) + decoder | [`hf-mirror.com/csukuangfj/sherpa-onnx-whisper-base`](https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-base) |
| 8 | **Whisper-small int8** | encoder **107 MiB** (112,442,483 B) + decoder | [`hf-mirror.com/csukuangfj/sherpa-onnx-whisper-small`](https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-small) |

**镜像冗余**（每家都给 2 个源，沿用现有 `urls` 列表模式）：
1. `hf-mirror.com`（已验证，国内直连首选）
2. `modelscope.cn`（阿里，纯国内域名，最稳）—— paraformer / SenseVoice 官方源头都在 ModelScope
3. 兜底：`huggingface.co` 官方（海外用户）

> 说明：Whisper 系列**中文识别明显弱于 SenseVoice/Paraformer**（官方对比见 [SenseVoice 论文](http://arxiv.org/pdf/2407.04051)），列入是为了「超小体积档」——tiny 只有 12MiB，给极低端机兜底，但要如实标注中文准确率偏低。

### C2. 性能分级（按「最低可用机型」分档，而非按参数）

| 档位 | 机型要求 | 推荐模型 | 体积 | 内存峰值 | 中文准确率 | 适用场景 |
|------|---------|---------|------|---------|-----------|---------|
| **L1 轻量** | 4 核 A53 级 / 2GB 内存 / Android 8+ | Zipformer zh-14M int8 | **~21 MiB** | ~120 MB | 中 | 老机型、只求能用；流式，可边录边出字 |
| **L2 均衡** ⭐默认 | 6 核 / 4GB+ | **Paraformer-zh-small int8** | **~78 MiB** | ~350 MB | 良 | 绝大多数用户的最优解：体积是 SenseVoice 的 1/3 |
| **L3 高精度** | 8 核旗舰 / 6GB+ | **SenseVoice-small int8**（现状） | ~228 MiB | ~700 MB | 优 | 要情感/事件标签、中英日韩粤混说 |
| **L4 高精度+** | 8 核旗舰 / 8GB+ | Paraformer-zh int8（大） | ~232 MiB | ~750 MB | 优+ | 纯中文长音频，追求最高准确率 |

**判定依据**：
- `ActivityManager.getMemoryClass()` / `isLowRamDevice()` → 内存门槛
- `Runtime.availableProcessors()` + `Build.SOC_MODEL`(API31+) → 算力门槛
- 首次启动自动推荐高亮，用户可手动改（**不强制**）

### C3. 架构改造

现有代码把 SenseVoice 硬编码在多处（`AsrModels`、`SenseVoiceEngine`、`ModelManager`、`TranscriptionCoordinator`）。需引入抽象：

```kotlin
interface AsrEngine {
    fun transcribe(samples: FloatArray): AsrResult
    fun release()
}

enum class AsrModelTier { L1_LIGHT, L2_BALANCED, L3_ACCURATE, L4_MAX }

data class AsrModelSpec(
    val id: String,                 // "sense-voice-small"
    val displayName: String,        // "SenseVoice 小模型（高精度）"
    val tier: AsrModelTier,
    val kind: AsrModelKind,         // SENSE_VOICE / PARAFORMER / ZIPFORMER_STREAM / WHISPER
    val files: List<AsrModelFile>,
    val minCores: Int,
    val minRamMb: Int,
    val accuracyNote: String,       // 诚实标注（Whisper 中文偏弱等）
    val supportsEmotion: Boolean,
    val supportsStreaming: Boolean,
)
```

- `ModelManager` 从「单模型」→「多模型 + 当前选中」，每模型独立子目录。
- `ModelCatalog`（新）持有全部 `AsrModelSpec`，含分级与推荐逻辑。
- `TranscriptionCoordinator` 按 `kind` 分派到对应引擎实现。
- **模型热复用**：把 `SenseVoiceEngine` 的「用完即释放」改为 LRU 缓存（保留 1 个），避免重复加载 228MB；低内存时（`onTrimMemory`）立即释放。这是转写速度与耗电的双赢。

### C4. UI 改造

`ModelScreen` 从「一个下载按钮」→ **分级推荐卡片列表**：

```
┌─────────────────────────────────────┐
│ 📱 你的设备：8 核 · 8GB · 推荐 L3     │
├─────────────────────────────────────┤
│ ● L3 高精度        SenseVoice 228MB  │ ← 高亮「推荐」
│   中英日韩粤 · 情感/事件标签          │
│   [ 已安装 ✓ ]                       │
├─────────────────────────────────────┤
│ ○ L2 均衡          Paraformer 78MB   │ ← 「体积仅 1/3，中文更准」
│   [ 下载 ]                           │
├─────────────────────────────────────┤
│ ○ L1 轻量          Zipformer 21MB    │
│   [ 下载 ]  流式，边录边出字          │
├─────────────────────────────────────┤
│ ○ L4 最高精度      Paraformer 232MB  │
│   [ 下载 ]                           │
└─────────────────────────────────────┘
```

- 每档显示：体积、内存占用、准确率说明、是否支持情感/流式。
- 已装模型标 ✓，切换即生效；支持同时装多个（磁盘占用提示）。
- 「本地导入」保留，但按当前选中模型的哈希校验（现在校验写得是 SenseVoice 专用）。

---

## 4. 实施顺序与验收

| 阶段 | 内容 | 验收 |
|------|------|------|
| **P1** | A1 唤醒锁改造 + A2 去锁化/零分配 + A5 线程 | 69 项单测全绿；真机灭屏 8h 耗电对比曲线 |
| **P2** | A3 静默降频 + A4 功耗档 + A6 耗电统计 | 三档功耗实测；确认 COOLDOWN 零丢音 |
| **P3** | B1 双门限 + B2 预滚 + B3 标定 | 触发召回率回归（真实录音集）不低于现状 |
| **P4** | C3 引擎抽象 + C2 分级 + C1 目录 | 4 个模型各跑通一次转写；哈希校验通过 |
| **P5** | C4 分级推荐 UI + B5 反馈 | 截图验收；低端机自动推荐 L2 |
| **P6** | 文档 + 真机验收清单更新 | README/PLAN 同步，`verify-device.ps1` 扩展 |

**回归门槛**（沿用仓库现有纪律）：`testDebugUnitTest` 全绿 + `assembleDebug`/`assembleRelease` 通过 + 真机验收清单。

---

## 5. 风险

| 风险 | 对策 |
|------|------|
| 释放唤醒锁后系统杀服务 | 已忽略电池优化；灭屏低频探测；自愈闹钟保留 |
| IDLE_PROBE 丢音被用户投诉 | 默认关闭，文案明确；前置能量预判兜底 |
| 多模型并存导致磁盘爆 | 显示总占用，切换时提示旧模型可删 |
| ModelScope 直链格式与 HF 不同 | 下载器已支持 Range 续传；先探 URL 再进目录 |
| 引擎抽象引入 native 崩溃 | 保留 `SenseVoiceEngine` 的「必须走文件版构造」注释与约束 |
| 省电收益无法验证 | A6 耗电统计 + P1/P2 真机对比，不达标就回滚 |

---

## 6. 待用户确认（阻塞实施）

见聊天中提问：功耗档默认值、IDLE_PROBE 是否上线、B4 噪声过滤、模型档位数量与默认推荐。