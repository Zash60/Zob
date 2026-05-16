package com.zob.recorder.encoder

import android.content.Context
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.rtmp.RtmpDisplay

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
