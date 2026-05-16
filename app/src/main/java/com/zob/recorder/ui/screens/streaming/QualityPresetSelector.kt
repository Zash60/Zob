package com.zob.recorder.ui.screens.streaming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zob.recorder.model.DEFAULT_PRESETS
import com.zob.recorder.model.RecordingPreset

@Composable
fun QualityPresetSelector(
    presets: List<RecordingPreset> = DEFAULT_PRESETS,
    selectedPresetId: String,
    onSelectPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Quality Preset",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                QualityPresetChip(
                    preset = preset,
                    selected = preset.id == selectedPresetId,
                    onClick = { onSelectPreset(preset.id) }
                )
            }
        }
    }
}

@Composable
private fun QualityPresetChip(
    preset: RecordingPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(preset.name) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
fun QualityPresetInfo(
    selectedPresetId: String,
    presets: List<RecordingPreset> = DEFAULT_PRESETS,
    modifier: Modifier = Modifier
) {
    val preset = presets.find { it.id == selectedPresetId } ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "${preset.resolutionWidth}x${preset.resolutionHeight} @ ${preset.fps}fps",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Bitrate: ${formatBitrate(preset.bitrate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatBitrate(bitrate: Int): String {
    return if (bitrate >= 1_000_000) {
        "${bitrate / 1_000_000} Mbps"
    } else {
        "${bitrate / 1_000} Kbps"
    }
}
