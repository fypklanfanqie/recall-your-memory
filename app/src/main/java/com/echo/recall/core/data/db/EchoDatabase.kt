package com.echo.recall.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MemoryEntity::class, TodoEntity::class, NoteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    abstract fun todoDao(): TodoDao

    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "echo.db"
    }
}
