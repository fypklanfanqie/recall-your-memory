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

/** 本地转写模型状态 */
sealed interface ModelState {
    data object NotReady : ModelState
    data class Downloading(
        val fileName: String,
        val fileBytes: Long,
        val fileTotal: Long,
        val overallPercent: Int,
    ) : ModelState

    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * 离线转写模型管理：多镜像 + 断点续传 + SHA-256 校验 + 本地导入。
 * 模型不进 APK（228MB），首次使用时下载到 filesDir/models/sense-voice。
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

    val modelDir: File
        get() = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    val modelPath: String get() = File(modelDir, SenseVoiceFiles.MODEL.name).absolutePath
    val tokensPath: String get() = File(modelDir, SenseVoiceFiles.TOKENS.name).absolutePath

    fun isReady(): Boolean {
        // 诊断日志：真机排查模型可见性（是否存在 / 长度是否与内置期望一致 / 能否读取）
        val checks = SenseVoiceFiles.all.map { spec ->
            val f = File(modelDir, spec.name)
            "${spec.name}(exists=${f.exists()}, len=${f.length()}, expect=${spec.sizeBytes}, readable=${f.canRead()})"
        }
        val ready = SenseVoiceFiles.all.all { isPresent(File(modelDir, it.name), it) }
        Log.i(TAG, "isReady=$ready dir=$modelDir ${checks.joinToString(" ")}")
        return ready
    }

    fun usedBytes(): Long = modelDir.listFiles()?.sumOf { it.length() } ?: 0L

    /** 已下载则刷新状态 */
    fun refreshState() {
        _state.value = if (isReady()) ModelState.Ready else ModelState.NotReady
    }

    suspend fun download(): Result<Unit> = withContext(Dispatchers.IO) {
        refreshState()
        if (isReady()) return@withContext Result.success(Unit)

        try {
            for ((index, file) in SenseVoiceFiles.all.withIndex()) {
                val target = File(modelDir, file.name)
                if (isPresent(target, file)) continue
                if (target.exists()) target.delete()

                val temp = File(modelDir, "${file.name}.part")
                var lastError: Throwable? = null
                var done = false
                for (url in file.urls) {
                    try {
                        downloadOne(url, temp, file, index)
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
                Log.i(TAG, "${file.name} ready (${target.length()} bytes)")
            }
            _state.value = ModelState.Ready
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "download failed", t)
            _state.value = ModelState.Failed(t.message ?: "下载失败")
            Result.failure(t)
        }
    }

    /** 从本地文件导入（用户自行下载的 model.int8.onnx） */
    suspend fun importModel(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val target = File(modelDir, SenseVoiceFiles.MODEL.name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取所选文件")

            val actual = sha256(target)
            if (!actual.equals(SenseVoiceFiles.MODEL.sha256, ignoreCase = true)) {
                target.delete()
                throw IOException("所选文件不是预期的 SenseVoice int8 模型（哈希不匹配）")
            }
            if (!File(modelDir, SenseVoiceFiles.TOKENS.name).exists()) {
                throw IOException("还缺少 tokens.txt，请同时从模型页面下载")
            }
            _state.value = ModelState.Ready
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "import failed", t)
            _state.value = ModelState.Failed(t.message ?: "导入失败")
            Result.failure(t)
        }
    }

    suspend fun importTokens(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val target = File(modelDir, SenseVoiceFiles.TOKENS.name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取所选文件")
            val actual = sha256(target)
            if (!actual.equals(SenseVoiceFiles.TOKENS.sha256, ignoreCase = true)) {
                target.delete()
                throw IOException("所选文件不是预期的 tokens.txt")
            }
            refreshState()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun deleteAll() {
        modelDir.listFiles()?.forEach { it.delete() }
        _state.value = ModelState.NotReady
    }

    private fun downloadOne(url: String, temp: File, file: AsrModelFile, index: Int) {
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
                        val overall = overallPercent(index, written, knownTotal)
                        _state.value = ModelState.Downloading(
                            fileName = file.name,
                            fileBytes = written,
                            fileTotal = knownTotal,
                            overallPercent = overall,
                        )
                    }
                }
            }
            if (temp.length() < file.sizeBytes) {
                throw IOException("文件不完整（${temp.length()}/${file.sizeBytes}），可重试续传")
            }
        }
    }

    private fun overallPercent(index: Int, written: Long, total: Long): Int {
        val fileFraction = if (total > 0) written.toDouble() / total else 0.0
        val perFile = 100.0 / SenseVoiceFiles.all.size
        return ((index * perFile) + fileFraction * perFile).toInt().coerceIn(0, 100)
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
        const val DIR = "models/sense-voice"
    }
}
