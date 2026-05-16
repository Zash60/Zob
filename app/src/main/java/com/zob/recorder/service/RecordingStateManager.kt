package com.zob.recorder.service

import com.zob.recorder.model.RecordingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingStateManager @Inject constructor() {

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)

    val state: StateFlow<RecordingState> = _state.asStateFlow()

    fun updateState(newState: RecordingState) {
        _state.value = newState
    }
}
