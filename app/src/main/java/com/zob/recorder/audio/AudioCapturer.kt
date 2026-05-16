package com.zob.recorder.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.Log
import com.zob.recorder.encoder.StreamEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Callback invoked on [Dispatchers.Default] with each mixed PCM buffer.
 *
 * The buffer is a direct ByteBuffer in native byte order, positioned at 0
 * with [remaining] bytes of PCM_16BIT mono data set. The buffer is **not**
 * valid after the callback returns — copy data if you need it later.
 */
fun interface AudioCaptureCallback {
    fun onMixedPcm(buffer: ByteBuffer, size: Int)
}

/**
 * Captures microphone audio AND internal device audio (AudioPlaybackCapture),
 * mixes the two PCM streams sample-by-sample with overflow clamping, and
 * delivers the result to a callback and/or [StreamEncoder].
 *
 * ## Architecture
 * ```
 * MIC AudioRecord ──→ PCM buffer ──┐
 *                                   ├── mixPcm() ──→ mixed PCM → callback / encoder
 * Internal AudioRecord ──→ PCM ────┘
 *         ↑
 *   AudioPlaybackCaptureConfiguration
 *         ↑
 *   MediaProjection token (from ScreenRecorderService)
 * ```
 *
 * ## Fallback behaviour
 * If [AudioPlaybackCapture] is unavailable (OEM restriction, DRM content, or
 * lower API level) the capturer logs a warning and continues with MIC-only
 * capture. No audio is lost — the downstream encoder still receives data.
 *
 * ## Thread safety
 * All AudioRecord I/O and PCM mixing runs on [Dispatchers.Default]. The
 * callback is invoked from that same coroutine context.
 */
class AudioCapturer {

    // ── AudioRecord instances ──────────────────────────────────────────────

    private var micRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null

    // ── Coroutine lifecycle ────────────────────────────────────────────────

    private var captureJob: Job? = null
    private var captureScope: CoroutineScope? = null

    // ── Public state ───────────────────────────────────────────────────────

    /**
     * `true` once [startCapture] successfully created the internal audio
     * (AudioPlaybackCapture) record. When `false` the capturer is in MIC-only
     * fallback mode.
     */
    @get:JvmName("isInternalAudioAvailable")
    val internalAudioAvailable: Boolean
        get() = internalRecord != null

    /**
     * Optional callback for mixed PCM data. Called on [Dispatchers.Default].
     */
    var callback: AudioCaptureCallback? = null

    // ── Encoder integration ────────────────────────────────────────────────

    private var encoder: StreamEncoder? = null

    /**
     * Connects this capturer to a [StreamEncoder] so that mixed PCM is
     * automatically forwarded to RootEncoder's audio encoder.
     *
     * Calling this also sets [callback], which is used as the bridge. If you
     * need your own callback, call [setCallback] after this method.
     */
    fun setStreamEncoder(streamEncoder: StreamEncoder) {
        encoder = streamEncoder
        callback = AudioCaptureCallback { buffer, size ->
            streamEncoder.feedAudioPcm(buffer, size)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Starts capturing from both MIC and internal (AudioPlaybackCapture)
     * sources.
     *
     * If AudioPlaybackCapture cannot be set up the capturer falls back to
     * MIC-only capture and logs a warning.
     *
     * @param mediaProjection A valid [MediaProjection] obtained from
     *                        [MediaProjectionManager.getMediaProjection].
     *                        Required for AudioPlaybackCapture.
     * @param coroutineScope  Scope in which the capture/mix coroutine runs.
     *                        Typically a lifecycle-aware scope.
     */
    fun startCapture(mediaProjection: MediaProjection, coroutineScope: CoroutineScope) {
        stopCapture()

        captureScope = coroutineScope

        // 1. Create MIC AudioRecord
        val micBufferSize = computeBufferSize()
        micRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConfig.SAMPLE_RATE,
                AudioConfig.CHANNEL_CONFIG,
                AudioConfig.FORMAT,
                micBufferSize
            ).also { it.startRecording() }
        } catch (e: SecurityException) {
            Log.e(TAG, "MIC permission denied", e)
            return
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MIC AudioRecord", e)
            return
        }

        if (micRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "MIC AudioRecord failed to start recording")
            micRecord?.release()
            micRecord = null
            return
        }

        // 2. Create internal AudioRecord via AudioPlaybackCapture (with fallback)
        val internalBufferSize = computeBufferSize()
        try {
            internalRecord = createInternalAudioRecord(mediaProjection, internalBufferSize)
            internalRecord?.startRecording()
            if (internalRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                internalRecord?.release()
                internalRecord = null
                Log.w(TAG, "Internal AudioRecord failed to start — MIC only")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioPlaybackCapture unavailable (${e.message}) — MIC only")
            internalRecord = null
        }

        // 3. Launch mixing coroutine
        captureJob = coroutineScope.launch(Dispatchers.Default) {
            captureAndMix()
        }
    }

