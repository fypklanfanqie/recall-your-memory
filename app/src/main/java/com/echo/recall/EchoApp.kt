package com.echo.recall

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.echo.recall.core.asr.ModelManager
import com.echo.recall.core.data.settings.SettingsRepository
import com.echo.recall.core.service.RetentionWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EchoApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var modelManager: ModelManager
    @Inject lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 7 天保留策略：每日清理一次未收藏的记忆
        RetentionWorker.schedule(this)

        // 尽早确定「当前使用哪个本地转写模型」：
        // 优先用户选择 → 已装好的 v1.0 模型 → 按设备能力推荐
        appScope.launch {
            runCatching {
                val saved = settingsRepository.settings.first().asrModelId
                modelManager.initSelection(saved)
            }
        }
    }
}
