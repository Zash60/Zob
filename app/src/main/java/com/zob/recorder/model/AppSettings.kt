package com.zob.recorder.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultPresetId: String = "balanced",
    val rtmpUrl: String = "",
    val streamKey: String = "",
    val selectedCodec: Codec = Codec.H264,
    val recordingResolution: String = "1920x1080",
    val recordingFps: Int = 30,
    val recordingBitrate: Int = 5_000_000,
    val hasCompletedOnboarding: Boolean = false
)

@Serializable
enum class ThemeMode { LIGHT, DARK, SYSTEM }
