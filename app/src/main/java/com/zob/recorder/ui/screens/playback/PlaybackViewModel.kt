package com.zob.recorder.ui.screens.playback

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zob.recorder.data.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaybackUiState(
    val recordingId: String = "",
    val fileUri: Uri? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val isMuted: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val recordingId: String = savedStateHandle.get<String>("recordingId") ?: ""

    private val _uiState = MutableStateFlow(PlaybackUiState(recordingId = recordingId))
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    init {
        loadRecording()
    }

    private fun loadRecording() {
        viewModelScope.launch {
            recordingRepository.getRecordings().collect { recordings ->
                val recording = recordings.find { it.id == recordingId }
                if (recording != null) {
                    val uri = Uri.parse(recording.filePath)
                    _uiState.update { 
                        it.copy(
                            fileUri = uri,
                            duration = recording.durationMs,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Recording not found") }
                }
            }
        }
    }

    fun play() {
        _uiState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(position: Long) {
        _uiState.update { it.copy(currentPosition = position) }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun share() {
        // Implementation will be handled in the UI layer via Intent
    }

    fun delete() {
        viewModelScope.launch {
            val uri = _uiState.value.fileUri
            if (uri != null) {
                recordingRepository.deleteRecording(uri)
            }
        }
    }
}
