package com.zob.recorder.di

import android.content.Context
import com.zob.recorder.audio.AudioCapturer
import com.zob.recorder.encoder.StreamEncoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RecordingModule {

    // SceneCompositor will be provided here when implemented in T14

    @Provides
    @Singleton
    fun provideAudioCapturer(): AudioCapturer {
        return AudioCapturer()
    }

    @Provides
    @Singleton
    fun provideStreamEncoder(
        @ApplicationContext context: Context
    ): StreamEncoder {
        return StreamEncoder(context)
    }

    @Provides
    @Singleton
    fun provideRecordingDirectory(
        @ApplicationContext context: Context
    ): String {
        val dir = context.getExternalFilesDir(null)
        return dir?.absolutePath ?: context.filesDir.absolutePath
    }
}
