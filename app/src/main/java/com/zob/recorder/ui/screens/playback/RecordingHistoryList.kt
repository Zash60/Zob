package com.zob.recorder.ui.screens.playback

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zob.recorder.model.RecordingSummary
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecordingHistoryList(
    recordings: List<RecordingSummary>,
    onRecordingClick: (RecordingSummary) -> Unit,
    onRecordingLongClick: (RecordingSummary) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recordings) { recording ->
            RecordingItem(
                recording = recording,
                onClick = { onRecordingClick(recording) },
                onLongClick = { onRecordingLongClick(recording) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingItem(
    recording: RecordingSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { 
                    showMenu = true
                    onLongClick() 
                }
            )
    ) {
        Box {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail placeholder
                Surface(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    // TODO: Use Coil to load thumbnail
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(text = recording.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${formatDuration(recording.durationMs)} • ${formatSize(recording.fileSize)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatDate(recording.dateCreated),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(text = { Text("Play") }, onClick = { showMenu = false; onClick() })
                DropdownMenuItem(text = { Text("Share") }, onClick = { showMenu = false })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false })
            }
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60))
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun formatSize(sizeBytes: Long): String {
    val mb = sizeBytes / (1024 * 1024)
    return "$mb MB"
}

fun formatDate(dateMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(dateMs))
}
