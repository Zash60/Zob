# Zob Screen Recorder - Learnings

## Project Context
- OBS-like Android screen recorder app
- Package: com.zob.recorder
- Min API 29, Target SDK 34, Compile SDK 36
- Kotlin + Compose M3 + AGP 9.1.0
- Single module (:app)
- RootEncoder via JitPack for streaming + recording
- Hilt DI, Navigation Compose 2.x type-safe routes
- Audio: PCM mix of MIC + internal (AudioPlaybackCapture)
- Scene compositing: OpenGL ES 3.0 offscreen
- Theme: Light/Dark fixed palette

## Android Resource Layout
- `AndroidManifest.xml` — declares FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, POST_NOTIFICATIONS, RECORD_AUDIO, WAKE_LOCK, INTERNET, ACCESS_NETWORK_STATE
- `strings.xml` — all user-facing strings (English) including notifications, permission rationales, recording/streaming controls
- `themes.xml` — single `Theme.Zob` extending `android:Theme.Material.Light.NoActionBar` (Compose handles actual theming; system theme for splash/startup only)
- `backup_rules.xml` — excludes `recordings/` directory from auto-backup
- Adaptive icons via `mipmap-anydpi-v26/` — foreground is a simple screen-with-record-dot vector, background is solid purple (#6750A4)
- Placeholder `.gitkeep` files in mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi} for legacy density buckets

## Key Guardrails
- NO kotlin-android plugin (AGP 9.x built-in Kotlin)
- NO Room (DataStore only)
- NO multi-module
- NO facecam, floating widget, PiP
- NO dynamic color / Monet

## Theme System (Wave 1, Task 4)
- **Color.kt** — Fixed M3 palettes in `com.zob.recorder.ui.theme`: Light (Purple 40) and Dark (Purple 80) with full semantic color tokens (primary, secondary, tertiary, error, background, surface, outline)
- **Type.kt** — Complete M3 Typography scale (display, headline, title, body, label) using `FontFamily.Default`
- **Shape.kt** — M3 Shapes scale (extraSmall=4dp through extraLarge=28dp)
- **Theme.kt** — `ZobTheme` composable wrapping `MaterialTheme` with status bar color syncing via `SideEffect`
- **ThemeViewModel.kt** — Hilt-injected ViewModel with `MutableStateFlow<ThemeMode>` (defaults to SYSTEM); persistence via SettingsRepository deferred to Wave 2
- **ThemeMode** — Already defined as enum in `com.zob.recorder.model.AppSettings.kt` with values LIGHT, DARK, SYSTEM
- NO dynamic color / Monet — fixed palettes only
- Source files placed under `app/src/main/java/com/zob/recorder/ui/theme/` (standard AGP layout, java/ root)

## CI/CD and Project Scaffold (Wave 1, Task 5)
- **`.github/workflows/ci.yml`** — CI on push/PR to main: JDK 17, assembleDebug + lint, upload APK artifact (7-day retention)
- **`.github/workflows/release.yml`** — Manual `workflow_dispatch` with version input: decodes base64 keystore from secret, builds signed release APK, uploads artifact (90-day retention)
- **`.gitignore`** — Standard Android ignores: build, .gradle, local.properties, *.apk, *.aab, *.jks, signing.properties
- **`README.md`** — Project overview with features, tech stack table, build instructions, CI badge placeholder, MIT license
- **Git** — Repo initialized on `main` branch with initial commit

## Wave 2: Infrastructure (T6-T10)
### T6 — Navigation + Screen Scaffolds
- **Routes.kt** defines 5 `@Serializable` route objects/classes (HomeRoute, SceneEditorRoute(sceneId), SettingsRoute, StreamingConfigRoute, RecordingPlaybackRoute(recordingId))
- **AppNavHost.kt** — NavHost with composable routes for all 5 destinations
- **ZobApp.kt** — Root Scaffold with BottomNavigationBar (Home, Scenes, Settings tabs) plus FloatingActionButton route to SceneEditor
- **MainActivity.kt** — @AndroidEntryPoint, edge-to-edge via WindowCompat.setDecorFitsSystemWindows, ZobTheme + ZobApp + PermissionGate wrapper
- 5 screen scaffolds (HomeScreen, SceneEditorScreen, SettingsScreen, StreamingConfigScreen, RecordingPlaybackScreen) — placeholder composables

