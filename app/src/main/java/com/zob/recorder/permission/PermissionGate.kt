package com.zob.recorder.permission

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionGate(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }

    var hasAudio by remember { mutableStateOf(permissionManager.hasRecordAudioPermission()) }
    var hasNotifications by remember { mutableStateOf(permissionManager.hasNotificationPermission()) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudio = granted }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifications = granted }

    val allGranted = hasAudio && hasNotifications

    if (allGranted) {
        content()
    } else {
        PermissionRationaleScreen(
            missingAudio = !hasAudio,
            missingNotifications = !hasNotifications,
            onRequestAudio = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }
}

@Composable
private fun PermissionRationaleScreen(
    missingAudio: Boolean,
    missingNotifications: Boolean,
    onRequestAudio: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Zob needs a few permissions to record your screen.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (missingAudio) {
            PermissionItem(
                title = "Microphone",
                description = "Required to record audio with your screen capture.",
                onClick = onRequestAudio
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (missingNotifications) {
            PermissionItem(
                title = "Notifications",
                description = "Shows recording controls while capturing your screen.",
                onClick = onRequestNotifications
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onClick) {
                Text("Grant")
            }
        }
    }
}

// Reusable composable for requesting MediaProjection at recording time
@Composable
fun rememberMediaProjectionLauncher(
    onResult: (resultCode: Int, data: Intent?) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onResult(result.resultCode, result.data)
    }
}
