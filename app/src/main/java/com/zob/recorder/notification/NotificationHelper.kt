package com.zob.recorder.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zob.recorder.R
import com.zob.recorder.service.ScreenRecorderService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val CHANNEL_NAME = "Recording"
        const val NOTIFICATION_ID_RECORDING = 1001
        const val NOTIFICATION_ID_ERROR = 1002

        // Action identifiers
        const val ACTION_STOP = "com.zob.recorder.action.STOP"
        const val ACTION_PAUSE = "com.zob.recorder.action.PAUSE"
        const val ACTION_RESUME = "com.zob.recorder.action.RESUME"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Screen recording status and controls"
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildRecordingNotification(
        elapsedSeconds: Long,
        resolution: String = "1920x1080",
        isPaused: Boolean = false,
        isStreaming: Boolean = false
    ): Notification {
        val title = when {
            isStreaming -> "Zob \u2014 Recording & Streaming"
            else -> "Zob \u2014 Recording"
        }
        val elapsed = formatElapsed(elapsedSeconds)
        val content = "$elapsed \u00b7 $resolution${if (isPaused) " \u00b7 PAUSED" else ""}"

        val stopIntent = Intent(context, ScreenRecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)

        if (!isPaused) {
            val pauseIntent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                context, 1, pauseIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        } else {
            val resumeIntent = Intent(context, ScreenRecorderService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getService(
                context, 2, resumeIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        }

        return builder.build()
    }

    fun buildStreamingNotification(
        elapsedSeconds: Long,
        bitrate: Int = 0
    ): Notification {
        val elapsed = formatElapsed(elapsedSeconds)
        val content = "$elapsed \u00b7 Streaming${if (bitrate > 0) " \u00b7 ${bitrate / 1000} kbps" else ""}"

        val stopIntent = Intent(context, ScreenRecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Zob \u2014 Streaming")
            .setContentText(content)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }

    fun buildErrorNotification(message: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Zob \u2014 Recording Failed")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
    }

    fun showErrorNotification(message: String) {
        notificationManager.notify(NOTIFICATION_ID_ERROR, buildErrorNotification(message))
    }

    fun dismissRecordingNotification() {
        notificationManager.cancel(NOTIFICATION_ID_RECORDING)
    }

    private fun formatElapsed(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
