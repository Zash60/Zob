package com.zob.recorder.model

import kotlinx.serialization.Serializable

@Serializable
sealed class RecordingState {
    data object Idle : RecordingState()
    data object Starting : RecordingState()
    data class Recording(
        val durationMs: Long = 0,
        val fileSize: Long = 0,
        val filePath: String = ""
    ) : RecordingState()
    data class Streaming(
        val durationMs: Long = 0,
        val bitrate: Int = 0
    ) : RecordingState()
    data class RecAndStream(
        val durationMs: Long = 0,
        val bitrate: Int = 0,
        val fileSize: Long = 0
    ) : RecordingState()
    data object Stopping : RecordingState()
    data class Error(val message: String) : RecordingState()
}
