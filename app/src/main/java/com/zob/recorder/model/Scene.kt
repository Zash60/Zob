package com.zob.recorder.model

import kotlinx.serialization.Serializable

@Serializable
data class Scene(
    val id: String,
    val name: String,
    val sources: List<Source> = listOf(ScreenSource(id = "default_screen")),
    val transitionType: TransitionType = TransitionType.CUT,
    val transitionDurationMs: Long = 300,
    val sortOrder: Int = 0
)
