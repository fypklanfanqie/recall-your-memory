package com.echo.recall.core.data.settings

/** 人声检测灵敏度（映射到 silero-vad 阈值，M1 接入） */
enum class VadSensitivity { LOW, MEDIUM, HIGH }

/**
 * 功耗档：决定「唤醒锁策略」与「静默期占空比」，与灵敏度解耦。
 *
 * - [BALANCED] 默认。静默期仍全速采集，**零丢音**；仅做无损省电（去锁化 / 降频 ticker / 释放唤醒锁）。
 * - [SAVER] 极致省电。静默期按 [SILENCE_DUTY_OFF_MS] 占空比停读，靠能量预判兜底。
 *   极端情况下可能丢掉静默期的环境音（人声起点有前置能量预判保护）。
 */
enum class PowerProfile { BALANCED, SAVER }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 全局设置。窗口时长 = 点「回溯」时向前取的时长（30 秒 ~ 5 分钟）。
 */
data class EchoSettings(
    val windowMinutes: Float = DEFAULT_WINDOW_MINUTES,
    val vadSensitivity: VadSensitivity = VadSensitivity.MEDIUM,
    val powerProfile: PowerProfile = PowerProfile.BALANCED,
    val triggerHaptic: Boolean = false,
    /** 选中的本地转写模型 id（空 = 尚未按设备推荐初始化） */
    val asrModelId: String = "",
    val glassMode: String = "LIQUID",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoSummary: Boolean = false,
    val recordingEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    // ---- 液态玻璃可调参数（语义严格对齐官方 Kyant0 Playground） ----
    val glassBlurDp: Float = DEFAULT_GLASS_BLUR_DP,
    /** 折射高度比例：refractionHeight = fraction × minDimension × 0.5 */
    val glassRefractionHeightFraction: Float = DEFAULT_REFRACTION_FRACTION,
    /** 折射强度比例：refractionAmount = fraction × minDimension */
    val glassRefractionAmountFraction: Float = DEFAULT_REFRACTION_FRACTION,
    val glassChromatic: Boolean = true,
    val glassHighlight: Float = 0.7f,
    val glassTint: Float = DEFAULT_GLASS_TINT,
    // ---- 背景（空 = 默认渐变光斑） ----
    val backgroundImagePath: String = "",
) {
    val windowSeconds: Int get() = (windowMinutes * 60f).toInt()

    fun windowLabel(): String = formatWindow(windowMinutes)

    companion object {
        const val MIN_WINDOW_MINUTES = 0.5f
        const val MAX_WINDOW_MINUTES = 5f
        const val DEFAULT_WINDOW_MINUTES = 3f

        /** 极致省电档：静默期停读时长（占空比 1:4 → 读 512ms / 停 1536ms） */
        const val SILENCE_DUTY_ON_MS = 512L
        const val SILENCE_DUTY_OFF_MS = 1536L

        /** 连续静默多久后进入 COOLDOWN / SAVER 停读 */
        const val SILENCE_COOLDOWN_MS = 3_000L
        const val SILENCE_DEEP_MS = 30_000L

        /** 与官方 Playground 一致的默认折射比例 */
        const val DEFAULT_REFRACTION_FRACTION = 0.2f

        /** 折射比例上限（0..1；1.0 时 refractionAmount = 整个短边） */
        const val MAX_REFRACTION_FRACTION = 1f

        /** 玻璃默认值（「恢复官方默认参数」用它） */
        const val DEFAULT_GLASS_BLUR_DP = 18f
        const val DEFAULT_GLASS_TINT = 0.25f

        fun formatWindow(minutes: Float): String {
            val totalSeconds = (minutes * 60f).toInt()
            return if (totalSeconds < 60) {
                "$totalSeconds 秒"
            } else if (totalSeconds % 60 == 0) {
                "${totalSeconds / 60} 分钟"
            } else {
                "${totalSeconds / 60} 分 ${totalSeconds % 60} 秒"
            }
        }
    }
}
