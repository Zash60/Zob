package com.zob.recorder.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zob.recorder.data.RecordingRepository
import com.zob.recorder.data.SettingsRepository
import com.zob.recorder.encoder.StreamEncoder
import com.zob.recorder.model.PRESET_BALANCED
import com.zob.recorder.model.RecordingPreset
import com.zob.recorder.model.RecordingState
import com.zob.recorder.model.RecordingSummary
import com.zob.recorder.notification.NotificationHelper
import com.zob.recorder.permission.PermissionManager
import com.zob.recorder.service.RecordingStateManager
import com.zob.recorder.service.ScreenRecorderService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recordingState: RecordingState = RecordingState.Idle,
    val recordings: List<RecordingSummary> = emptyList(),
    val selectedPreset: RecordingPreset = PRESET_BALANCED,
    val isLoading: Boolean = true,
    val hasPermissions: Boolean = true,
    val isStreaming: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateManager: RecordingStateManager,
    private val recordingRepository: RecordingRepository,
    private val streamEncoder: StreamEncoder,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val permissionManager = PermissionManager(context)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeRecordingState()
        observeRecordings()
        checkPermissions()
    }

    private fun observeRecordingState() {
        viewModelScope.launch {
            stateManager.state.collect { state ->
                _uiState.update {
                    it.copy(
                        recordingState = state,
                        isStreaming = state is RecordingState.Streaming
                                || state is RecordingState.RecAndStream
                    )
                }
            }
        }
    }

    private fun observeRecordings() {
        viewModelScope.launch {
            recordingRepository.getRecordings().collect { list ->
                _uiState.update { it.copy(recordings = list, isLoading = false) }
            }
        }
    }

    private fun checkPermissions() {
        _uiState.update {
            it.copy(hasPermissions = permissionManager.hasAllRequiredPermissions())
        }
    }

    fun startRecording(resultCode: Int, data: Intent) {
        val intent = ScreenRecorderService.createStartIntent(context, resultCode, data)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopRecording() {
        val intent = Intent(context, ScreenRecorderService::class.java).apply {
            action = NotificationHelper.ACTION_STOP
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun startStream() {
        viewModelScope.launch {
            try {
                val rtmpUrl = settingsRepository.rtmpUrl.first()
                val streamKey = settingsRepository.streamKey.first()
                if (rtmpUrl.isBlank()) {
                    _uiState.update {
                        it.copy(errorMessage = "Stream URL not configured. Go to Settings first.")
                    }
                    return@launch
                }
                streamEncoder.startStream(rtmpUrl, streamKey)
                stateManager.updateState(RecordingState.Streaming())
            } catch (e: IllegalStateException) {
                _uiState.update {
                    it.copy(errorMessage = "Stream encoder not yet initialized.")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Stream failed: ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun stopStream() {
        viewModelScope.launch {
            try {
                streamEncoder.stopStream()
                stateManager.updateState(RecordingState.Idle)
            } catch (_: Exception) {
            }
        }
    }

    fun selectPreset(preset: RecordingPreset) {
        _uiState.update { it.copy(selectedPreset = preset) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun deleteRecording(uri: Uri) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(uri)
        }
    }

    fun refreshRecordings() {
        _uiState.update { it.copy(isLoading = true) }
        observeRecordings()
    }

    fun recheckPermissions() {
        checkPermissions()
    }
}
