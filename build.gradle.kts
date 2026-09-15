plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin：kotlin-android 插件不再需要
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
