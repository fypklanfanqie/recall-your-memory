package com.kyant.backdrop

import android.os.Build

/*
 * Vendored from Kyant0/AndroidLiquidGlass (Apache License 2.0)
 * https://github.com/Kyant0/AndroidLiquidGlass
 *
 * Original KMP expect/actual declarations flattened for a single Android
 * source set (the published artifact requires a newer Kotlin/Compose than
 * this project uses).
 */
fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
