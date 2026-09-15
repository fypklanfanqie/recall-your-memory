# 回声 Echo — 交付说明

> 版本 0.1.0 · 自用侧载 APK · 全部音频与转写在**本机**完成

---

## 一、安装

```powershell
# 构建（工作区内完成，环境变量由脚本固定）
powershell -ExecutionPolicy Bypass -File build.ps1 assembleRelease

# 真机安装
adb install -r app\build\outputs\apk\release\app-release.apk
```

产物（实测）：

| 包 | 路径 | 体积 | 说明 |
|----|------|------|------|
| Debug | `app\build\outputs\apk\debug\app-debug.apk` | 82.7 MB | 包名 `com.echo.recall.debug`，未混淆 |
| Release | `app\build\outputs\apk\release\app-release.apk` | 66.1 MB | 包名 `com.echo.recall`，R8 混淆 + 资源压缩 |

- 体积主要来自 sherpa-onnx 原生库（arm64-v8a + x86_64）与 Compose；语音模型（228 MB）**不进 APK**
- 签名：`keystore/echo-release.jks`（别名与口令保存在本地 `keystore.properties`，该文件**不入库**；证书 `CN=Echo`，有效期 30 年，**自用开发签名，不要用于商店发布**）
- 自动化验收：`powershell -ExecutionPolicy Bypass -File tools\verify-device.ps1`（装包、权限、前台服务、通知、保活、内存、崩溃恢复、模型/记忆目录，`-SoakHours 8` 可跑过夜电量记录）
- 跳过模型下载：`powershell -ExecutionPolicy Bypass -File tools\push-model.ps1`（把本地 228MB 模型直接推进设备）

## 二、首次使用（约 3 分钟）

1. 打开「回声」→ 首页点 **开启聆听** → 允许麦克风与通知权限
2. 设置 → **AI 供应商** → 选一家（推荐 DeepSeek/智谱/Kimi）→ 贴 API Key → **验证并拉取模型** → 选模型
3. 设置 → **本地语音模型** → **下载模型（228 MB）** → 等进度走完（国内走 hf-mirror，可断点续传）
4. 设置 → **后台保活引导** → 按机型做完设置（否则系统会杀掉后台聆听）
5. 说话几句 → 首页点 **回溯记忆** → 得到一条带文字的记忆

## 三、功能对照

| 功能 | 位置 | 说明 |
|------|------|------|
| 静默聆听 | 首页状态卡 | 只在检测到人声时写入内存环形缓冲，不落盘、不联网 |
| 记忆回溯 | 首页大按钮 / 常驻通知「回溯」 | 取走点击前 `a` 分钟（30 秒~5 分钟可调）的人声，**缓冲随即清空** |
| 本地转写 | 自动 | SenseVoice-small int8 离线识别，中/粤/英/日/韩，带标点与时间戳 |
| AI 总结 / 追问 | 记忆详情页 | 只上传**转写文字**，音频不出本机 |
| 收藏 | 记忆卡片 ⭐ / 收藏页 | 收藏的永久保留（未收藏 7 天后自动清除） |
| 待办 / 备忘录 | Dock 第 3、4 项 | 备忘录支持 AI 总结与一键润色 |
| 液态玻璃 | 设置 → 外观 | 可随时关闭（关闭更省电）；Android 13+ 才有完整折射效果 |
| 保活引导 | 设置 → 后台保活引导 | 按机型给出设置步骤 + 快捷磁贴说明 |

## 四、液态玻璃的实现说明

原计划直接用 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（Backdrop 库），
但它的发布版本需要 **Kotlin 2.3.10+ / Compose 1.10.3+**，与本项目的 Kotlin 2.0.21 / Compose 1.7.6
**元数据不兼容**（Kotlin 编译器读不了更高版本的 metadata）。

因此按同样的思路自行实现（`core/designsystem/glass/`）：

| Android 版本 | 效果 |
|--------------|------|
| 13+ (API 33) | AGSL RuntimeShader 边缘折射 + 色散 + 高斯模糊 |
| 12 (API 31-32) | 高斯模糊（RenderEffect.blur） |
| 11 及以下 | 平面半透明（无 GPU 效果） |

做法：背景是自绘的确定性渐变光斑（`EchoWallpaper`），玻璃元素按自己在窗口中的坐标**重绘同一份渐变切片**，
再对该层施加 RenderEffect —— 等价于采样背后内容，且不依赖任何第三方库。
着色器编译失败时会自动退化到模糊/平面（`runCatching` 兜底），不会崩。

## 五、隐私

- 麦克风只在**聆听开启**时工作，常驻通知始终可见，可一键暂停
- 音频只进内存环形缓冲；回溯后编码为 .m4a 存本机 `filesDir/memories/`
- 转写全部本地完成；仅当用户主动点 AI 功能时，**文字**才会发往所选供应商
- API Key 用 Android Keystore（AES-256-GCM）加密存储
- 未收藏记忆 7 天后连音频带文字自动删除

## 六、已知限制

- Android 15 起**不允许开机自启麦克风前台服务**（平台限制）：重启后需打开一次 App，或用快捷磁贴恢复
- 各家 OEM 的后台清理策略无法 100% 绕过，务必完成「保活引导」里的设置
- 首次下载 228 MB 模型需要网络；也可在设置里手动导入 `model.int8.onnx` 与 `tokens.txt`
- 远场 / 嘈杂环境下识别率会下降（建议 1 米内正常音量）
