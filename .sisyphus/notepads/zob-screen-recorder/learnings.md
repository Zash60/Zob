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
