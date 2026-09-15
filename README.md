# 回声 Echo（recall-your-memory）

![状态](https://img.shields.io/badge/%E7%8A%B6%E6%80%81-%E6%97%A9%E6%9C%9F%E5%BC%80%E5%8F%91-orange) ![版本](https://img.shields.io/badge/%E7%89%88%E6%9C%AC-0.1.0--alpha-blue) ![平台](https://img.shields.io/badge/%E5%B9%B3%E5%8F%B0-Android%208.0%2B-green)

> ⚠️ **本项目仍处于早期开发阶段（Early Stage）**
> 核心链路已可跑通并经真机验证，但**接口、数据结构与功能形态随时可能变动**，不适合作为日常依赖或生产环境使用。
> ⚠️ **This project is in an early stage of development** — APIs and data models may change at any time.

> 环境声环形录音 + 人声检测 + 本地转写 + 云端 LLM 总结问答的 Android 记忆回溯 App。
> iOS 设计语言 · 底部 Dock 五大功能 · 液态玻璃可切换。

完整产品与技术方案见 **[PLAN.md](PLAN.md)**。

---

## 当前进度

> 项目整体处于**早期开发阶段**：M0–M5 里程碑虽已验证通过，仅代表核心链路可用，后续仍会有大量调整与重构。

| 阶段 | 内容 | 状态 |
|------|------|------|
| M0 | 工程脚手架、设计系统、Dock 导航、设置壳 | ✅ 完成（编译通过 + APK 产出） |
| M1 | 录音引擎：前台服务 + silero-vad + 环形缓冲 + 回溯 + 播放 | ✅ 完成（单测 11 项全绿） |
| M2 | 本地转写：模型管理（多镜像/续传/SHA256）+ SenseVoice 批转写 + 时间戳字幕 | ✅ 完成 |
| M3 | 云端 LLM：8 家供应商 + 模型列表拉取 + 记忆总结/追问 + 备忘润色 | ✅ 完成（单测 18 项全绿） |
| M4 | 收藏 / 待办 / 备忘交互 + 液态玻璃 UI + 7 天清理 | ✅ 完成 |
| M5 | 保活引导、快捷磁贴、续航实测、崩溃恢复、签名 APK | ✅ 完成（真机实测项见下） |

**验证门禁（全部通过）**：`testDebugUnitTest` **69 项测试 / 0 失败** · `assembleDebug` 通过 · `assembleRelease` 通过（R8 混淆 + 资源压缩，签名验证通过）

**已实现的 v1.5 联动**：记忆详情页可「**提取待办**」——用云端 LLM 从这段录音的转写里抽出待办，一键写进待办列表（只发送文字）。

> 真机验收清单见 [docs/VERIFY.md](docs/VERIFY.md)（可用 `tools/verify-device.ps1` 自动跑一遍），交付与安装说明见 [docs/DELIVERY.md](docs/DELIVERY.md)。
> 液态玻璃未使用第三方库的原因见 [PLAN.md](PLAN.md) 第 14 节；工具链适配的坑也在同一节。

## 项目结构

```
.
├── PLAN.md                    产品与技术方案（先读这个）
├── build.ps1                  本地构建脚本（沙箱友好的环境变量 + Gradle 调用）
├── app/                       Android 应用模块
│   └── src/main/java/com/echo/recall/
│       ├── core/audio/        录音引擎（AudioRecord / VAD / 环形缓冲）
│       ├── core/asr/          本地转写（sherpa-onnx + SenseVoice）
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
