package com.zob.recorder.ui.screens.home

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zob.recorder.model.DEFAULT_PRESETS
import com.zob.recorder.model.RecordingPreset
import com.zob.recorder.model.RecordingState
import com.zob.recorder.model.RecordingSummary
import com.zob.recorder.navigation.RecordingPlaybackRoute
import com.zob.recorder.navigation.SettingsRoute
import com.zob.recorder.permission.rememberMediaProjectionLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current

    val mediaProjectionLauncher = rememberMediaProjectionLauncher { resultCode, data ->
        if (resultCode == Activity.RESULT_OK && data != null) {
            viewModel.startRecording(resultCode, data)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Zob",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(SettingsRoute) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else if (!uiState.hasPermissions) {
            PermissionWarning(
                onRetry = viewModel::recheckPermissions,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            HomeContent(
                uiState = uiState,
                onStartRecording = {
                    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                            as MediaProjectionManager
                    mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                },
                onStopRecording = viewModel::stopRecording,
                onStartStream = viewModel::startStream,
                onStopStream = viewModel::stopStream,
                onSelectPreset = viewModel::selectPreset,
                onRecordingClick = { summary ->
                    navController.navigate(RecordingPlaybackRoute(summary.id))
                },
                onDeleteRecording = { summary ->
                    viewModel.deleteRecording(Uri.parse(summary.filePath))
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onSelectPreset: (RecordingPreset) -> Unit,
    onRecordingClick: (RecordingSummary) -> Unit,
    onDeleteRecording: (RecordingSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = uiState.recordingState !is RecordingState.Idle
    val recordState = uiState.recordingState

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = isActive,
                enter = slideInVertically() + fadeIn(),
                exit = fadeOut()
            ) {
                RecordingStatusCard(recordingState = recordState)
            }
        }

        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RecordButton(
                        isRecording = isActive,
                        onClick = {
                            if (isActive) onStopRecording()
                            else onStartRecording()
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isActive) "Tap to stop" else "Tap to record",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            StreamToggle(
                isStreaming = uiState.isStreaming,
                onStartStream = onStartStream,
                onStopStream = onStopStream
            )
        }

        item {
            PresetChipsRow(
                presets = DEFAULT_PRESETS,
                selectedPreset = uiState.selectedPreset,
                onSelectPreset = onSelectPreset
            )
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Recent Recordings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (uiState.recordings.isEmpty()) {
            item {
                EmptyState(modifier = Modifier.fillMaxWidth())
            }
        } else {
            items(
                items = uiState.recordings,
                key = { it.id }
            ) { summary ->
                RecordingSummaryCard(
                    summary = summary,
                    onClick = { onRecordingClick(summary) },
                    onDelete = { onDeleteRecording(summary) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RecordingStatusCard(
    recordingState: RecordingState,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (recordingState) {
        is RecordingState.Starting -> "Starting..." to MaterialTheme.colorScheme.primary
        is RecordingState.Recording -> "Recording" to MaterialTheme.colorScheme.error
        is RecordingState.Streaming -> "Streaming" to MaterialTheme.colorScheme.tertiary
        is RecordingState.RecAndStream -> "Recording + Streaming" to MaterialTheme.colorScheme.error
        is RecordingState.Stopping -> "Stopping..." to MaterialTheme.colorScheme.outline
        is RecordingState.Error -> "Error" to MaterialTheme.colorScheme.error
        else -> return
    }

    val durationMs = when (recordingState) {
        is RecordingState.Recording -> recordingState.durationMs
        is RecordingState.Streaming -> recordingState.durationMs
        is RecordingState.RecAndStream -> recordingState.durationMs
        else -> 0L
    }

    val fileSize = when (recordingState) {
        is RecordingState.Recording -> recordingState.fileSize
        else -> 0L
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (fileSize > 0) {
                Text(
                    text = formatFileSize(fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recordPulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        animationSpec = tween(300),
        label = "recordBg"
    )

    Box(
        modifier = modifier.size(88.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Surface(
                modifier = Modifier
                    .size(88.dp)
                    .scale(pulseScale),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha)
            ) {}
        }

        Surface(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = bgColor,
            tonalElevation = if (isRecording) 0.dp else 3.dp,
            shadowElevation = if (isRecording) 8.dp else 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop
                    else Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "Stop recording" else "Start recording",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

@Composable
private fun StreamToggle(
    isStreaming: Boolean,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isStreaming) Icons.Default.Wifi
                else Icons.Default.WifiOff,
                contentDescription = null,
                tint = if (isStreaming) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Streaming",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (isStreaming) "Connected" else "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = { if (isStreaming) onStopStream() else onStartStream() },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isStreaming) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = if (isStreaming) "Stop" else "Start"
                )
            }
        }
    }
}

@Composable
private fun PresetChipsRow(
    presets: List<RecordingPreset>,
    selectedPreset: RecordingPreset,
    onSelectPreset: (RecordingPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.id == selectedPreset.id,
                onClick = { onSelectPreset(preset) },
                label = { Text(preset.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Movie,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No recordings yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Tap the record button above to\ncapture your first recording",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PermissionWarning(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Zob needs audio recording and notification permissions to function.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            FilledTonalButton(onClick = onRetry) {
                Text("Grant Permissions")
            }
        }
    }
}