### T7 — Hilt DI
- **AppModule.kt** — @Module @InstallIn(SingletonComponent): provides MediaProjectionManager, DataStore<Preferences>, CoroutineDispatchers(Main/IO/Default)
- **RecordingModule.kt** — @Module @InstallIn(SingletonComponent): provides recording directory File via @Named("recordingDir")
- **ZobApplication.kt** — @HiltAndroidApp, creates notification channel in onCreate()

### T8 — Permission Handling
- **PermissionManager.kt** — Request/check functions for: RECORD_AUDIO (runtime), POST_NOTIFICATIONS (API33+), MediaProjection (ActivityResultLauncher<Intent>). Check functions use ContextCompat.checkSelfPermission + MediaProjectionManager check. Uses Build.VERSION_CODES.TIRAMISU branching.
- **PermissionGate.kt** — Composable wrapper showing permission rationale UI with step-by-step flow: Audio → Notifications → Enable Recording launches ConsentIntent. Uses accompanist-permissions for runtime tracking.

### T9 — Data Repositories
- **SettingsRepository.kt** — DataStore<Preferences> backed: themeMode enum, rtmpUrl, streamKey, defaultPresetId, recordingResolution, recordingFps, recordingBitrate, hasCompletedOnboarding. Each as Flow<T> + suspend setter with singleScopePreference.
- **RecordingRepository.kt** — Queries MediaStore.Video.Media for recording list, delete via ContentResolver, File URI resolution.

### T10 — Notification Helper
- **NotificationHelper.kt** — Creates "Zob Recording" channel (IMPORTANCE_LOW). Builds: recording notification (stop, pause/resume actions), streaming notification, error notification. Notification IDs: NOTIFICATION_ID_RECORDING=1001, NOTIFICATION_ID_STREAMING=1002, NOTIFICATION_ID_ERROR=1003.

### T12 — StreamEncoder / EncoderConfig
- **EncoderConfig.kt** — Data class with resolution (1920x1080 default), fps (30), bitrate (5Mbps), codec (H264/H265), audio settings (bitrate, sampleRate, stereo, internal vs mic).
- **EncoderVideoCodec enum** — H264, H265 — maps to RootEncoder's `VideoCodec.H264`/`.H265`.
- **StreamEncoder.kt** — Wraps RootEncoder's `RtmpDisplay(context, useOpengl=true, connectChecker)`.
  - `RtmpDisplay` extends `DisplayBase` — handles VirtualDisplay→MediaCodec→RTMP/MP4 internally.
  - `useOpengl=true` creates an internal OpenGL pipeline; `glInterface.getSurface()` returns the input Surface the compositor renders into.
  - Implements `ConnectChecker` interface from `com.pedro.common`.
  - `@Inject` removed in favor of explicit `@Provides` in `RecordingModule` (per task requirement).
  - Methods: `initialize(config)`, `getInputSurface()`, `startStream(url, key)`, `startRecording(path)`, `startBoth(url, key, path)`, `stopStream()`, `stopRecording()`, `stopBoth()`, `pauseRecording()`, `resumeRecording()`, `release()`.
  - Lambda callbacks: `onConnected`, `onDisconnected`, `onConnectionStarted`, `onConnectionFailed`, `onAuthError`, `onAuthSuccess`, `onBitrate`.
  - State queries: `isStreaming`, `isRecording` (delegated to RtmpDisplay).
  - URL builder: combines rtmpUrl + "/" + streamKey.
- **RecordingModule.kt** — Added `provideStreamEncoder()` as `@Singleton @Provides`.

### T13 — AudioCapturer (MIC + Internal Audio Mixing)
- **AudioConfig.kt** — Object with constants: SAMPLE_RATE=44100, CHANNELS=1 (MONO), FORMAT=ENCODING_PCM_16BIT, BUFFER_SIZE=1764 (20ms).
- **AudioCapturer.kt** — Dual AudioRecord class: `micRecord` (MIC source) + `internalRecord` (AudioPlaybackCapture).
  - `AudioPlaybackCaptureConfiguration` with `addMatchingUsage(USAGE_MEDIA)` and `addMatchingUsage(USAGE_GAME)`.
  - Mixing coroutine on `Dispatchers.Default` reads PCM from both sources and mixes sample-by-sample with overflow clamping to `Short.MIN_VALUE..Short.MAX_VALUE`.
  - `startCapture(mediaProjection, coroutineScope)` / `stopCapture()` lifecycle.
  - Graceful fallback: if AudioPlaybackCapture fails (OEM blocking), logs warning and continues MIC-only.
  - `setStreamEncoder(StreamEncoder)` bridges mixed PCM to the encoder.
