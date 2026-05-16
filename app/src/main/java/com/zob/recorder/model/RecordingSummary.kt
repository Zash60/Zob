package com.zob.recorder.model

data class RecordingSummary(
    val id: String,
    val fileName: String,
    val filePath: String,
    val durationMs: Long,
    val fileSize: Long,
    val dateCreated: Long,
    val resolution: String,
    val codec: String
)
