package com.echo.recall.core.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.echo.recall.core.data.MemoryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 保留策略：未收藏的记忆 7 天后连音频带文字一起清除（收藏的永久保留）。
 * 每日执行一次；App 启动时也会立即调度一次。
 */
@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val memoryRepository: MemoryRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val removed = runCatching { memoryRepository.cleanupExpired() }.getOrDefault(0)
        return Result.success(workDataOf(KEY_REMOVED to removed))
    }

    companion object {
        const val KEY_REMOVED = "removed"
        private const val UNIQUE_NAME = "echo-retention"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            runCatching {
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