- **RootEncoder PCM injection limitation**: `RtmpDisplay`'s internal `AudioEncoder` is `protected` in `Camera2Base` — no public API for external PCM input. Added `AudioEncoderInjector` (in `StreamEncoder.kt`) that uses reflection to access the `audioEncoder` field and call `inputPCMData(Frame)`.
- **`AudioEncoderInjector`**: Caches the reflected field and method. First tries `getAudioEncoder()` public getter (future-proofing), then walks the class hierarchy for `audioEncoder` field.
- **`StreamEncoder.feedAudioPcm(buffer, size)`**: New method added to support external PCM injection from AudioCapturer.
- **RecordingModule.kt** — Added `provideAudioCapturer()` as `@Singleton @Provides`.

- LSP unavailable in this environment (kotlin-ls not installed); manual API verification against RootEncoder dokka docs instead.

### T14 — OpenGL ES Scene Compositor
- **Package**: `com.zob.recorder.compositor` — 5 files, ~1973 lines total.
- **GLCanvasUtil.kt** — `object` with static GL helpers: shader compile/link, texture creation (2D, OES, empty), Bitmap→texture upload via `GLUtils.texImage2D`, FBO creation for render-to-texture, quad VBO/EBO creation, text→Bitmap Canvas rendering. Quad vertex layout: pos.xyz + tex.st (5 floats/vertex, 4 vertices).
- **TextureRenderer.kt** — Renders OES (External) texture as full-screen quad. Uses `samplerExternalOES` with `#extension GL_OES_EGL_image_external_essl3`. SurfaceTexture transform matrix applied via `uTexMatrix` uniform.
- **OverlayRenderer.kt** — Renders 2D texture overlays at configured position/size/opacity. Model matrix converts Android Y-down coordinates to OpenGL NDC. Texture cache via `LinkedHashMap` with LRU eviction (max 32 entries), keyed by source ID.
- **TransitionRenderer.kt** — Manages CUT (instant) and FADE (cross-fade) transitions. FADE uses glBlitFramebuffer to snapshot current back buffer to FBO texture, then renders snapshot with decreasing alpha. Progress method returns 0..1 and auto-completes.
- **SceneCompositor.kt** — Main orchestrator. Dedicated GL thread (`Thread("GL-Compositor")`) with ~33ms frame loop. EGL14 init with ES 3.0 context (EGL_CONTEXT_CLIENT_VERSION=3), `EGL_RECORDABLE_ANDROID=1` for MediaCodec compatibility. Window surface created from `StreamEncoder.getInputSurface()`. Screen SurfaceTexture attached via `attachToGLContext(OES_texture_id)`.
- **Rendering pipeline** per frame:
  1. Process pending texture uploads (async from caller thread via `ConcurrentLinkedQueue`)
  2. Process pending scene transitions (lock-protected, supports first-scene, cut, fade)
  3. Update screen capture (`SurfaceTexture.updateTexImage()` + getTransformMatrix)
  4. Clear, sort sources by zOrder, render each (ScreenSource→TextureRenderer, TextSource→Canvas→texture→OverlayRenderer, ImageSource→OverlayRenderer)
  5. Fade: render snapshot with (1-progress) alpha, blending, render new scene with progress alpha
  6. Swap buffers
  7. Pace to 30fps via `Thread.sleep`
- **Fade transition** snapshot timing: render old scene → `startTransition()` blits FBO → clear → render snapshot + new scene with blend. Done in a single frame (two renders but one visible output).
- **Thread sync model**: `AtomicBoolean` for lifecycle, `synchronized(sceneLock)` for scene transitions, `ConcurrentLinkedQueue` for texture uploads/invalidations. GL thread has no Looper — uses simple `while(isRunning)` loop.
- **Min API 29 guarantees ES 3.0**: no fallback needed. Request ES 3.0 context, use `GLES30` API with `#version 300 es` shaders.
- **Integration points**: `SceneCompositor.start(encoderInputSurface, screenSurfaceTexture, width, height)` — Service must pass from its VirtualDisplay SurfaceTexture. `requestScene(scene)` for scene changes. `updateImageTexture(sourceId, bitmap)` for image overlays.
