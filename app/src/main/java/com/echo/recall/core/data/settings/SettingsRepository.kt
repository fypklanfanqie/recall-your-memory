package com.echo.recall.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.echoSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "echo_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val windowMinutes = floatPreferencesKey("window_minutes")
        val vadSensitivity = stringPreferencesKey("vad_sensitivity")
        val powerProfile = stringPreferencesKey("power_profile")
        val triggerHaptic = booleanPreferencesKey("trigger_haptic")
        val asrModelId = stringPreferencesKey("asr_model_id")
        val glassMode = stringPreferencesKey("glass_mode")
        val legacyLiquidGlass = booleanPreferencesKey("liquid_glass")
        val themeMode = stringPreferencesKey("theme_mode")
        val autoSummary = booleanPreferencesKey("auto_summary")
        val recordingEnabled = booleanPreferencesKey("recording_enabled")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val glassBlur = floatPreferencesKey("glass_blur_dp")
        val glassRefractionHeight = floatPreferencesKey("glass_refraction_height_dp")
        val glassRefractionAmount = floatPreferencesKey("glass_refraction_amount_dp")
        val glassChromatic = booleanPreferencesKey("glass_chromatic")
        val glassHighlight = floatPreferencesKey("glass_highlight")
        val glassTint = floatPreferencesKey("glass_tint")
        val backgroundImagePath = stringPreferencesKey("background_image_path")
    }

    val settings: Flow<EchoSettings> = context.echoSettingsStore.data.map { prefs ->
        EchoSettings(
            windowMinutes = prefs[Keys.windowMinutes]
                ?.coerceIn(EchoSettings.MIN_WINDOW_MINUTES, EchoSettings.MAX_WINDOW_MINUTES)
                ?: EchoSettings.DEFAULT_WINDOW_MINUTES,
            vadSensitivity = prefs[Keys.vadSensitivity]?.let { runCatching { VadSensitivity.valueOf(it) }.getOrNull() }
                ?: VadSensitivity.MEDIUM,
            powerProfile = prefs[Keys.powerProfile]?.let { runCatching { PowerProfile.valueOf(it) }.getOrNull() }
                ?: PowerProfile.BALANCED,
            triggerHaptic = prefs[Keys.triggerHaptic] ?: false,
            asrModelId = prefs[Keys.asrModelId] ?: "",
            glassMode = prefs[Keys.glassMode]
                // 旧版布尔开关迁移：关 = 毛玻璃，开 = 液态玻璃
                ?: (prefs[Keys.legacyLiquidGlass]?.let { if (it) "LIQUID" else "FROSTED" }
                    ?: "LIQUID"),
            themeMode = prefs[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            autoSummary = prefs[Keys.autoSummary] ?: false,
            recordingEnabled = prefs[Keys.recordingEnabled] ?: false,
            onboardingDone = prefs[Keys.onboardingDone] ?: false,
            glassBlurDp = prefs[Keys.glassBlur] ?: 18f,
            glassRefractionHeightDp = prefs[Keys.glassRefractionHeight] ?: 24f,
            glassRefractionAmountDp = prefs[Keys.glassRefractionAmount] ?: 24f,
            glassChromatic = prefs[Keys.glassChromatic] ?: true,
            glassHighlight = prefs[Keys.glassHighlight] ?: 0.7f,
            glassTint = prefs[Keys.glassTint] ?: 0.25f,
            backgroundImagePath = prefs[Keys.backgroundImagePath] ?: "",
        )
    }

    suspend fun setWindowMinutes(minutes: Float) {
        context.echoSettingsStore.edit { prefs ->
            prefs[Keys.windowMinutes] =
                minutes.coerceIn(EchoSettings.MIN_WINDOW_MINUTES, EchoSettings.MAX_WINDOW_MINUTES)
        }
    }

    suspend fun setVadSensitivity(value: VadSensitivity) {
        context.echoSettingsStore.edit { it[Keys.vadSensitivity] = value.name }
    }

    suspend fun setPowerProfile(value: PowerProfile) {
        context.echoSettingsStore.edit { it[Keys.powerProfile] = value.name }
    }

    suspend fun setTriggerHaptic(enabled: Boolean) {
        context.echoSettingsStore.edit { it[Keys.triggerHaptic] = enabled }
    }

    suspend fun setAsrModelId(id: String) {
        context.echoSettingsStore.edit { it[Keys.asrModelId] = id }
    }

    suspend fun setGlassMode(mode: String) {
        context.echoSettingsStore.edit { it[Keys.glassMode] = mode }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.echoSettingsStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setAutoSummary(enabled: Boolean) {
        context.echoSettingsStore.edit { it[Keys.autoSummary] = enabled }
    }

    suspend fun setRecordingEnabled(enabled: Boolean) {
        context.echoSettingsStore.edit { it[Keys.recordingEnabled] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.echoSettingsStore.edit { it[Keys.onboardingDone] = done }
    }

    // ---- 液态玻璃参数 ----

    suspend fun setGlassBlur(dp: Float) {
        context.echoSettingsStore.edit { it[Keys.glassBlur] = dp.coerceIn(0f, 40f) }
    }

    suspend fun setGlassRefractionHeight(dp: Float) {
        context.echoSettingsStore.edit { it[Keys.glassRefractionHeight] = dp.coerceIn(0f, 40f) }
    }

    suspend fun setGlassRefractionAmount(dp: Float) {
        context.echoSettingsStore.edit { it[Keys.glassRefractionAmount] = dp.coerceIn(0f, 40f) }
    }

    suspend fun setGlassChromatic(enabled: Boolean) {
        context.echoSettingsStore.edit { it[Keys.glassChromatic] = enabled }
    }

    suspend fun setGlassHighlight(alpha: Float) {
        context.echoSettingsStore.edit { it[Keys.glassHighlight] = alpha.coerceIn(0f, 1f) }
    }

    suspend fun setGlassTint(alpha: Float) {
        context.echoSettingsStore.edit { it[Keys.glassTint] = alpha.coerceIn(0f, 1f) }
    }

    suspend fun setBackgroundImage(path: String) {
        context.echoSettingsStore.edit { it[Keys.backgroundImagePath] = path }
    }
}
