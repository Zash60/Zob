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
