package com.echo.recall.core.asr

import android.app.ActivityManager
import android.content.Context

/**
 * 本地转写模型目录：3 档模型 + 设备分级推荐。
 *
 * ## 国内可达性（本仓库实测）
 *
 * 每个 URL 都用 `tools/HeadProbe.java` 实探过，返回 `206 Partial Content` 与精确字节数，
 * 全程经国内网络直连（**无需 VPN**）。
 *
 * 镜像顺序（对国内用户而言前两个都是境内可达的独立源）：
 * 1. `hf-mirror.com` —— 国内直连首选，实测 8 个文件全部 206 且体积精确吻合
 * 2. `modelscope.cn` —— 阿里旗下、完全境内；作为独立兜底源
 * 3. `huggingface.co` —— 官方站，海外用户用
 *
 * ## ⚠ 关于 ModelScope 源的来源说明（重要）
 *
 * ModelScope 上 **没有 k2-fsa / csukuangfj 官方发布**的这些 ONNX 导出仓库
 * （`csukuangfj`、`k2-fsa`、`pkufool` 这三个命名空间全部 404）。这里使用的是一个
 * **第三方社区转存**仓库，实测其文件与官方站**逐字节一致**（体积完全吻合）。
 *
 * 供应链风险由**强制 SHA-256 校验**兜底：`ModelManager` 下载后必定比对哈希，
 * 不一致就删除文件并报错，绝不会安装被篡改的模型。因此把社区转存作为
 * 「最后一道兜底」是安全的 —— 最坏情况只是该源不可用，会自动回落到官方站。
 *
 * 另注：ModelScope 上看似「正统」的 `iic/SenseVoiceSmall*` 其实是**不同的文件**
 * （`model_quant.onnx` + `tokens.json`，是 PyTorch/量化导出），文件名与格式都不匹配，
 * **不能**直接替换本 App 需要的 `model.int8.onnx` + `tokens.txt`。
 */
object ModelCatalog {

    private const val HF_MIRROR = "https://hf-mirror.com"
    private const val HF_OFFICIAL = "https://huggingface.co"
    private const val MODELSCOPE = "https://modelscope.cn/models"

    /**
     * 每个模型配 3 个源：国内镜像 → 国内独立兜底 → 官方站。
     *
     * @param msRepo ModelScope 上的仓库（`<namespace>/<model>`）；为空则不给该源
     */
    private fun urls(repo: String, file: String, msRepo: String? = null): List<String> = buildList {
        add("$HF_MIRROR/$repo/resolve/main/$file")
        if (msRepo != null) add("$MODELSCOPE/$msRepo/resolve/master/$file")
        add("$HF_OFFICIAL/$repo/resolve/main/$file")
    }

    // ---------------------------------------------------------------- L1 轻量

    private const val ZIP14M_REPO = "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /** ModelScope 社区转存（实测逐字节一致；哈希校验兜底） */
    private const val ZIP14M_MS = "HZZSCIENCE/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23"

    /**
     * L1 轻量：流式 Zipformer zh-14M int8，4 个文件合计约 29 MB
     * （encoder 21MB + decoder 7MB + joiner 2MB + tokens）。
     *
     * 这是体积最小的可用中文模型（14M 参数）。流式结构意味着未来可以做「边录边出字」。
     * 中文准确率中等，适合老机型 / 只求能用的场景。
     */
    val L1_ZIPFORMER_14M = AsrModelSpec(
        id = "zipformer-zh-14m",
        displayName = "Zipformer 中文轻量",
        tier = AsrModelTier.L1_LIGHT,
        kind = AsrModelKind.ZIPFORMER_STREAM,
        dirName = "zipformer-zh-14m",
        files = listOf(
            AsrModelFile(
                name = "encoder-epoch-99-avg-1.int8.onnx",
                sizeBytes = 21_621_684L,
                sha256 = "1c556ea57cec304e55ec4b72e52c1cc098bb01476ed7d90f3de939fe126487b1",
                urls = urls(ZIP14M_REPO, "encoder-epoch-99-avg-1.int8.onnx", ZIP14M_MS),
            ),
            AsrModelFile(
                name = "decoder-epoch-99-avg-1.onnx",
                sizeBytes = 7_509_745L,
                sha256 = "5ee0f03a2768ff1d5c83ef3a493243c7935d316cd41280037b14783a3467cc78",
                urls = urls(ZIP14M_REPO, "decoder-epoch-99-avg-1.onnx", ZIP14M_MS),
            ),
            AsrModelFile(
                name = "joiner-epoch-99-avg-1.int8.onnx",
                sizeBytes = 1_795_562L,
                sha256 = "a7cf9d82757bdcf786059454495a9ca95e4bd7347f72473fc08d794475c36169",
                urls = urls(ZIP14M_REPO, "joiner-epoch-99-avg-1.int8.onnx", ZIP14M_MS),
            ),
            AsrModelFile(
                name = "tokens.txt",
                sizeBytes = 48_697L,
                sha256 = "8b294db9045d6e5f94647f4c1eec1af4da143a75053c399611444b378ff966ac",
                urls = urls(ZIP14M_REPO, "tokens.txt", ZIP14M_MS),
            ),
        ),
        minCores = 4,
        minRamMb = 0,
        languages = "中文 / 英文",
        accuracyNote = "体积最小（约 29 MB），中文准确率中等；流式结构，未来可边录边出字",
        supportsEmotion = false,
        supportsStreaming = true,
    )

