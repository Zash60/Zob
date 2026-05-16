package com.zob.recorder.ui.screens.streaming

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zob.recorder.data.SettingsRepository
import com.zob.recorder.encoder.StreamEncoder
import com.zob.recorder.model.RecordingPreset
import com.zob.recorder.model.StreamConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, FAILED
}

data class StreamingUiState(
    val streamConfig: StreamConfig = StreamConfig(),
    val urlError: String? = null,
    val keyError: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionErrorMessage: String? = null,
    val isStreaming: Boolean = false,
    val selectedPresetId: String = "balanced"
)

@HiltViewModel
class StreamingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val streamEncoder: StreamEncoder
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamingUiState())
    val uiState: StateFlow<StreamingUiState> = _uiState.asStateFlow()

    init {
        loadSavedConfig()
        setupEncoderCallbacks()
    }

    private fun loadSavedConfig() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.update {
                it.copy(
                    streamConfig = StreamConfig(
                        rtmpUrl = settings.rtmpUrl,
                        streamKey = settings.streamKey,
                        selectedPresetId = settings.defaultPresetId
                    ),
                    selectedPresetId = settings.defaultPresetId
                )
            }
        }
    }

    private fun setupEncoderCallbacks() {
        streamEncoder.onConnectionStarted = {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
        }
        streamEncoder.onConnected = {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.CONNECTED,
                    connectionErrorMessage = null,
                    isStreaming = true
                )
            }
        }
        streamEncoder.onConnectionFailed = { reason ->
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.FAILED,
                    connectionErrorMessage = reason,
                    isStreaming = false
                )
            }
        }
        streamEncoder.onDisconnected = {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    isStreaming = false
                )
            }
        }
    }

    fun setStreamConfig(url: String, key: String) {
        val urlError = validateUrl(url)
        val keyError = validateStreamKey(key)
        _uiState.update {
            it.copy(
                streamConfig = it.streamConfig.copy(rtmpUrl = url, streamKey = key),
                urlError = urlError,
                keyError = keyError
            )
        }
    }

    fun setUrl(url: String) {
        val urlError = validateUrl(url)
        _uiState.update {
            it.copy(
                streamConfig = it.streamConfig.copy(rtmpUrl = url),
                urlError = urlError
            )
        }
    }

    fun setStreamKey(key: String) {
        val keyError = validateStreamKey(key)
        _uiState.update {
            it.copy(
                streamConfig = it.streamConfig.copy(streamKey = key),
                keyError = keyError
            )
        }
    }

    fun selectPreset(presetId: String) {
        _uiState.update {
            it.copy(selectedPresetId = presetId)
        }
        viewModelScope.launch {
            settingsRepository.setDefaultPresetId(presetId)
        }
    }

    fun testConnection() {
        val state = _uiState.value
        val urlError = validateUrl(state.streamConfig.rtmpUrl)
        val keyError = validateStreamKey(state.streamConfig.streamKey)

        if (urlError != null || keyError != null) {
            _uiState.update { it.copy(urlError = urlError, keyError = keyError) }
            return
        }

        // Save config before testing
        viewModelScope.launch {
            settingsRepository.setRtmpUrl(state.streamConfig.rtmpUrl)
            settingsRepository.setStreamKey(state.streamConfig.streamKey)
        }

        _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING, connectionErrorMessage = null) }

        viewModelScope.launch {
            try {
                streamEncoder.startStream(state.streamConfig.rtmpUrl, state.streamConfig.streamKey)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.FAILED,
                        connectionErrorMessage = e.localizedMessage ?: "Connection failed"
                    )
                }
            }
        }
    }

    fun startStreaming() {
        val state = _uiState.value
        val urlError = validateUrl(state.streamConfig.rtmpUrl)
        val keyError = validateStreamKey(state.streamConfig.streamKey)

        if (urlError != null || keyError != null) {
            _uiState.update { it.copy(urlError = urlError, keyError = keyError) }
            return
        }

        viewModelScope.launch {
            settingsRepository.setRtmpUrl(state.streamConfig.rtmpUrl)
            settingsRepository.setStreamKey(state.streamConfig.streamKey)
        }

        viewModelScope.launch {
            try {
                streamEncoder.startStream(state.streamConfig.rtmpUrl, state.streamConfig.streamKey)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.FAILED,
                        connectionErrorMessage = e.localizedMessage ?: "Failed to start stream"
                    )
                }
            }
        }
    }

    fun stopStreaming() {
        viewModelScope.launch {
            try {
                streamEncoder.stopStream()
            } catch (_: Exception) {
                // Safe to ignore
            }
        }
    }

    private fun validateUrl(url: String): String? {
        if (url.isBlank()) return "Server URL is required"
        if (!url.startsWith("rtmp://") && !url.startsWith("rtmps://")) {
            return "URL must start with rtmp:// or rtmps://"
        }
        return null
    }

    private fun validateStreamKey(key: String): String? {
        if (key.isBlank()) return "Stream key is required"
        return null
    }

    fun clearConnectionError() {
        _uiState.update { it.copy(connectionErrorMessage = null) }
    }
}
