package com.zob.recorder.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamConfig(
    val rtmpUrl: String = "",
    val streamKey: String = "",
    val selectedPresetId: String = "balanced"
)
