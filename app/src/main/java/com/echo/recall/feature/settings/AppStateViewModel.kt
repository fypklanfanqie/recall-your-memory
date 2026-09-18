package com.echo.recall.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.data.settings.EchoSettings
import com.echo.recall.core.data.settings.SettingsRepository
import com.echo.recall.core.data.settings.ThemeMode
import com.echo.recall.core.data.settings.VadSensitivity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import javax.inject.Inject

/**
 * 承载全局设置（主题 / 玻璃参数 / 录音 / 背景），供 Activity、Dock 与各页面共享。
 */
@HiltViewModel
class AppStateViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings: StateFlow<EchoSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = EchoSettings(),
    )

    fun setWindowMinutes(minutes: Float) = viewModelScope.launch { settingsRepository.setWindowMinutes(minutes) }

    fun setVadSensitivity(value: VadSensitivity) = viewModelScope.launch { settingsRepository.setVadSensitivity(value) }

    fun setGlassMode(mode: String) = viewModelScope.launch { settingsRepository.setGlassMode(mode) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setAutoSummary(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoSummary(enabled) }

    fun setRecordingEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setRecordingEnabled(enabled) }

    // ---- 液态玻璃参数 ----

    fun setGlassBlur(dp: Float) = viewModelScope.launch { settingsRepository.setGlassBlur(dp) }

    fun setGlassRefractionHeight(dp: Float) = viewModelScope.launch { settingsRepository.setGlassRefractionHeight(dp) }

    fun setGlassRefractionAmount(dp: Float) = viewModelScope.launch { settingsRepository.setGlassRefractionAmount(dp) }

    fun setGlassChromatic(enabled: Boolean) = viewModelScope.launch { settingsRepository.setGlassChromatic(enabled) }

    fun setGlassHighlight(alpha: Float) = viewModelScope.launch { settingsRepository.setGlassHighlight(alpha) }

    fun setGlassTint(alpha: Float) = viewModelScope.launch { settingsRepository.setGlassTint(alpha) }

    // ---- 背景图片 ----

    /** 把 SAF 选中的图片拷入 filesDir（唯一文件名，确保换图后 UI 重新加载），删除旧文件并记录新路径 */
    fun setBackgroundImage(uri: Uri) = viewModelScope.launch {
        val oldPath = settingsRepository.settings.first().backgroundImagePath
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val dest = File(context.filesDir, "background_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                dest.absolutePath
            }.getOrNull()
        }
        if (path != null) {
            if (!oldPath.isNullOrBlank() && oldPath != path) {
                runCatching { File(oldPath).delete() }
            }
            settingsRepository.setBackgroundImage(path)
        }
    }

    /** 删除自定义壁纸：清记录 + 删文件，回到内置渐变 */
    fun clearBackgroundImage() = viewModelScope.launch {
        val oldPath = settingsRepository.settings.first().backgroundImagePath
        withContext(Dispatchers.IO) {
            if (!oldPath.isNullOrBlank()) runCatching { File(oldPath).delete() }
        }
        settingsRepository.setBackgroundImage("")
    }

    /** 保存裁剪结果：按所见导出位图 → JPEG → 记录路径（复用唯一文件名逻辑） */
    suspend fun saveCroppedWallpaper(
        frame: IntSize,
        zoom: Float,
        pan: androidx.compose.ui.geometry.Offset,
        src: Bitmap,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val out = Bitmap.createBitmap(
                frame.width.coerceAtLeast(1),
                frame.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(out)
            canvas.drawColor(android.graphics.Color.BLACK)
            canvas.translate(pan.x, pan.y)
            canvas.scale(zoom, zoom)
            canvas.drawBitmap(src, 0f, 0f, null)
            val oldPath = settingsRepository.settings.first().backgroundImagePath
            val dest = File(context.filesDir, "background_${System.currentTimeMillis()}.jpg")
            java.io.FileOutputStream(dest).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (!oldPath.isNullOrBlank() && oldPath != dest.absolutePath) {
                runCatching { File(oldPath).delete() }
            }
            settingsRepository.setBackgroundImage(dest.absolutePath)
            true
        }.getOrDefault(false)
    }
}
