package com.echo.recall.core.data.settings

/** 人声检测灵敏度（映射到 silero-vad 阈值，M1 接入） */
enum class VadSensitivity { LOW, MEDIUM, HIGH }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 全局设置。窗口时长 = 点「回溯」时向前取的时长（30 秒 ~ 5 分钟）。
 */
data class EchoSettings(
    val windowMinutes: Float = DEFAULT_WINDOW_MINUTES,
    val vadSensitivity: VadSensitivity = VadSensitivity.MEDIUM,
    val glassMode: String = "LIQUID",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoSummary: Boolean = false,
    val recordingEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    // ---- 液态玻璃可调参数（参考 Kyant0/AndroidLiquidGlass 的效果模型） ----
    val glassBlurDp: Float = 18f,
    val glassRefractionHeightDp: Float = 24f,
    val glassRefractionAmountDp: Float = 24f,
    val glassChromatic: Boolean = true,
    val glassHighlight: Float = 0.7f,
    val glassTint: Float = 0.25f,
    // ---- 背景（空 = 默认渐变光斑） ----
    val backgroundImagePath: String = "",
) {
    val windowSeconds: Int get() = (windowMinutes * 60f).toInt()

    fun windowLabel(): String = formatWindow(windowMinutes)

    companion object {
        const val MIN_WINDOW_MINUTES = 0.5f
        const val MAX_WINDOW_MINUTES = 5f
        const val DEFAULT_WINDOW_MINUTES = 3f

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
