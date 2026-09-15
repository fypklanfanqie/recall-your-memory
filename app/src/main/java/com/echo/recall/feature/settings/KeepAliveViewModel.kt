package com.echo.recall.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.asr.ModelManager
import com.echo.recall.core.audio.RecorderEngine
import com.echo.recall.core.data.AudioStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class KeepAliveUi(
    val ignoringBatteryOptimizations: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val listening: Boolean = false,
    val audioBytes: Long = 0L,
    val modelBytes: Long = 0L,
    val brand: String = "",
    val steps: List<String> = emptyList(),
)

@HiltViewModel
class KeepAliveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: RecorderEngine,
    private val audioStore: AudioStore,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(KeepAliveUi())
    val ui: StateFlow<KeepAliveUi> = _ui.asStateFlow()

    val status = engine.status

    init {
        val guide = OemGuides.forManufacturer(Build.MANUFACTURER)
        _ui.value = _ui.value.copy(brand = guide.brand, steps = guide.steps)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val power = context.getSystemService(PowerManager::class.java)
            val ignoring = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            val notifications = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            val audio = withContext(Dispatchers.IO) { audioStore.usedBytes() }
            val model = withContext(Dispatchers.IO) { modelManager.usedBytes() }
            _ui.value = _ui.value.copy(
                ignoringBatteryOptimizations = ignoring,
                notificationsEnabled = notifications,
                listening = engine.isListening,
                audioBytes = audio,
                modelBytes = model,
            )
        }
    }

    /** 请求忽略电池优化（需 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限，自用侧载无碍） */
    fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes <= 0L -> "0 MB"
        bytes < 1024L * 1024L -> "${"%.0f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    }
}
