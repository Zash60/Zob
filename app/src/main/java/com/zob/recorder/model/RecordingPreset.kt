package com.zob.recorder.model

import kotlinx.serialization.Serializable

@Serializable
data class RecordingPreset(
    val id: String,
    val name: String,
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080,
    val fps: Int = 30,
    val bitrate: Int = 5_000_000,
    val codec: Codec = Codec.H264,
    val enableAudio: Boolean = true
)

@Serializable
enum class Codec { H264, H265 }

val PRESET_PERFORMANCE = RecordingPreset(
    id = "performance", name = "Performance",
    resolutionWidth = 1280, resolutionHeight = 720,
    fps = 30, bitrate = 3_000_000
)

val PRESET_BALANCED = RecordingPreset(
    id = "balanced", name = "Balanced",
    resolutionWidth = 1920, resolutionHeight = 1080,
    fps = 30, bitrate = 5_000_000
)

val PRESET_QUALITY = RecordingPreset(
    id = "quality", name = "Quality",
    resolutionWidth = 1920, resolutionHeight = 1080,
    fps = 60, bitrate = 10_000_000
)

val DEFAULT_PRESETS = listOf(PRESET_PERFORMANCE, PRESET_BALANCED, PRESET_QUALITY)
