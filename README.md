# 回声 Echo（recall-your-memory）

![版本](https://img.shields.io/badge/%E7%89%88%E6%9C%AC-1.1.0-blue) ![平台](https://img.shields.io/badge/%E5%B9%B3%E5%8F%B0-Android%208.0%2B-green) ![测试](https://img.shields.io/badge/%E5%8D%95%E6%B5%8B-141%20%E9%A1%B9%E5%85%A8%E7%BB%BF-success)

> 环境声环形录音 + 人声检测 + 本地转写 + 云端 LLM 总结问答的 Android 记忆回溯 App。
> iOS 设计语言 · 液态玻璃 UI · 底部 Dock 五大功能 · 本地转写全程离线。

完整产品与技术方案见 **[PLAN.md](PLAN.md)**；v1.1 优化方案见 **[docs/PLAN-OPTIMIZATION.md](docs/PLAN-OPTIMIZATION.md)**。

## 界面预览

| 收藏 | 待办 | 备忘 |
|:---:|:---:|:---:|
| ![收藏](docs/screenshots/favorites.jpg) | ![待办](docs/screenshots/todos.jpg) | ![备忘](docs/screenshots/notes.jpg) |
| **液态玻璃调参** | **设置** | |
| ![液态玻璃](docs/screenshots/glass.jpg) | ![设置](docs/screenshots/settings.jpg) | |

<details>
<summary>本地语音模型管理</summary>

![本地语音模型](docs/screenshots/model.jpg)

</details>

## 功能

- **回声聆听**：后台常驻轻量监听（silero-vad），只在有人声时写入内存环形缓冲，保留最近 0.5–5 分钟（可调），取走即清空
- **两级触发门**：静默帧先用自适应能量门挡掉，**不喂 VAD**（省掉 onnx 推理）；再用「过零率 + 能量平稳度」滤掉稳态嗡鸣/白噪声。召回优先，宁可漏过滤也不误杀真人声
- **句首预滚**：VAD 报出起点时补上起点前 400ms 音频，不再切掉第一个字
- **一键回溯**：点击即取出窗口内的声音生成「记忆」，本地离线转写（逐段实时上屏），自动加标点
- **本地模型分级**：3 档离线转写模型按设备能力推荐，体积 **29MB / 78MB / 228MB**，国内直连可下（详见下节）
- **AI 联动**：记忆总结 / 追问（流式 SSE）、一键提取待办（转写全文 + AI 总结直接写入待办）
- **收藏 / 待办 / 备忘**：收藏永久保留，未收藏 7 天自动清理
- **液态玻璃 UI**：正版 [Kyant0 Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 实时折射（Dock / 回溯按钮 / 悬浮药丸），模糊 / 折射 / 色散 / 高光 / 染色全参数可调，三档降级（液态玻璃 / 毛玻璃 / 半透明）
- **壁纸**：自定义壁纸 + 内置裁剪（拖动 / 双指缩放取景），玻璃折射效果随壁纸呈现
- **保活**：前台服务 + 清后台自愈 + 快捷磁贴 + 通知一键恢复 + 分 ROM 保活引导

---

## 省电设计（v1.1）

聆听是常驻的，所以省电是核心指标。改动集中在四点：

| 手段 | 说明 |
|------|------|
| **唤醒锁按需持有** | 旧版**无条件**持 `PARTIAL_WAKE_LOCK`，AP 永远无法 suspend。现在：亮屏不持锁；灭屏才持，且**限时 10 分钟续租**，给系统留出进入低功耗态的窗口 |
| **两级触发门** | 静默期绝大多数帧不喂 VAD，直接省掉 onnx 推理（静默期的主要 CPU 开销） |
| **采集循环去锁化 + 零分配** | 预分配缓冲复用；VAD 推理不再持 `audioLock`；状态发布由 O(n) 遍历改为 O(1) 增量计数 |
| **静默期降频** | 连续静默后状态发布 ticker 从 1s 降到 5s；capture 线程优先级由 `MAX_PRIORITY` 降为 `THREAD_PRIORITY_AUDIO` |

设置里提供 **均衡（默认，零丢音）/ 极致省电** 两档：

- **均衡**：只做上述无损优化，静默期仍全速采集，**不丢任何音频**
- **极致省电**：静默超过 30s 后跳过 VAD 推理，但音频仍写入原始环形缓冲；一旦能量触发就把刚跳过的这段**回灌**给 VAD —— 因此**同样不丢音**，省的只是推理算力

> ⚠ 诚实的边界：**麦克风硬件本身的功耗无法通过上述手段降低**。只要 `AudioRecord` 处于活动状态，麦克风与其供电域就是开启的。真正的大头是让 AP 能进入 suspend（即唤醒锁改造）。上表各项为设计目标，**具体百分比需真机实测校准**，未实测前不写具体数字。

---

## 本地转写模型分级（v1.1）

3 档模型，**全部经国内网络实测可直连下载，无需 VPN**：

| 档位 | 模型 | 体积 | 语种 | 特点 |
|------|------|------|------|------|
| **L1 轻量** | Zipformer zh-14M int8 | **约 29 MB** | 中/英 | 体积最小，中文准确率中等；流式结构 |
| **L2 均衡** ⭐ | Paraformer-zh-small int8 | **约 78 MB** | 中/英 | 体积约为 L3 的 1/3，中文准确率良好，性价比最高 |
| **L3 高精度** | SenseVoice-small int8 | **约 228 MB** | 中/英/日/韩/粤 | 准确率最高，支持情感/事件标签 |

**自动推荐规则**（保守优先，宁可选小不选大）：

| 设备条件 | 推荐 |
|----------|------|
| 低内存设备 / 核心 < 4 / 堆上限 < 192MB | L1 |
| 核心 ≥ 8 且 堆上限 ≥ 512MB | L3 |
| 其余 | L2 |

**升级兼容**：v1.0 的模型目录是 `filesDir/models/sense-voice`，与 v1.1 的 L3 目录**完全相同** —— 已下载 228MB 模型的老用户会直接沿用，**不需要重新下载**。

**下载可靠性**：每个文件配 3 个源（`hf-mirror.com` → `modelscope.cn` → `huggingface.co`），支持断点续传，且**强制 SHA-256 校验**，不一致即删除并报错。

> **关于 ModelScope 源的来源说明（重要）**：ModelScope 上并**没有** k2-fsa / csukuangfj 官方发布的这些 ONNX 仓库（`csukuangfj`、`k2-fsa`、`pkufool` 命名空间全部 404）。这里使用的是**第三方社区转存**，实测其文件与官方站**逐字节一致**（体积与哈希均吻合）。供应链风险由强制 SHA-256 校验兜底：内容一旦被改动就无法通过校验，绝不会安装。另注：ModelScope 上看似「正统」的 `iic/SenseVoiceSmall*` 其实是**不同的文件**（`model_quant.onnx` + `tokens.json`），**不能**直接替换。

---

## 里程碑

| 阶段 | 内容 | 状态 |
|------|------|------|
| M0 | 工程脚手架、设计系统、Dock 导航、设置壳 | ✅ |
| M1 | 录音引擎：前台服务 + silero-vad + 环形缓冲 + 回溯 + 播放 | ✅ |
| M2 | 本地转写：模型管理（多镜像/续传/SHA256）+ SenseVoice 批转写 + 时间戳字幕 | ✅ |
| M3 | 云端 LLM：8 家供应商 + 模型列表拉取 + 记忆总结/追问 + 备忘润色 | ✅ |
| M4 | 收藏 / 待办 / 备忘交互 + 液态玻璃 UI + 7 天清理 | ✅ |
| M5 | 保活引导、快捷磁贴、续航实测、崩溃恢复、签名 APK | ✅ |
| **R1** | UI/UX 与液态玻璃重构（详见 [PLAN.md](PLAN.md) §14.1） | ✅ |
| **R2** | 省电优化 + 触发门重构 + 本地模型分级（详见 [docs/PLAN-OPTIMIZATION.md](docs/PLAN-OPTIMIZATION.md)） | ✅ |

**验证门禁（全部通过）**：`testDebugUnitTest` **141 项测试 / 0 失败** · `assembleDebug` 通过 · `assembleRelease` 通过（R8 混淆 + 资源压缩）

**R1 重构要点（2026-09）**：修复「立即本地转写」native 闪退（sherpa-onnx 构造参数误用）；工具链升级 Kotlin 2.4.10 / AGP 9.1.1 / Gradle 9.7.1 / Compose 1.12.1；液态玻璃换正版 **Kyant0 backdrop-android 2.0.1 + Shapes 1.2.1**（Dock 玻璃上玻璃滑块可拖动换页）；iOS 组件库、全局触感与 push 转场；清后台自愈保活；回溯转写逐段流式上屏；内置壁纸裁剪。

> 真机验收清单见 [docs/VERIFY.md](docs/VERIFY.md)（可用 `tools/verify-device.ps1` 自动跑一遍），交付与安装说明见 [docs/DELIVERY.md](docs/DELIVERY.md)。
> 液态玻璃现使用正版 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（backdrop 2.0.1）+ [Kyant0/Shapes](https://github.com/Kyant0/Shapes)（1.2.1）；升级记录见 [PLAN.md](PLAN.md) §14.1。

## 项目结构

```
.
├── PLAN.md                    产品与技术方案（先读这个）
├── build.ps1                  本地构建脚本（沙箱友好的环境变量 + Gradle 调用）
├── app/                       Android 应用模块
│   └── src/main/java/com/echo/recall/
│       ├── core/audio/        录音引擎（AudioRecord / VAD / 两级触发门 / 环形缓冲 / 句首预滚）
│       ├── core/asr/          本地转写（引擎抽象 + 3 档模型目录 + sherpa-onnx 多模型）
│       ├── core/llm/          云端 LLM（OpenAI 兼容客户端 + 供应商注册表）
│       ├── core/data/         Room / DataStore / 仓库
│       ├── core/service/      前台服务、快捷磁贴、清理任务
│       ├── core/designsystem/ iOS 风格主题与组件（含液态玻璃容器）
│       ├── feature/           memory / favorites / todos / notes / settings
│       └── nav/               Dock 五大功能导航
├── tools/                     构建与验证小工具（JVM 编写，绕开 Windows Schannel 限制）
│   ├── Download.java          下载器（断点续传 + SHA-256 校验）
│   ├── HeadProbe.java         URL 连通性/体积探测
│   └── HttpProbe.java         HTTPS 连通性诊断
└── .gradle-home/              本机 Gradle 用户目录（含代理配置与 Gradle 发行版，**不入库**）
```

## 签名

Release 构建口令从根目录 `keystore.properties` 读取，该文件与 `keystore/` 目录均**不入库**。克隆后如需出签名的 release 包，需自建：

```properties
storeFile=keystore/echo-release.jks
storePassword=你的口令
keyAlias=你的别名
keyPassword=你的口令
```

没有该文件时会自动回退到 debug 签名，`assembleDebug` 与单元测试不受影响。

## 构建

```powershell
# 一次性：安装 JDK 17 与 Android SDK（本机已具备）
pwsh -File build.ps1 assembleDebug      # 产出 app/build/outputs/apk/debug/app-debug.apk
pwsh -File build.ps1 testDebugUnitTest  # 跑 JVM 单元测试
pwsh -File build.ps1 clean assembleRelease
```

`build.ps1` 会固定以下环境（因为运行环境不允许写工作区外的目录）：

| 变量 | 值 |
|------|-----|
| `JAVA_HOME` | `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` |
| `ANDROID_HOME` | `%LOCALAPPDATA%\Android\Sdk`（按本机实际路径） |
| `GRADLE_USER_HOME` | `<repo>\.gradle-home` |
| `ANDROID_USER_HOME` | `<repo>\.android-home` |

网络：Maven/Gradle 下载经本机代理 `127.0.0.1:7897`（配置在 `.gradle-home/gradle.properties`）。

## 隐私原则

- 音频与转写 **100% 本地**：录音只进内存环形缓冲，取走即消失，绝不自动上传。
- 只有用户主动点 AI 功能时，才把**文字**发送到所选云端供应商（API Key 用 Android Keystore 加密存储）。
- 聆听期间常驻通知可见，随时可暂停。
