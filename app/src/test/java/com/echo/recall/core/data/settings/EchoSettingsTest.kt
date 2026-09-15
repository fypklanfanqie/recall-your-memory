package com.echo.recall.core.data.settings

import org.junit.Assert.assertEquals
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
    fun `min and max window bounds are 30 seconds and 5 minutes`() {
        assertEquals(0.5f, EchoSettings.MIN_WINDOW_MINUTES, 0.001f)
        assertEquals(5f, EchoSettings.MAX_WINDOW_MINUTES, 0.001f)
    }
}
