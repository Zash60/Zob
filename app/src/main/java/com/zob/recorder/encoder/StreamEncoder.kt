package com.zob.recorder.encoder

import android.content.Context
import android.util.Log
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.Frame
import com.pedro.library.rtmp.RtmpDisplay
import java.lang.reflect.Method
import java.nio.ByteBuffer

/**
 * Wraps RootEncoder's [RtmpDisplay] to provide streaming AND local recording
 * through a single pipeline.
 *
 * ## Architecture
 * ```
 * Compositor (Task 14) ──render─→ getInputSurface() ──→ RtmpDisplay ──→ RTMP / MP4
 * ```
 *
 * The compositor renders the final scene graph into the Surface returned by
 * [getInputSurface]. RootEncoder reads from that Surface, encodes via
 * MediaCodec, and sends the output to RTMP (streaming) and/or MP4 (recording).
 *
 * Call [initialize] once before starting. Call [release] when done.
 */
class StreamEncoder(
    private val context: Context
) : ConnectChecker {

    // ── RootEncoder instance ──────────────────────────────────────────────

    private var rtmpDisplay: RtmpDisplay? = null
    private var config: EncoderConfig? = null
    private var prepared = false

    // ── Callback lambdas ──────────────────────────────────────────────────

    /** Invoked when the RTMP connection is successfully established. */
    var onConnected: (() -> Unit)? = null

    /** Invoked when the RTMP connection is fully terminated. */
    var onDisconnected: (() -> Unit)? = null

    /** Invoked when a connection attempt begins. */
    var onConnectionStarted: ((url: String) -> Unit)? = null

    /** Invoked when the connection fails with a human-readable reason. */
    var onConnectionFailed: ((reason: String) -> Unit)? = null

    /** Invoked when RTMP authentication fails. */
    var onAuthError: (() -> Unit)? = null

    /** Invoked when RTMP authentication succeeds. */
    var onAuthSuccess: (() -> Unit)? = null

    /** Invoked with the current upload bitrate (in bps). */
    var onBitrate: ((bitrate: Long) -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Initialises the encoder pipeline.
     *
     * Must be called once before any start method. Can be called again after
     * [release] to re-initialise.
     *
     * @return `true` if video and audio prep succeeded.
     */
    fun initialize(config: EncoderConfig): Boolean {
        stopAndRelease()

        val display = RtmpDisplay(context, useOpengl = true, this)
        rtmpDisplay = display

        // Map codec
        val rootCodec = when (config.codec) {
            EncoderVideoCodec.H264 -> VideoCodec.H264
            EncoderVideoCodec.H265 -> VideoCodec.H265
        }
        display.setVideoCodec(rootCodec)

        // Prepare video  (width, height, fps, bitrate, rotation, dpi)
        val videoOk = display.prepareVideo(
            config.resolutionWidth,
            config.resolutionHeight,
            config.fps,
            config.bitrate,
            rotation = 0,
            dpi = 1
        )
        if (!videoOk) {
            display.stopStream()
            return false
        }

        // Prepare audio (unless video-only)
        if (config.enableAudio) {
            val audioOk = if (config.useInternalAudio) {
                display.prepareInternalAudio(
                    config.audioBitrate,
                    config.audioSampleRate,
                    config.audioStereo
                )
            } else {
                display.prepareAudio(
                    config.audioBitrate,
                    config.audioSampleRate,
                    config.audioStereo,
                    echoCanceler = false,
                    noiseSuppressor = false
                )
            }
            if (!audioOk) {
                display.stopStream()
                return false
            }
        }

        this.config = config
        prepared = true
        return true
    }

    /**
     * Releases the underlying RootEncoder instance. After calling this you
     * must call [initialize] again before starting.
     */
    fun release() {
        stopAndRelease()
    }

    // ── Input surface (for compositor / Task 14) ─────────────────────────

    /**
     * Returns the [Surface] that the OpenGL compositor (Task 14) must render
     * into. Frames rendered here are encoded and sent to the active output(s).
     *
     * @throws IllegalStateException if [initialize] has not been called yet.
     */
    fun getInputSurface(): Surface {
        val display = rtmpDisplay
            ?: throw IllegalStateException("StreamEncoder not initialised — call initialize() first")
        return display.glInterface?.getSurface()
            ?: throw IllegalStateException("OpenGL interface unavailable — useOpengl must be true")
    }

    // ── Streaming ────────────────────────────────────────────────────────

    /**
     * Starts an RTMP stream.
     *
     * @param rtmpUrl   RTMP server URL, e.g. "rtmp://example.com/live".
     * @param streamKey Stream key / name to append to the URL.
     */
    fun startStream(rtmpUrl: String, streamKey: String) {
        check(prepared) { "StreamEncoder not initialised — call initialize() first" }
        val url = buildUrl(rtmpUrl, streamKey)
        rtmpDisplay?.startStream(url)
    }

    /**
     * Stops the RTMP stream (if active). Safe to call when not streaming.
     */
    fun stopStream() {
        rtmpDisplay?.stopStream()
    }

    // ── Local recording ───────────────────────────────────────────────────

    /**
     * Starts recording a local MP4 file.
     *
     * @param outputPath Absolute file path for the MP4 output.
     */
    fun startRecording(outputPath: String) {
        check(prepared) { "StreamEncoder not initialised — call initialize() first" }
        rtmpDisplay?.startRecord(outputPath)
    }

    /**
     * Stops recording (if active). Safe to call when not recording.
     */
    fun stopRecording() {
        rtmpDisplay?.stopRecord()
    }

    // ── Simultaneous ──────────────────────────────────────────────────────

    /**
     * Starts streaming AND recording simultaneously.
     *
     * @param rtmpUrl    RTMP server URL.
     * @param streamKey  Stream key / name.
     * @param outputPath Absolute file path for the MP4 output.
     */
    fun startBoth(rtmpUrl: String, streamKey: String, outputPath: String) {
        startStream(rtmpUrl, streamKey)
        startRecording(outputPath)
    }

    /**
     * Stops both streaming and recording (if either is active). Safe to call
     * when nothing is running.
     */
    fun stopBoth() {
        stopStream()
        stopRecording()
    }

    // ── Recording control ─────────────────────────────────────────────────

    /**
     * Pauses the local recording. Has no effect if not recording.
     */
    fun pauseRecording() {
        rtmpDisplay?.pauseRecord()
    }

    /**
     * Resumes a paused recording. Has no effect if not paused.
     */
    fun resumeRecording() {
        rtmpDisplay?.resumeRecord()
    }

    // ── State queries ─────────────────────────────────────────────────────

    /** Whether an RTMP stream is currently active. */
    val isStreaming: Boolean get() = rtmpDisplay?.isStreaming == true

    /** Whether a local recording is currently active. */
    val isRecording: Boolean get() = rtmpDisplay?.isRecording == true

    // ── ConnectChecker implementation ─────────────────────────────────────

    override fun onConnectionStarted(url: String) {
        onConnectionStarted?.invoke(url)
    }

    override fun onConnectionSuccess() {
        onConnected?.invoke()
    }

    override fun onConnectionFailed(reason: String) {
        onConnectionFailed?.invoke(reason)
    }

    override fun onDisconnect() {
        onDisconnected?.invoke()
    }

    override fun onAuthError() {
        onAuthError?.invoke()
    }

    override fun onAuthSuccess() {
        onAuthSuccess?.invoke()
    }

    override fun onNewBitrate(bitrate: Long) {
        onBitrate?.invoke(bitrate)
    }

    // ── Audio injection (for AudioCapturer / Task 13) ────────────────────

    /**
     * Feeds raw PCM audio data into RootEncoder's audio encoder pipeline.
     *
     * This is the integration point for [com.zob.recorder.audio.AudioCapturer].
     * Mixed PCM from MIC + internal sources is delivered here and injected
     * into the AAC encoder via [AudioEncoderInjector].
     *
     * @return `true` if the PCM buffer was accepted by the encoder.
     */
    fun feedAudioPcm(buffer: ByteBuffer, size: Int): Boolean {
        val display = rtmpDisplay
            ?: return false
        return AudioEncoderInjector.feed(display, buffer, size)
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun buildUrl(rtmpUrl: String, streamKey: String): String {
        val base = rtmpUrl.trimEnd('/')
        return if (streamKey.isBlank()) base else "$base/$streamKey"
    }

    private fun stopAndRelease() {
        rtmpDisplay?.let { display ->
            display.stopStream()
            display.stopRecord()
        }
        rtmpDisplay = null
        config = null
        prepared = false
    }
}

// ── AudioEncoderInjector ─────────────────────────────────────────────────────
//
// RootEncoder's AudioEncoder.inputPCMData(Frame) is the standard entry point
// for feeding raw PCM into the AAC encoder. However, the audioEncoder field
// lives as a protected member deep in the class hierarchy (Camera2Base →
// DisplayBase → RtmpDisplay) and is not exposed through any public getter in
// v2.7.2.
//
// This injector uses reflection to locate the field and invoke inputPCMData.
// It caches the reflected method and field for subsequent calls.

/**
 * Injects raw PCM data into RootEncoder's [com.pedro.encoder.audio.AudioEncoder]
 * via reflection.
 *
 * RootEncoder doesn't expose a public API for external PCM input, so we
 * traverse the [RtmpDisplay] class hierarchy to find the `audioEncoder`
 * field and call `inputPCMData(Frame)` on it.
 */
internal object AudioEncoderInjector {

    private var pcmInputMethod: Method? = null
    private var encoderField: java.lang.reflect.Field? = null

    /**
     * Feeds [size] bytes of PCM_16BIT mono data from [buffer] into the
     * audio encoder of [display].
     *
     * @return `true` if the data was accepted.
     */
    fun feed(display: Any, buffer: ByteBuffer, size: Int): Boolean {
        try {
            val encoder = resolveAudioEncoder(display) ?: return false
            val method = resolveInputPcmMethod(encoder) ?: return false

            // Copy PCM data into a byte array (Frame wraps byte[], not ByteBuffer)
            val bytes = ByteArray(size)
            val savedPosition = buffer.position()
            buffer.get(bytes, 0, size)
            buffer.position(savedPosition)

            val frame = Frame(bytes, 0, size, System.nanoTime() / 1000)
            method.invoke(encoder, frame)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "feed failed — RootEncoder may not support external PCM", e)
            return false
        }
    }

    // ── Reflection helpers ─────────────────────────────────────────────────

    /**
     * Locates the `audioEncoder` field by walking the display's class hierarchy.
     *
     * Strategy:
     * 1. Try public `getAudioEncoder()` method (future-proofing).
     * 2. Fall back to `getDeclaredField("audioEncoder")` on each ancestor.
     */
    private fun resolveAudioEncoder(display: Any): Any? {
        // Use cached field if available
        val cached = encoderField
        if (cached != null) return cached.get(display)

        // Try public getter
        try {
            val getter = display.javaClass.getMethod("getAudioEncoder")
            val encoder = getter.invoke(display)
            // Cache the underlying field for subsequent calls
            cacheEncoderField(display.javaClass)
            return encoder
        } catch (_: NoSuchMethodException) {
            // Fall through to field reflection
        }

        // Walk hierarchy for protected/private "audioEncoder" field
        var clazz: Class<*>? = display.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField("audioEncoder")
                field.isAccessible = true
                encoderField = field
                return field.get(display)
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        return null
    }

    /**
     * Locates the `inputPCMData(Frame)` method on the encoder object.
     */
    private fun resolveInputPcmMethod(encoder: Any): Method? {
        val cached = pcmInputMethod
        if (cached != null) return cached

        return try {
            val method = encoder.javaClass.getMethod("inputPCMData", Frame::class.java)
            pcmInputMethod = method
            method
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "inputPCMData(Frame) not found on encoder", e)
            null
        }
    }

    /**
     * Finds and caches the `audioEncoder` field for [rootClass] so that
     * subsequent lookups skip the hierarchy walk.
     */
    private fun cacheEncoderField(rootClass: Class<*>) {
        var clazz: Class<*>? = rootClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField("audioEncoder")
                field.isAccessible = true
                encoderField = field
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }

    private const val TAG = "AudioEncoderInjector"
}
