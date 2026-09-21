package com.echo.recall.core.asr

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 单个模型的下载/安装状态 */
sealed interface ModelState {
    data object NotReady : ModelState
    data class Downloading(
        val modelId: String,
        val fileName: String,
        val fileBytes: Long,
        val fileTotal: Long,
        val overallPercent: Int,
    ) : ModelState

    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/** 某个模型的安装情况（UI 列表用） */
data class ModelInstall(
    val spec: AsrModelSpec,
    val installed: Boolean,
    val usedBytes: Long,
)

/**
 * 多模型管理：每个模型独立子目录（`filesDir/models/<dirName>`），可并存。
 *
 * 保留 v1.0 的能力：多镜像 + 断点续传 + SHA-256 校验 + 本地导入。
 * 模型不进 APK（最小档也有 31MB），首次使用时下载。
 *
 * ## v1.1 变化
 * - 从「单模型」→「多模型 + 当前选中」（[selectedId]）
 * - 校验按所选模型的清单进行（v1.0 写死了 SenseVoice 的哈希）
 * - 新增 LRU 引擎缓存（见 [com.echo.recall.core.asr.TranscriptionCoordinator]），
 *   避免每次转写都重新加载模型
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES)
        .build()

    private val _state = MutableStateFlow<ModelState>(ModelState.NotReady)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** 当前选中的模型 id（默认取推荐档，由 [initSelection] 决定） */
    private val _selectedId = MutableStateFlow(ModelCatalog.LEGACY_DEFAULT_ID)
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    val modelsRoot: File
        get() = File(context.filesDir, ROOT).apply { if (!exists()) mkdirs() }

    fun dirFor(spec: AsrModelSpec): File =
        File(modelsRoot, spec.dirName).apply { if (!exists()) mkdirs() }

    /** 兼容旧调用：v1.0 的模型目录就是 L3 的目录 */
    val modelDir: File get() = dirFor(ModelCatalog.L3_SENSE_VOICE)

    val selectedSpec: AsrModelSpec
        get() = ModelCatalog.byId(_selectedId.value) ?: ModelCatalog.L3_SENSE_VOICE

    /**
     * 首次启动：决定默认选中哪个模型。
     *
     * 优先级：
     * 1. 用户此前显式选过 → 尊重用户选择
     * 2. **迁移**：v1.0 用户已经下好 SenseVoice（v1.0 目录 `models/sense-voice` 与
     *    v1.1 的 L3 `dirName` 完全相同），直接用 L3，不让他们白下第二个模型
     * 3. 否则按设备能力推荐
     */
    fun initSelection(savedId: String?) {
        val saved = savedId?.takeIf { it.isNotBlank() }?.let { ModelCatalog.byId(it) }
        val resolved = when {
            saved != null -> saved
            isInstalled(ModelCatalog.L3_SENSE_VOICE) -> ModelCatalog.L3_SENSE_VOICE
            else -> ModelCatalog.recommend(deviceTier())
        }
        _selectedId.value = resolved.id
        refreshState()
    }

    /** 探测本机能力（核心数 / 堆上限 / 是否低内存设备） */
    fun deviceTier(): ModelCatalog.DeviceTier = ModelCatalog.detectDevice(context)

    fun select(spec: AsrModelSpec) {
        _selectedId.value = spec.id
        refreshState()
    }

    /** 指定模型是否已完整安装（逐文件比对体积） */
    fun isInstalled(spec: AsrModelSpec): Boolean =
        spec.files.all { isPresent(File(dirFor(spec), it.name), it) }

    fun isReady(): Boolean = isInstalled(selectedSpec)

    fun usedBytesFor(spec: AsrModelSpec): Long =
        dirFor(spec).listFiles()?.sumOf { it.length() } ?: 0L

    fun usedBytes(): Long = ModelCatalog.all.sumOf { usedBytesFor(it) }

    fun installs(): List<ModelInstall> = ModelCatalog.all.map {
        ModelInstall(spec = it, installed = isInstalled(it), usedBytes = usedBytesFor(it))
    }

    /** 已下载则刷新状态 */
    fun refreshState() {
        _state.value = if (isReady()) ModelState.Ready else ModelState.NotReady
    }