    // ---------------------------------------------------------------- L2 均衡

    private const val PARA_SMALL_REPO = "csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09"

    /** ModelScope 社区转存（实测逐字节一致；哈希校验兜底） */
    private const val PARA_SMALL_MS = "liaowenbin/sherpa-onnx-paraformer-zh-small"

    /**
     * L2 均衡（**默认推荐**）：Paraformer-zh-small int8，约 78 MB。
     *
     * 体积只有 SenseVoice 的约 1/3，中文准确率却相当好 —— 绝大多数用户的最优解。
     */
    val L2_PARAFORMER_SMALL = AsrModelSpec(
        id = "paraformer-zh-small",
        displayName = "Paraformer 中文均衡",
        tier = AsrModelTier.L2_BALANCED,
        kind = AsrModelKind.PARAFORMER,
        dirName = "paraformer-zh-small",
        files = listOf(
            AsrModelFile(
                name = "model.int8.onnx",
                sizeBytes = 81_828_675L,
                sha256 = "3ef6c19369b912f7caf3cef8e545c5ccd1a33d9d7ec792a46668dc41c4b229ec",
                urls = urls(PARA_SMALL_REPO, "model.int8.onnx", PARA_SMALL_MS),
            ),
            AsrModelFile(
                name = "tokens.txt",
                sizeBytes = 75_352L,
                sha256 = "4b2d964e18b9cf139b473003b6698fb2ed9a2a5ec55b93daa677b28f578897aa",
                urls = urls(PARA_SMALL_REPO, "tokens.txt", PARA_SMALL_MS),
            ),
        ),
        minCores = 4,
        minRamMb = 0,
        languages = "中文 / 英文",
        accuracyNote = "体积约为高精度档的 1/3（约 78 MB），中文准确率良好，性价比最高",
        supportsEmotion = false,
        supportsStreaming = false,
    )

    // ---------------------------------------------------------------- L3 高精度

    private const val SENSE_VOICE_REPO = "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"

    /** ModelScope 社区转存（实测逐字节一致；哈希校验兜底） */
    private const val SENSE_VOICE_MS = "WEAAEW/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"

    /**
     * L3 高精度：SenseVoice-small int8，约 228 MB（v1.0 的现状模型）。
     *
     * 中英日韩粤五语种 + 情感/事件标签，准确率最好，代价是体积与内存。
     */
    val L3_SENSE_VOICE = AsrModelSpec(
        id = "sense-voice-small",
        displayName = "SenseVoice 多语种高精度",
        tier = AsrModelTier.L3_ACCURATE,
        kind = AsrModelKind.SENSE_VOICE,
        dirName = "sense-voice",
        files = listOf(
            AsrModelFile(
                name = "model.int8.onnx",
                sizeBytes = 239_233_841L,
                sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
                urls = urls(SENSE_VOICE_REPO, "model.int8.onnx", SENSE_VOICE_MS),
            ),
            AsrModelFile(
                name = "tokens.txt",
                sizeBytes = 315_894L,
                sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
                urls = urls(SENSE_VOICE_REPO, "tokens.txt", SENSE_VOICE_MS),
            ),
        ),
        minCores = 8,
        minRamMb = 4096,
        languages = "中 / 英 / 日 / 韩 / 粤",
        accuracyNote = "准确率最高，支持中英日韩粤混说与情感/事件标签；体积与内存占用最大",
        supportsEmotion = true,
        supportsStreaming = false,
    )

    /** 全部模型，按档位从低到高（UI 展示顺序） */
    val all: List<AsrModelSpec> = listOf(L1_ZIPFORMER_14M, L2_PARAFORMER_SMALL, L3_SENSE_VOICE)

    fun byId(id: String): AsrModelSpec? = all.firstOrNull { it.id == id }

    /** v1.0 的模型 id，用于迁移旧设置 */
    const val LEGACY_DEFAULT_ID = "sense-voice-small"

