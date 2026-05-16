# Zob Screen Recorder - Architecture Decisions

AD-1: Single module(:app) for V1. Multi-module later if needed.
AD-2: RootEncoder DisplayBase for both streaming AND local recording (not separate pipelines).
AD-3: MediaCodec + MediaMuxer pipeline, NOT MediaRecorder (full control).
AD-4: Scene compositing via OpenGL ES offscreen rendering (GPU), NOT CPU Canvas.
AD-5: Audio: pre-mix PCM from MIC + internal (AudioPlaybackCapture) → single AAC track.
AD-6: Visual scene editor: drag-and-drop with real-time preview.
AD-7: AGP 9.x built-in Kotlin (NO kotlin-android plugin).
AD-8: Type-safe Navigation Compose 2.x with kotlinx-serialization routes.
