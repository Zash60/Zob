package com.zob.recorder.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.Display
import com.zob.recorder.encoder.StreamEncoder
import com.zob.recorder.audio.AudioCapturer
import com.zob.recorder.compositor.SceneCompositor
import com.zob.recorder.data.RecordingRepository
import android.os.Environment
import java.io.File
import com.zob.recorder.encoder.EncoderConfig
import com.zob.recorder.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ScreenRecorderService : Service() {

    @Inject lateinit var mediaProjectionManager: MediaProjectionManager
    @Inject lateinit var displayManager: DisplayManager
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var streamEncoder: StreamEncoder
    @Inject lateinit var audioCapturer: AudioCapturer
    @Inject lateinit var sceneCompositor: SceneCompositor
    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var stateManager: RecordingStateManager

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val handler = Handler(Looper.getMainLooper())

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            handler.post { stopRecording() }
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationHelper.ACTION_STOP -> {
                stopRecording()
                return START_NOT_STICKY
            }
            NotificationHelper.ACTION_PAUSE,
            NotificationHelper.ACTION_RESUME -> {
                // Encoder-level pause/resume (Task 12+)
                return START_NOT_STICKY
            }
            else -> {
                handleStartRecording(intent)
                return START_NOT_STICKY
            }
        }
    }

    private fun handleStartRecording(intent: Intent?) {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        // Process death: no MediaProjection token available (one-shot on API 34+)
        if (resultCode == -1 || data == null) {
            stateManager.updateState(
                RecordingState.Error("Screen capture session unavailable — please restart recording")
            )
            notificationHelper.showErrorNotification(
                "Screen capture session unavailable. Please restart recording."
            )
            stopSelf()
            return
        }

        stateManager.updateState(RecordingState.Starting)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_RECORDING,
                notificationHelper.buildRecordingNotification(elapsedSeconds = 0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_RECORDING,
                notificationHelper.buildRecordingNotification(elapsedSeconds = 0)
            )
        }

        val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
        if (projection == null) {
            stateManager.updateState(
                RecordingState.Error("Failed to create MediaProjection — token may be stale")
            )
            notificationHelper.showErrorNotification(
                "Failed to create screen capture session. Please restart recording."
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    streamEncoder.pauseRecording()
                } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                    streamEncoder.resumeRecording()
                }
            }
        }
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)

        projection.registerCallback(projectionCallback, handler)

        val config = EncoderConfig()
        if (!streamEncoder.initialize(config)) {
            stateManager.updateState(RecordingState.Error("Failed to initialize encoder"))
            stopSelf()
            return
        }

        val metrics = DisplayMetrics()
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        display?.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        audioCapturer.setStreamEncoder(streamEncoder)
        audioCapturer.startCapture(projection, kotlinx.coroutines.MainScope())

        val surface = streamEncoder.getInputSurface()
        val surfaceTexture = SurfaceTexture(0).apply {
            setDefaultBufferSize(width, height)
        }
        sceneCompositor.start(surface, surfaceTexture, width, height)

        virtualDisplay = projection.createVirtualDisplay(
            "Zob",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            Surface(surfaceTexture),
            null,
            handler
        )

        val fileName = "Zob_${System.currentTimeMillis()}.mp4"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), fileName)
        streamEncoder.startRecording(file.absolutePath)

        stateManager.updateState(RecordingState.Recording(filePath = file.absolutePath))

        val recordingNotification = notificationHelper.buildRecordingNotification(
            elapsedSeconds = 0,
            resolution = "${width}x${height}"
        )
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NotificationHelper.NOTIFICATION_ID_RECORDING, recordingNotification)
    }

    private fun stopRecording() {
        val currentState = stateManager.state.value
        if (currentState is RecordingState.Idle || currentState is RecordingState.Stopping) {
            return
        }

        stateManager.updateState(RecordingState.Stopping)

        virtualDisplay?.release()
        virtualDisplay = null

        // Unregister callback then stop projection (avoid re-entrant onStop())
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        streamEncoder.stopRecording()
        audioCapturer.stopCapture()
        sceneCompositor.stop()
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        phoneStateListener = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stateManager.updateState(RecordingState.Idle)
        stopSelf()
    }

    override fun onDestroy() {
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        phoneStateListener = null
        streamEncoder.release()
        audioCapturer.stopCapture()
        sceneCompositor.stop()
        if (mediaProjection != null) {
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
            mediaProjection = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "com.zob.recorder.extra.RESULT_CODE"
        const val EXTRA_DATA = "com.zob.recorder.extra.DATA"

        fun createStartIntent(
            context: Context,
            resultCode: Int,
            data: Intent
        ): Intent {
            return Intent(context, ScreenRecorderService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
        }
    }
}
