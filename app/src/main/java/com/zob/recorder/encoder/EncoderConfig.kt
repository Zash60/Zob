package com.zob.recorder.encoder

/**
 * Configuration for the StreamEncoder wrapping RootEncoder's DisplayBase.
 *
 * @property resolutionWidth  Video width in pixels (default: 1920).
 * @property resolutionHeight Video height in pixels (default: 1080).
 * @property fps              Target frames per second (default: 30).
 * @property bitrate          Video bitrate in bits per second (default: 5 Mbps).
 * @property codec            Video codec — H264 or H265 (default: H264).
 * @property audioBitrate     Audio bitrate in bits per second (default: 128 Kbps).
 * @property audioSampleRate  Audio sample rate in Hz (default: 44100).
 * @property audioStereo      Whether to use stereo audio (default: true).
 * @property useInternalAudio Whether to capture internal device audio via
 *                            AudioPlaybackCapture (API 29+). When false the
 *                            microphone is used. Must be prepared before
 *                            calling @see StreamEncoder.startStream or
 *                            @see StreamEncoder.startRecording.
 * @property enableAudio      Whether audio encoding is enabled at all. Set to
 *                            false for video-only streaming/recording.
 */
data class EncoderConfig(
    val resolutionWidth: Int = 1920,
    val resolutionHeight: Int = 1080,
    val fps: Int = 30,
    val bitrate: Int = 5_000_000,
    val codec: EncoderVideoCodec = EncoderVideoCodec.H264,
    val audioBitrate: Int = 128_000,
    val audioSampleRate: Int = 44100,
    val audioStereo: Boolean = true,
    val useInternalAudio: Boolean = false,
    val enableAudio: Boolean = true
)

/**
 * Video codec choices exposed to UI layer and serialization.
 */
enum class EncoderVideoCodec {
    H264,
    H265
}
