package com.echo.recall.core.di

import android.content.Context
import androidx.room.Room
import com.echo.recall.core.data.AudioStore
import com.echo.recall.core.data.db.EchoDatabase
import com.echo.recall.core.data.db.MemoryDao
import com.echo.recall.core.data.db.NoteDao
import com.echo.recall.core.data.db.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EchoDatabase =
        Room.databaseBuilder(context, EchoDatabase::class.java, EchoDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMemoryDao(db: EchoDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideTodoDao(db: EchoDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideNoteDao(db: EchoDatabase): NoteDao = db.noteDao()

    @Provides
    @Singleton
    fun provideAudioStore(@ApplicationContext context: Context): AudioStore = AudioStore(context)
}
