package com.zob.recorder.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.zob.recorder.model.AppSettings
import com.zob.recorder.model.Codec
import com.zob.recorder.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DEFAULT_PRESET_ID = stringPreferencesKey("default_preset_id")
        private val KEY_RTMP_URL = stringPreferencesKey("rtmp_url")
        private val KEY_STREAM_KEY = stringPreferencesKey("stream_key")
        private val KEY_SELECTED_CODEC = stringPreferencesKey("selected_codec")
        private val KEY_RESOLUTION = stringPreferencesKey("recording_resolution")
        private val KEY_FPS = intPreferencesKey("recording_fps")
        private val KEY_BITRATE = intPreferencesKey("recording_bitrate")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = try {
                ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: "SYSTEM")
            } catch (_: Exception) { ThemeMode.SYSTEM },
            defaultPresetId = prefs[KEY_DEFAULT_PRESET_ID] ?: "balanced",
            rtmpUrl = prefs[KEY_RTMP_URL] ?: "",
            streamKey = prefs[KEY_STREAM_KEY] ?: "",
            selectedCodec = try {
                Codec.valueOf(prefs[KEY_SELECTED_CODEC] ?: "H264")
            } catch (_: Exception) { Codec.H264 },
            recordingResolution = prefs[KEY_RESOLUTION] ?: "1920x1080",
            recordingFps = prefs[KEY_FPS] ?: 30,
            recordingBitrate = prefs[KEY_BITRATE] ?: 5_000_000,
            hasCompletedOnboarding = prefs[KEY_ONBOARDING_COMPLETED] ?: false
        )
    }

    // Individual flow accessors for components that need specific settings
    val themeMode: Flow<ThemeMode> = settings.map { it.themeMode }
    val rtmpUrl: Flow<String> = settings.map { it.rtmpUrl }
    val streamKey: Flow<String> = settings.map { it.streamKey }
    val defaultPresetId: Flow<String> = settings.map { it.defaultPresetId }

    // Suspend setters
    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDefaultPresetId(id: String) {
        dataStore.edit { prefs -> prefs[KEY_DEFAULT_PRESET_ID] = id }
    }

    suspend fun setRtmpUrl(url: String) {
        dataStore.edit { prefs -> prefs[KEY_RTMP_URL] = url }
    }

    suspend fun setStreamKey(key: String) {
        dataStore.edit { prefs -> prefs[KEY_STREAM_KEY] = key }
    }

    suspend fun setSelectedCodec(codec: Codec) {
        dataStore.edit { prefs -> prefs[KEY_SELECTED_CODEC] = codec.name }
    }

    suspend fun setRecordingResolution(resolution: String) {
        dataStore.edit { prefs -> prefs[KEY_RESOLUTION] = resolution }
    }

    suspend fun setRecordingFps(fps: Int) {
        dataStore.edit { prefs -> prefs[KEY_FPS] = fps }
    }

    suspend fun setRecordingBitrate(bitrate: Int) {
        dataStore.edit { prefs -> prefs[KEY_BITRATE] = bitrate }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_COMPLETED] = completed }
    }
}
