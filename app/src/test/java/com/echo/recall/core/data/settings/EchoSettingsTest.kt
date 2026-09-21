package com.echo.recall.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSettingsTest {

    @Test
    fun `formats the recording window in human readable chinese`() {
        assertEquals("30 秒", EchoSettings.formatWindow(0.5f))
        assertEquals("1 分钟", EchoSettings.formatWindow(1f))
        assertEquals("2 分 30 秒", EchoSettings.formatWindow(2.5f))
        assertEquals("3 分钟", EchoSettings.formatWindow(3f))
        assertEquals("5 分钟", EchoSettings.formatWindow(5f))
    }

    @Test
    fun `window seconds follows the slider value`() {
        assertEquals(30, EchoSettings(windowMinutes = 0.5f).windowSeconds)
        assertEquals(180, EchoSettings(windowMinutes = 3f).windowSeconds)
        assertEquals(300, EchoSettings(windowMinutes = 5f).windowSeconds)
    }

    @Test
    fun `window label uses the same formatting`() {
        assertEquals("2 分 30 秒", EchoSettings(windowMinutes = 2.5f).windowLabel())
    }

    @Test
    fun `defaults match the agreed product decisions`() {
        val defaults = EchoSettings()
        assertEquals(3f, defaults.windowMinutes, 0.001f)
        assertEquals(VadSensitivity.MEDIUM, defaults.vadSensitivity)
        assertEquals("LIQUID", defaults.glassMode)
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(false, defaults.autoSummary)
        assertEquals(false, defaults.recordingEnabled)
    }

    @Test
    fun `power profile defaults to balanced so no audio is lost`() {
        // 用户决策：默认「均衡」（零丢音），极致省电必须是显式选择
        assertEquals(PowerProfile.BALANCED, EchoSettings().powerProfile)
    }

    @Test
    fun `power profile has exactly two levels`() {
        assertEquals(listOf(PowerProfile.BALANCED, PowerProfile.SAVER), PowerProfile.entries.toList())
    }

    @Test
    fun `trigger haptic is off by default`() {
        // 震动会额外耗电，默认必须关
        assertEquals(false, EchoSettings().triggerHaptic)
    }

    @Test
    fun `asr model id starts empty so the device recommendation applies`() {
        assertEquals("", EchoSettings().asrModelId)
    }

    @Test
    fun `silence thresholds are ordered and sane`() {
        // COOLDOWN 必须先于 DEEP 触发，否则状态机会跳过中间档
        assertTrue(EchoSettings.SILENCE_COOLDOWN_MS < EchoSettings.SILENCE_DEEP_MS)
        assertTrue(EchoSettings.SILENCE_COOLDOWN_MS > 0)
        // 占空比 1:4 —— 停读必须明显长于读，否则省不下电
        assertTrue(EchoSettings.SILENCE_DUTY_OFF_MS > EchoSettings.SILENCE_DUTY_ON_MS)
    }

    @Test
    fun `duty cycle on window is about half a second`() {
        assertEquals(512L, EchoSettings.SILENCE_DUTY_ON_MS)
        assertEquals(1_536L, EchoSettings.SILENCE_DUTY_OFF_MS)
    }

    @Test
    fun `min and max window bounds are 30 seconds and 5 minutes`() {
        assertEquals(0.5f, EchoSettings.MIN_WINDOW_MINUTES, 0.001f)
        assertEquals(5f, EchoSettings.MAX_WINDOW_MINUTES, 0.001f)
    }
}
