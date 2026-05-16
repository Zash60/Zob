package com.zob.recorder.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RecordingModule {

    // AudioCapturer will be provided here when implemented in T13
    // SceneCompositor will be provided here when implemented in T14
    // RecordingStateManager will be provided here when implemented in T21

    @Provides
    @Singleton
    fun provideRecordingDirectory(
        @ApplicationContext context: Context
    ): String {
        val dir = context.getExternalFilesDir(null)
        return dir?.absolutePath ?: context.filesDir.absolutePath
    }
}