    /**
     * 设备能力探测结果。
     *
     * @param cores        CPU 核心数
     * @param totalRamMb   **设备总内存**（MB）—— 判断能否装下大模型的主依据
     * @param memoryClassMb `ActivityManager.getMemoryClass()`（应用 **Java 堆**上限，MB）。
     *   仅用于展示：onnxruntime 的模型是 **native 常驻，不受 Java 堆上限约束**，
     *   所以它**不适合**作为「能不能跑大模型」的判据。
     * @param lowRam       系统是否标记为低内存设备
     */
    data class DeviceTier(
        val cores: Int,
        val totalRamMb: Int,
        val memoryClassMb: Int = 0,
        val lowRam: Boolean = false,
    ) {
        /** 供 UI 展示的一句话摘要 */
        fun summary(): String {
            val ram = if (totalRamMb > 0) {
                val gb = totalRamMb / 1024.0
                if (gb >= 1) "%.1fGB 内存".format(gb) else "${totalRamMb}MB 内存"
            } else {
                "内存未知"
            }
            return "$cores 核 · $ram${if (lowRam) " · 低内存设备" else ""}"
        }
    }

    fun detectDevice(context: Context): DeviceTier {
        val am = context.getSystemService(ActivityManager::class.java)
        val cores = Runtime.getRuntime().availableProcessors()
        val memoryClass = runCatching { am?.memoryClass ?: 0 }.getOrDefault(0)
        val lowRam = runCatching { am?.isLowRamDevice ?: false }.getOrDefault(false)
        val totalRamMb = runCatching {
            val info = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(info)
            (info.totalMem / (1024L * 1024L)).toInt()
        }.getOrDefault(0)
        return DeviceTier(
            cores = cores,
            totalRamMb = totalRamMb,
            memoryClassMb = memoryClass,
            lowRam = lowRam,
        )
    }

    /**
     * 按设备能力推荐档位。
     *
     * 判据用**设备总内存**，而不是 `getMemoryClass()`（Java 堆上限）：
     * 模型是 native 常驻，不受 Java 堆上限约束。
     *
     * 实测教训：一台 8 核 / 15.5GB 的机器 `getMemoryClass()` 只报 256MB，
     * 若以堆上限为门槛会把它误判成中端机、只推荐 78MB 档（真机验证时发现并修正）。
     *
     * 规则（保守优先，宁可选小不选大 —— 模型下不动或跑不动比准确率略低更伤体验）：
     * - 低内存设备 / 核心 < 4 / 总内存 < 3GB   → L1（29MB）
     * - 核心 ≥ 8 且 总内存 ≥ 6GB 且 非低内存   → L3（228MB）
     * - 其余                                   → L2（78MB）
     */
    fun recommend(device: DeviceTier): AsrModelSpec {
        val l1 = device.lowRam ||
            device.cores < 4 ||
            (device.totalRamMb in 1..(L1_MAX_RAM_MB - 1))
        if (l1) return L1_ZIPFORMER_14M

        val l3 = device.cores >= 8 &&
            device.totalRamMb >= L3_MIN_RAM_MB &&
            !device.lowRam
        return if (l3) L3_SENSE_VOICE else L2_PARAFORMER_SMALL
    }

    /** 总内存低于此值（MB）→ 只推荐 L1 */
    const val L1_MAX_RAM_MB = 3 * 1024

    /** 总内存达到此值（MB）且核心数 ≥ 8 → 推荐 L3 */
    const val L3_MIN_RAM_MB = 6 * 1024

    /** 推荐理由，直接展示给用户 */
    fun recommendReason(device: DeviceTier, spec: AsrModelSpec): String = when (spec.tier) {
        AsrModelTier.L1_LIGHT ->
            "检测到设备内存或核心数偏紧（${device.summary()}），优先保证能稳定运行"
        AsrModelTier.L2_BALANCED ->
            "你的设备（${device.summary()}）性能充足，这一档体积小、中文准确率好，最划算"
        AsrModelTier.L3_ACCURATE ->
            "你的设备（${device.summary()}）性能充裕，可以承担最高精度与多语种"
    }

    /**
     * 转写线程数：随核心数伸缩，但**上限 4**。
     *
     * 依据 sherpa-onnx 官方 RK3588 实测（SenseVoice int8）：
     * 1 线程 RTF 0.436 / 2 线程 0.260 / 3 线程 0.208 / 4 线程 0.175。
     * 4 线程之后收益递减而功耗线性上升，所以封顶 4；低端机只用 2 线程避免抢占前台。
     */
    fun threadsFor(cores: Int = Runtime.getRuntime().availableProcessors()): Int = when {
        cores <= 4 -> 2
        cores <= 6 -> 3
        else -> 4
    }
}
