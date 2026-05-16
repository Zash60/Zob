package com.zob.recorder.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    companion object {
        const val RC_MEDIA_PROJECTION = 1001
    }

    // --- Audio Permission ---
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestRecordAudioPermission(permissionLauncher: ActivityResultLauncher<String>) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // --- Notification Permission (API 33+) ---
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true // Pre-API 33, notifications are always granted
    }

    fun requestNotificationPermission(permissionLauncher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // --- MediaProjection Permission (Screen Capture) ---
    fun hasMediaProjectionPermission(): Boolean {
        // MediaProjection is per-session consent (not a runtime permission)
        // Always returns false so the launcher triggers the consent dialog
        return false
    }

    fun createScreenCaptureIntent(): Intent {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mpm.createScreenCaptureIntent()
    }

    fun launchScreenCapture(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(createScreenCaptureIntent())
    }

    // --- All permissions check ---
    fun hasAllRequiredPermissions(): Boolean {
        return hasRecordAudioPermission() &&
               hasNotificationPermission()
        // MediaProjection is checked separately at recording start
    }
}
