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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    /** 把 SAF 选中的图片拷入 filesDir/background.jpg 并记录路径 */
    fun setBackgroundImage(uri: Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val dest = File(context.filesDir, "background.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                dest.absolutePath
            }.getOrNull()
        }
        if (path != null) settingsRepository.setBackgroundImage(path)
    }

    fun clearBackgroundImage() = viewModelScope.launch { settingsRepository.setBackgroundImage("") }
}
