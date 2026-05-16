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

### T15 — SceneManager
- **Package**: `com.zob.recorder.scene` — 2 files.
- **SceneManager.kt**: `@Singleton` with `@Inject constructor(@ApplicationContext)`. State via `MutableStateFlow` for scenes list, activeSceneId, isTransitioning. Derives `activeScene: StateFlow<Scene?>` via `combine(scenes, activeSceneId).stateIn()`. 
- **CRUD**: createScene, deleteScene, updateScene, reorderScenes, setActiveScene, duplicateScene. All trigger `scheduleSave()` (500ms debounce via `CoroutineScope` + `delay`).
- **Source management**: addSource, removeSource, updateSource, reorderSources — all operate on the sources list within a target scene.
- **Persistence**: JSON via `kotlinx.serialization` to `context.filesDir/scenes/scenes.json`. Writes wrapped in `SceneData(scenes, activeSceneId)`. Auto-loads on init, seeds a default scene if empty.
- **SceneDefaults.kt**: `object` with factory functions: `defaultScreenSource()`, `createDefaultScene()`, `createSceneWithText()`, `createSceneWithImage()`, `createDefaultScenes()`.
- **SceneModule.kt**: `@Module @InstallIn(SingletonComponent)` provides `@Named("scenesDir") File` for the scenes directory.
- Integration: `onTransitionComplete()` for SceneCompositor to signal done. `onCleared()` for test lifecycle.
- **Edge cases**: deleted active scene → first remaining scene becomes active; corrupted JSON → logs error, starts with defaults; setActiveScene with invalid ID → no-op.

## Wave 4: Home Screen (T16-T18)
### T16 — HomeViewModel
- **File**: `app/src/main/java/com/zob/recorder/ui/screens/home/HomeViewModel.kt`
- `@HiltViewModel` injecting `@ApplicationContext`, `RecordingStateManager`, `RecordingRepository`, `StreamEncoder`, `SettingsRepository`
- `HomeUiState` data class: recordingState, recordings list, selectedPreset, isLoading, hasPermissions, isStreaming, errorMessage
- Observes `RecordingStateManager.state` and `RecordingRepository.getRecordings()` in `init`
- Actions: `startRecording(resultCode, data)` → `ScreenRecorderService.createStartIntent()` + `ContextCompat.startForegroundService`; `stopRecording()` → ACTION_STOP intent; `startStream()` → `StreamEncoder.startStream()` with RTMP URL/key from `SettingsRepository`; `stopStream()` → `StreamEncoder.stopStream()`; `selectPreset()`; `deleteRecording(uri)`
- `PermissionManager` created inline from application context for permission checks

### T17 — RecordingSummaryCard
- **File**: `app/src/main/java/com/zob/recorder/ui/screens/home/RecordingSummaryCard.kt`
- Reusable `@Composable RecordingSummaryCard(summary, onClick, onDelete, modifier)`
- M3 `Card` with `AsyncImage` thumbnail (Coil), file name, duration icon+text, file size icon+text, date
- Overflow menu (`MoreVert` → `DropdownMenu` → Delete with error-colored icon)
- Package-level `internal` format utilities: `formatDuration(ms)`, `formatFileSize(bytes)`, `formatDate(epochMillis)`

### T18 — HomeScreen
- **File**: `app/src/main/java/com/zob/recorder/ui/screens/home/HomeScreen.kt`
- `@Composable HomeScreen(viewModel: HomeViewModel = hiltViewModel(), navController: NavController)` — matches existing `AppNavHost` call
- `rememberMediaProjectionLauncher` for screen capture consent; launches `MediaProjectionManager.createScreenCaptureIntent()` on record tap
- States: `LoadingState` (centered spinner), `PermissionWarning` (when permissions revoked), `HomeContent` (main UI)
- **TopAppBar**: "Zob" title + Settings gear icon navigating to `SettingsRoute`
- **RecordingStatusCard**: Animated visibility card showing state label (Recording/Streaming/etc) with colored dot, duration, file size
- **RecordButton**: 88dp Box with 72dp circular Surface; `rememberInfiniteTransition` pulse (scale 1.0→1.12, alpha 0.25→0.55, 900ms reverse) when recording; color animates primary↔error
- **StreamToggle**: Card with Wifi/WifiOff icon, connected status, Start/Stop `FilledTonalButton`
- **PresetChipsRow**: `FilterChip` per preset from `DEFAULT_PRESETS`
- **Recent Recordings**: `LazyColumn` with `items(recordings, key={it.id})` + `EmptyState` (large Movie icon + text) when empty
- **SnackbarHost** for error messages
- `material-icons-extended` confirmed in compose bundle → all icons available

