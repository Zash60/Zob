package com.zob.recorder.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class SceneEditorRoute(val sceneId: String)

@Serializable
data object StreamingConfigRoute

@Serializable
data object SettingsRoute

@Serializable
data class RecordingPlaybackRoute(val recordingId: String)
