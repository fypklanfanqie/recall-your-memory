package com.echo.recall.core.data

import android.content.Context
import java.io.File

/** 记忆音频存放位置：filesDir/memories/<id>.m4a */
class AudioStore(private val context: Context) {

    fun audioFile(id: String): File {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$id.m4a")
    }

    fun delete(id: String): Boolean = audioFile(id).delete()

    fun usedBytes(): Long {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) return 0L
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    companion object {
        const val DIR = "memories"
    }
}