## Wave 4: Scene Editor (AD-6, T19)
### SceneEditorScreen Implementation
- **Files created**: `SceneEditorScreen.kt`, `SceneEditorViewModel.kt`, `DraggablePreview.kt`, `SourceConfigPanel.kt`
- **Package**: `com.zob.recorder.ui.screens.sceneeditor`
- **Signature**: `SceneEditorScreen(sceneId: String, navController: NavController, viewModel: SceneEditorViewModel = hiltViewModel())`
- **ViewModel pattern**: `@HiltViewModel`, `MutableStateFlow<SceneEditorUiState>`, `uiState: StateFlow<SceneEditorUiState>`
- **UiState**: `scene: Scene?`, `selectedSourceId: String?`, `isDirty: Boolean`, `isLoading: Boolean`, `errorMessage: String?`
- **Actions**: `addTextSource()`, `addImageSource(uri)`, `removeSource(id)`, `updateSourcePosition(id, x, y)`, `updateSourceSize(id, w, h)`, `updateSourceOpacity(id, opacity)`, `updateTextSourceConfig(id, text, fontSize, color)`, `updateImageSourceConfig(id, uri, scaleType)`, `reorderSources(ids)`, `setTransition(type)`, `selectSource(id?)`
- **DraggablePreview**: Canvas-based with `pointerInput` for tap-to-select and drag-to-move. Scene coordinates (1920x1080) scaled to canvas size. Grid overlay shown during drag.
- **SourceConfigPanel**: ModalBottomSheet with dynamic content per source type. Position X/Y with steppers, Width/Height with sliders, opacity slider. TextSource: text input, font size slider, color preset picker. ImageSource: URI input, scale type selector (FIT/CROP/STRETCH). ScreenSource: info card only.
- **Source list**: LazyColumn with up/down reorder buttons, delete button, "+ Add Source" dropdown (Screen/Text/Image). Image picker via `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`.
- **Auto-save**: Delegated to SceneManager's built-in 500ms debounce via `scheduleSave()`.
- **LSP not available**: kotlin-lsp not installed; manual verification only.

## Wave 5: Streaming & Settings Screens
### StreamingConfigScreen
- **Files**: `StreamingConfigScreen.kt`, `StreamingViewModel.kt`, `QualityPresetSelector.kt`
- **Package**: `com.zob.recorder.ui.screens.streaming`
- **ViewModel pattern**: `@HiltViewModel`, `MutableStateFlow<StreamingUiState>`, `uiState: StateFlow<StreamingUiState>`
- **UiState**: `streamConfig`, `urlError`, `keyError`, `connectionStatus` (enum: DISCONNECTED/CONNECTING/CONNECTED/FAILED), `connectionErrorMessage`, `isStreaming`, `selectedPresetId`
- **Actions**: `setStreamConfig(url, key)`, `setUrl(url)`, `setStreamKey(key)`, `selectPreset(id)`, `testConnection()`, `startStreaming()`, `stopStreaming()`, `clearConnectionError()`
- **URL validation**: must start with `rtmp://` or `rtmps://`, inline error via `supportingText` on `OutlinedTextField`
- **Stream key**: password-masked with `PasswordVisualTransformation`, eye toggle via `Visibility`/`VisibilityOff` icons
- **Connection status**: colored dot indicator (CircleShape + background) with label text
- **QualityPresetSelector**: FilterChip row for Performance/Balanced/Quality presets, with `QualityPresetInfo` card showing resolution@fps and bitrate
- **Encoder callbacks**: `onConnectionStarted`, `onConnected`, `onConnectionFailed`, `onDisconnected` wired to update UiState
- **Persistence**: URL and key saved to `SettingsRepository` before test/start

### SettingsScreen
- **Files**: `SettingsScreen.kt`, `SettingsViewModel.kt`
- **Package**: `com.zob.recorder.ui.screens.settings`
- **ViewModel pattern**: `@HiltViewModel`, `MutableStateFlow<SettingsUiState>`, `uiState: StateFlow<SettingsUiState>`
- **UiState**: `themeMode`, `selectedPresetId`, `selectedCodec`, `recordingResolution`, `recordingFps`, `recordingBitrate`, `audioEnabled`, `isLoading`
- **Actions**: `setTheme(mode)`, `setPreset(id)`, `setCodec(codec)`, `setResolution(res)`, `setFps(fps)`, `setBitrate(bitrate)`, `setAudioEnabled(enabled)`
- **Recording section**: preset selector (3 buttons), codec (H.264/H.265), resolution (720p/1080p/Native), FPS (24/30/60), audio toggle (Switch)
- **Theme section**: Light/Dark/Follow System radio buttons with `selectableGroup` + `selectable` modifier
- **Storage section**: save location info card, Clear Cache button (placeholder action)
- **About section**: app version (v1.0.0), GitHub link (opens via `LocalUriHandler`), Open Source Licenses link
- **All settings persist via SettingsRepository** on each change
- **collectAsState import**: must use `import androidx.compose.runtime.collectAsState` (not auto-imported by all IDEs)

