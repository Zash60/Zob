package com.zob.recorder.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zob.recorder.data.SettingsRepository
import com.zob.recorder.model.Codec
import com.zob.recorder.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val selectedPresetId: String = "balanced",
    val selectedCodec: Codec = Codec.H264,
    val recordingResolution: String = "1920x1080",
    val recordingFps: Int = 30,
    val recordingBitrate: Int = 5_000_000,
    val audioEnabled: Boolean = true,
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.update {
                it.copy(
                    themeMode = settings.themeMode,
                    selectedPresetId = settings.defaultPresetId,
                    selectedCodec = settings.selectedCodec,
                    recordingResolution = settings.recordingResolution,
                    recordingFps = settings.recordingFps,
                    recordingBitrate = settings.recordingBitrate,
                    isLoading = false
                )
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setPreset(id: String) {
        _uiState.update { it.copy(selectedPresetId = id) }
        viewModelScope.launch {
            settingsRepository.setDefaultPresetId(id)
        }
    }

    fun setCodec(codec: Codec) {
        _uiState.update { it.copy(selectedCodec = codec) }
        viewModelScope.launch {
            settingsRepository.setSelectedCodec(codec)
        }
    }

    fun setResolution(res: String) {
        _uiState.update { it.copy(recordingResolution = res) }
        viewModelScope.launch {
            settingsRepository.setRecordingResolution(res)
        }
    }

    fun setFps(fps: Int) {
        _uiState.update { it.copy(recordingFps = fps) }
        viewModelScope.launch {
            settingsRepository.setRecordingFps(fps)
        }
    }

    fun setBitrate(bitrate: Int) {
        _uiState.update { it.copy(recordingBitrate = bitrate) }
        viewModelScope.launch {
            settingsRepository.setRecordingBitrate(bitrate)
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        _uiState.update { it.copy(audioEnabled = enabled) }
    }
}
