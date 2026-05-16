package com.zob.recorder.ui.screens.streaming

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingConfigScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var streamKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Streaming",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ServerUrlField(
                url = uiState.streamConfig.rtmpUrl,
                error = uiState.urlError,
                onUrlChange = { viewModel.setUrl(it) }
            )

            StreamKeyField(
                key = uiState.streamConfig.streamKey,
                error = uiState.keyError,
                visible = streamKeyVisible,
                onVisibilityToggle = { streamKeyVisible = !streamKeyVisible },
                onKeyChange = { viewModel.setStreamKey(it) }
            )

            ConnectionStatusIndicator(status = uiState.connectionStatus)

            AnimatedVisibility(visible = uiState.connectionErrorMessage != null) {
                Text(
                    text = uiState.connectionErrorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.connectionStatus != ConnectionStatus.CONNECTING
                ) {
                    if (uiState.connectionStatus == ConnectionStatus.CONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Test Connection")
                }
            }

            QualityPresetSelector(
                selectedPresetId = uiState.selectedPresetId,
                onSelectPreset = { viewModel.selectPreset(it) }
            )

            QualityPresetInfo(selectedPresetId = uiState.selectedPresetId)

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isStreaming) {
                Button(
                    onClick = { viewModel.stopStreaming() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Stop Streaming")
                }
            } else {
                Button(
                    onClick = { viewModel.startStreaming() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.urlError == null && uiState.keyError == null
                ) {
                    Text("Start Streaming")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ServerUrlField(
    url: String,
    error: String?,
    onUrlChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("rtmp://live.twitch.tv/app/") },
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
            supportingText = {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
    }
}

@Composable
private fun StreamKeyField(
    key: String,
    error: String?,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
    onKeyChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text("Stream Key") },
            placeholder = { Text("Enter your stream key") },
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
            supportingText = {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onVisibilityToggle) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "Hide stream key" else "Show stream key"
                    )
                }
            },
            singleLine = true
        )
    }
}

@Composable
private fun ConnectionStatusIndicator(status: ConnectionStatus) {
    val (label, color) = when (status) {
        ConnectionStatus.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.onSurfaceVariant
        ConnectionStatus.CONNECTING -> "Connecting..." to MaterialTheme.colorScheme.primary
        ConnectionStatus.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
        ConnectionStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}