    suspend fun download(spec: AsrModelSpec = selectedSpec): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = dirFor(spec)
            for ((index, file) in spec.files.withIndex()) {
                val target = File(dir, file.name)
                if (isPresent(target, file)) continue
                if (target.exists()) target.delete()

                val temp = File(dir, "${file.name}.part")
                var lastError: Throwable? = null
                var done = false
                for (url in file.urls) {
                    try {
                        downloadOne(url, temp, file, spec, index)
                        done = true
                        break
                    } catch (t: Throwable) {
                        lastError = t
                        Log.w(TAG, "mirror failed: $url -> ${t.message}")
                    }
                }
                if (!done) throw IOException("所有镜像都失败: ${lastError?.message}")

                val actual = sha256(temp)
                if (!actual.equals(file.sha256, ignoreCase = true)) {
                    temp.delete()
                    throw IOException("校验失败（${file.name}）：期望 ${file.sha256.take(12)}… 实际 ${actual.take(12)}…")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) throw IOException("无法写入 ${target.name}")
                Log.i(TAG, "${spec.id}/${file.name} ready (${target.length()} bytes)")
            }
            refreshState()
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "download failed for ${spec.id}", t)
            _state.value = ModelState.Failed(t.message ?: "下载失败")
            Result.failure(t)
        }
    }

    /** 删除某个模型（默认删当前选中） */
    fun delete(spec: AsrModelSpec = selectedSpec) {
        dirFor(spec).listFiles()?.forEach { it.delete() }
        refreshState()
    }

    /** 删除所有模型 */
    fun deleteAll() {
        ModelCatalog.all.forEach { spec -> dirFor(spec).listFiles()?.forEach { it.delete() } }
        _state.value = ModelState.NotReady
    }

    /**
     * 从本地文件导入（用户自行下载的模型文件）。
     *
     * 按「当前选中模型」的清单校验哈希；v1.0 只能导入 SenseVoice。
     */
    suspend fun importModel(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val spec = selectedSpec
        try {
            val primary = spec.primaryFile
            val target = File(dirFor(spec), primary.name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取所选文件")

            val actual = sha256(target)
            if (!actual.equals(primary.sha256, ignoreCase = true)) {
                target.delete()
                throw IOException("所选文件不是预期的 ${spec.displayName} 模型（哈希不匹配）")
            }
            if (!isInstalled(spec)) {
                throw IOException("还缺少 tokens.txt 等文件，请一并从模型页面下载")
            }
            refreshState()
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "import failed", t)
            _state.value = ModelState.Failed(t.message ?: "导入失败")
            Result.failure(t)
        }
    }

    /** 导入 tokens.txt（按当前选中模型校验） */
    suspend fun importTokens(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val spec = selectedSpec
        try {
            val tokenSpec = spec.files.firstOrNull { it.name == "tokens.txt" }
                ?: throw IOException("当前模型不需要 tokens.txt")
            val target = File(dirFor(spec), tokenSpec.name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取所选文件")
            val actual = sha256(target)
            if (!actual.equals(tokenSpec.sha256, ignoreCase = true)) {
                target.delete()
                throw IOException("所选文件不是预期的 tokens.txt")
            }
            refreshState()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun downloadOne(
        url: String,
        temp: File,
        file: AsrModelFile,
        spec: AsrModelSpec,
        index: Int,
    ) {
        val existing = if (temp.exists()) temp.length() else 0L
        val request = Request.Builder()
            .url(url)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应为空")
            val append = existing > 0 && response.code == 206
            val startBytes = if (append) existing else 0L
            val remaining = body.contentLength().takeIf { it > 0 } ?: (file.sizeBytes - startBytes)
            val knownTotal = startBytes + remaining

            RandomAccessFile(temp, "rw").use { raf ->
                if (append) raf.seek(existing) else raf.setLength(0)
                body.byteStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    var written = startBytes
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        raf.write(buffer, 0, read)
                        written += read
                        _state.value = ModelState.Downloading(
                            modelId = spec.id,
                            fileName = file.name,
                            fileBytes = written,
                            fileTotal = knownTotal,
                            overallPercent = overallPercent(spec, index, written, knownTotal),
                        )
                    }
                }
            }
            if (temp.length() < file.sizeBytes) {
                throw IOException("文件不完整（${temp.length()}/${file.sizeBytes}），可重试续传")
            }
        }
    }

    private fun overallPercent(spec: AsrModelSpec, index: Int, written: Long, total: Long): Int {
        // 按字节加权（而非按文件数），否则 tokens.txt 会占掉与模型同等的进度
        val totalBytes = spec.totalBytes.coerceAtLeast(1L)
        val before = spec.files.take(index).sumOf { it.sizeBytes }
        val fraction = if (total > 0) written.toDouble() / total else 0.0
        val done = before + (fraction * spec.files[index].sizeBytes)
        return ((done / totalBytes) * 100).toInt().coerceIn(0, 100)
    }

    private fun isPresent(file: File, spec: AsrModelFile): Boolean =
        file.exists() && file.length() == spec.sizeBytes

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ModelManager"
        const val ROOT = "models"
    }
}
