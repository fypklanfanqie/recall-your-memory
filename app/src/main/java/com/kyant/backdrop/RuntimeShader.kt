package com.kyant.backdrop

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toArgb

/*
 * Vendored from Kyant0/AndroidLiquidGlass (Apache License 2.0)
 * https://github.com/Kyant0/AndroidLiquidGlass
 *
 * KMP expect(interface + factory) 与 androidMain actual 合并为单源集实现。
 */
interface RuntimeShader {
    fun setFloatUniform(name: String, value: Float)

    fun setFloatUniform(name: String, value1: Float, value2: Float)

    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float)

    fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float)

    fun setFloatUniform(name: String, values: FloatArray)

    fun setIntUniform(name: String, value: Int)

    fun setIntUniform(name: String, value1: Int, value2: Int)

    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int)

    fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int)

    fun setIntUniform(name: String, values: IntArray)

    fun setColorUniform(name: String, color: Color)
}

/** AGSL 着色器仅在 API 33+ 可用；调用方应先用 [isRuntimeShaderSupported] 判断。 */
fun RuntimeShader(shaderString: String): RuntimeShader {
    check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        "RuntimeShader requires Android 13 (API 33)"
    }
    return AndroidRuntimeShader(android.graphics.RuntimeShader(shaderString))
}

fun RuntimeShader.asComposeShader(): Shader {
    return this.asAndroidRuntimeShader()
}

fun RuntimeShader.asAndroidRuntimeShader(): android.graphics.RuntimeShader {
    return (this as AndroidRuntimeShader).shader
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AndroidRuntimeShader(val shader: android.graphics.RuntimeShader) : RuntimeShader {

    override fun setFloatUniform(name: String, value: Float) {
        shader.setFloatUniform(name, value)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float) {
        shader.setFloatUniform(name, value1, value2)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
        shader.setFloatUniform(name, value1, value2, value3)
    }

    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
        shader.setFloatUniform(name, value1, value2, value3, value4)
    }

    override fun setFloatUniform(name: String, values: FloatArray) {
        shader.setFloatUniform(name, values)
    }

    override fun setIntUniform(name: String, value: Int) {
        shader.setIntUniform(name, value)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int) {
        shader.setIntUniform(name, value1, value2)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) {
        shader.setIntUniform(name, value1, value2, value3)
    }

    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {
        shader.setIntUniform(name, value1, value2, value3, value4)
    }

    override fun setIntUniform(name: String, values: IntArray) {
        shader.setIntUniform(name, values)
    }

    override fun setColorUniform(name: String, color: Color) {
        shader.setColorUniform(name, color.toArgb())
    }
}
