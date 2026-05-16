package com.zob.recorder.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SceneModule {

    @Provides
    @Singleton
    @Named("scenesDir")
    fun provideScenesDirectory(
        @ApplicationContext context: Context
    ): File {
        return File(context.filesDir, "scenes").also { it.mkdirs() }
    }
}