    /**
     * Stops audio capture, releases both AudioRecord instances, and cancels
     * the mixing coroutine. Safe to call when not capturing.
     */
    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        captureScope = null

        releaseRecord(micRecord)
        micRecord = null

        releaseRecord(internalRecord)
        internalRecord = null
    }

    // ── Internal: PCM capture and mixing ───────────────────────────────────

    /**
     * Reads PCM buffers from both AudioRecord sources, mixes them
     * sample-by-sample with overflow clamping, and delivers the result.
     */
    @SuppressLint("MissingPermission")  // permission checked upstream
    private suspend fun captureAndMix() {
        val bufSize = AudioConfig.BUFFER_SIZE
        val micBuf = ByteBuffer.allocateDirect(bufSize).order(ByteOrder.nativeOrder())
        val intBuf = ByteBuffer.allocateDirect(bufSize).order(ByteOrder.nativeOrder())

        while (isActive) {
            // Read from MIC
            micBuf.clear()
            val micBytes = readSafely(micRecord, micBuf, bufSize)
            if (micBytes <= 0) {
                delay(1)  // yield to avoid busy-spin
                continue
            }
            micBuf.limit(micBytes)

            // Read from internal audio
            if (internalRecord != null) {
                intBuf.clear()
                val internalBytes = readSafely(internalRecord, intBuf, bufSize)
                if (internalBytes > 0) {
                    intBuf.limit(internalBytes)
                    mixPcm(micBuf, intBuf)
                }
            }

            // Deliver mixed PCM
            micBuf.flip()
            val size = micBuf.remaining()
            if (size > 0) {
                callback?.onMixedPcm(micBuf, size)
            }
        }
    }

    /**
     * Mixes [buf2] into [buf1] sample-by-sample with overflow clamping.
     *
     * After this call [buf1] contains the mixed result. Both buffers are
     * assumed to be PCM_16BIT mono in native byte order. The shorter buffer
     * determines the number of mixed samples.
     *
     * Overflow clamp: `S + T` clamped to `[Short.MIN_VALUE .. Short.MAX_VALUE]`
     */
    private fun mixPcm(buf1: ByteBuffer, buf2: ByteBuffer) {
        val short1 = buf1.asShortBuffer()
        val short2 = buf2.asShortBuffer()
        val count = minOf(short1.remaining(), short2.remaining())

        val mixed = ShortArray(count)
        for (i in 0 until count) {
            val sum = short1[i].toInt() + short2[i].toInt()
            mixed[i] = sum.coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            ).toShort()
        }

        buf1.position(0)
        buf1.asShortBuffer().put(mixed)
        buf1.limit(count * 2)
    }

    // ── Internal: AudioRecord helpers ──────────────────────────────────────

    /**
     * Creates an [AudioRecord] configured for AudioPlaybackCapture.
     *
     * @throws UnsupportedOperationException if the platform doesn't support
     *         AudioPlaybackCapture or the OEM has blocked it.
     * @throws SecurityException if the caller doesn't have the required
     *         permission or the MediaProjection token is invalid.
     */
    private fun createInternalAudioRecord(
        mediaProjection: MediaProjection,
        bufferSize: Int
    ): AudioRecord {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioConfig.FORMAT)
            .setSampleRate(AudioConfig.SAMPLE_RATE)
            .setChannelMask(AudioConfig.CHANNEL_CONFIG)
            .build()

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    /**
     * Returns the larger of [AudioRecord.getMinBufferSize] and our desired
     * read size, ensuring the underlying audio buffer is large enough.
     */
    private fun computeBufferSize(): Int {
        val minSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.FORMAT
        )
        return maxOf(minSize, AudioConfig.BUFFER_SIZE)
    }

    /**
     * Reads PCM data from [record] into [buffer]. Returns the number of bytes
     * read, or -1 on error.
     */
    private fun readSafely(record: AudioRecord?, buffer: ByteBuffer, size: Int): Int {
        if (record == null) return -1
        return try {
            record.read(buffer, size)
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord read error", e)
            -1
        }
    }

    /** Releases an [AudioRecord] and swallows any exception. */
    private fun releaseRecord(record: AudioRecord?) {
        record?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
            } catch (_: Exception) { }
            try {
                it.release()
            } catch (_: Exception) { }
        }
    }

    companion object {
        private const val TAG = "AudioCapturer"
    }
}
