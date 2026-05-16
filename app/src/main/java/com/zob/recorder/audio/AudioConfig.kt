package com.zob.recorder.audio

import android.media.AudioFormat

/**
 * Audio configuration constants for the Zob screen recorder.
 *
 * Both MIC and internal audio sources use these same settings so that PCM
 * samples can be mixed sample-by-sample without resampling.
 */
object AudioConfig {

    /** Sample rate in Hz (44100 is universally supported on API 29+). */
    const val SAMPLE_RATE: Int = 44100

    /** Number of audio channels (1 = mono). */
    const val CHANNELS: Int = 1

    /** PCM sample format (16-bit linear PCM). */
    const val FORMAT: Int = AudioFormat.ENCODING_PCM_16BIT

    /** Channel configuration for AudioRecord (mono input). */
    const val CHANNEL_CONFIG: Int = AudioFormat.CHANNEL_IN_MONO

    /** Duration of each read buffer in milliseconds. */
    const val BUFFER_DURATION_MS: Int = 20

    /**
     * Size of each PCM read buffer in bytes.
     *
     * Calculation: 44100 samples/sec × 1 channel × 2 bytes/sample × 0.020 sec = 1764 bytes
     * This gives us 882 samples per buffer at 20ms intervals (~50 reads/sec).
     */
    val BUFFER_SIZE: Int = SAMPLE_RATE * CHANNELS * 2 * BUFFER_DURATION_MS / 1000
    // = 44100 × 1 × 2 × 20 / 1000 = 1764 bytes
}
