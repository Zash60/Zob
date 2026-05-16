# Zob — Android Screen Recorder (OBS-like)

## TL;DR

> **Quick Summary**: Build a complete OBS-inspired screen recorder app for Android called "Zob" using Kotlin + Jetpack Compose + Material 3. Features include scene composition with drag-and-drop editor, screen capture via MediaProjection API, RTMP streaming via RootEncoder, local MP4 recording, and audio mixing (mic + internal audio). Entirely delivered via GitHub/gh CLI — no local builds.
>
> **Deliverables**:
> - Complete Android project (single module `:app`) with Gradle Kotlin DSL + version catalog
> - MediaProjection-based screen recording foreground service
> - OpenGL ES scene compositing engine (screen + text + image sources with drag-and-drop visual editor)
> - RTMP streaming via RootEncoder (DisplayBase pipeline)
> - Local MP4 recording via RootEncoder (simultaneous with streaming)
> - Audio mixer (microphone + internal audio via AudioPlaybackCapture)
> - Compose Material 3 UI (Home, Scene Editor, Streaming Config, Settings screens)
> - GitHub Actions CI/CD (build + lint + instrumented tests)
>
> **Estimated Effort**: XL (complex multi-component app)
> **Parallel Execution**: YES — 5 waves × 4-6 tasks per wave
> **Critical Path**: Task 1 (Gradle) → Task 5 (Models) → Tasks 9-12 (Engines) → Task 17 (Integration) → F1-F4 → user ok

---

## Context

### Original Request
> "Criar um app Android gravador de tela baseado no OBS. Estrutura completa em Kotlin + Jetpack Compose com Material 3. Sem build local, só pelo GitHub com gh CLI."

### Interview Summary
**Key Discussions**:
- **App name**: Zob (package: `com.zob.recorder`)
- **OBS features from V1**: Scenes (2-3 sources), screen/text/image sources, transitions (cut/fade), RTMP streaming, MP4 recording, audio mixer (mic + internal)
- **Scope OUT**: No facecam, no floating widget, no PiP, no dynamic color, no video editor, no chroma key, no Room database
- **Architecture**: Single module (`:app`), MVVM + UDF with StateFlow, Hilt DI
- **Build system**: AGP 9.1.0, Kotlin 2.3.20, Compose BOM 2026.04.01, Gradle 9.1.0+
- **Pipeline**: RootEncoder DisplayBase for both streaming AND local recording
- **UI**: Compose Material 3 (Light/Dark themes only), Navigation Compose 2.x type-safe routes
- **Scene editor**: Visual drag-and-drop with real-time preview
- **Audio**: AudioRecord (MIC) + AudioPlaybackCapture (internal audio) → PCM mix → AAC
- **Testing**: Instrumented tests via ADB (after implementation)
- **CI/CD**: GitHub Actions (build, lint, tests with APK artifact)

**Research Findings**:
- **MediaProjection API**: `createScreenCaptureIntent()` per-session consent. API 34+ requires `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission + `mediaProjection` FGS type. Token is ONE-SHOT (cannot reuse). Callback mandatory before `createVirtualDisplay()`.
- **AGP 9.x Kotlin**: Built-in Kotlin support — `kotlin-android` plugin must NOT be applied. Gradle 9.1.0+ required.
- **RootEncoder (2.7.2)**: JitPack-hosted. `DisplayBase` class handles VirtualDisplay → MediaCodec pipeline. 40+ OpenGL filters. Supports RTMP, RTSP, SRT.
- **AudioPlaybackCapture API**: Android 10+ only. Captures `USAGE_MEDIA`, `USAGE_GAME`, `USAGE_UNKNOWN`. OEMs may block. No DRM content capture.
- **OpenGL ES compositing**: Standard approach for OBS-like scene mixing. EGL context + SurfaceTexture + fragment shaders.
- **No multi-track audio in MediaMuxer**: Requires PCM pre-mixing before encoding.

### Metis Review
**Identified Gaps (addressed)**:
- AGP 9.x built-in Kotlin → Convention to NOT apply `kotlin-android` plugin anywhere
- Gradle 9.1.0+ required → Set in `gradle-wrapper.properties`
- JitPack for RootEncoder → Added to `dependencyResolutionManagement`
- Type-safe navigation → `kotlinx-serialization` plugin required
- Single module decision → No multi-module complexity for V1
- Scene editor scope → Visual drag-and-drop confirmed by user
- Process death handling → `MediaProjection.Callback.onStop()` + graceful stop
- RootEncoder scope → Used for both streaming AND recording via DisplayBase

---

## Work Objectives

### Core Objective
Build and ship a feature-complete OBS-inspired screen recorder Android app (Zob) that handles screen capture, scene composition, audio mixing, local recording, and RTMP streaming through a Compose Material 3 UI — all delivered via GitHub.

### Concrete Deliverables
- `app/` — Complete Android application module
- `.github/workflows/` — CI/CD pipeline configuration
- Published APK as GitHub Actions artifact

### Definition of Done
- App records screen at 1080p30 H.264 with audio
- Scene system with visual drag-and-drop editor works (screen + text + image sources)
- Transitions work (cut and fade between scenes)
- RTMP streaming connects and transmits video
- Local MP4 saves correctly with mixed audio
- CI/CD builds on every push

### Must Have
- Foreground service screen recording via MediaProjection
- Scene composition with OpenGL ES (screen + text + image sources)
- Visual drag-and-drop scene editor
- Audio mixer (MIC + internal audio via AudioPlaybackCapture)
- RTMP streaming via RootEncoder
- Local MP4 recording via RootEncoder
- Compose Material 3 UI (Light/Dark themes)
- Navigation Compose with type-safe routes
- GitHub Actions CI/CD

### Must NOT Have (Guardrails)
- NO `kotlin-android` plugin anywhere (AGP 9.x built-in Kotlin)
- NO facecam / camera overlay
- NO floating widget / SYSTEM_ALERT_WINDOW overlay
- NO Picture-in-Picture mode
- NO Room database (DataStore for settings)
- NO dynamic color / Monet theming
- NO multi-module architecture
- NO video editor (trim, cut, merge)
- NO chroma key / green screen effects
- NO source plugin architecture (sealed class for exactly 3 source types)
- NO AV1 encoding
- NO individual window capture
- NO multi-track audio (pre-mix PCM)
- NO boilerplate use cases for simple CRUD

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: NO (new project)
- **Automated tests**: Tests after implementation (instrumented via ADB)
- **Framework**: AndroidX Test + Compose UI Test + JUnit 4
- **QA Policy**: Every task MUST include agent-executed QA scenarios. Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

### QA Methods
- **UI/Compose**: Bash (adb + uiautomator) — launch app, interact UI, assert state via screenshots/logcat
- **System dialogs** (MediaProjection, permissions): Bash (adb + uiautomator dump + grep + input tap)
- **Recording output**: Bash (adb shell ffprobe) — verify MP4 validity, codec, resolution, duration
- **Streaming**: Bash (adb logcat + grep for RootEncoder callbacks) + mock RTMP server or connection callback validation
- **Scene composition**: Bash (adb pull screenshot/readPixels) — verify overlay elements present in rendered frame
- **Audio**: Bash (adb shell ffprobe + ffmpeg) — verify audio track(s), sample rate, channels

---

## Execution Strategy

### Parallel Execution Waves

> Maximize throughput by grouping independent tasks into parallel waves.
> Target: 4-6 tasks per wave. Fewer than 3 = under-splitting.

```
Wave 1 (Foundation — Start Immediately):
├── Task 1: Gradle project setup + version catalog + build config
├── Task 2: AndroidManifest + resources + permissions
├── Task 3: Core data models (sealed classes for scenes, sources, recording state)
├── Task 4: Material 3 theme (Light/Dark + typography + shapes)
└── Task 5: GitHub Actions CI/CD + README + repo init

Wave 2 (Infrastructure — Depends on Wave 1 models):
├── Task 6: Navigation Compose + screen scaffolds (Home, SceneEditor, Settings, StreamingConfig)
├── Task 7: Hilt DI modules (MediaProjectionManager, RootEncoder, coroutine dispatchers)
├── Task 8: Permission handler + onboarding flow (Accompanist Permissions)
├── Task 9: Settings repository (DataStore — recording presets, theme, stream config)
└── Task 10: Notification channel + recording controls notification

Wave 3 (Core Engines — MAX PARALLEL, depends on Wave 1 models):
├── Task 11: MediaProjection service (foreground, VirtualDisplay, token lifecycle, Callback.onStop)
├── Task 12: RootEncoder wrapper (DisplayBase integration, streaming + recording pipeline)
├── Task 13: Audio capture + mixer (AudioRecord MIC + AudioPlaybackCapture, PCM mix, AAC encode)
├── Task 14: OpenGL ES scene compositing engine (EGL context, SurfaceTexture, shader pipeline)
└── Task 15: Scene manager (CRUD, scene list, active scene state, source configuration)

Wave 4 (UI Screens — Depends on Wave 2+3):
├── Task 16: Home screen (recording list, start/stop, permission gates, recording state)
├── Task 17: Visual scene editor (drag-and-drop, preview, source selection + config)
├── Task 18: Streaming configuration screen (RTMP URL, stream key, presets, connect/disconnect)
├── Task 19: Settings screen (recording presets, theme toggle, codec selection, about)
└── Task 20: Recording playback + file management (MediaStore save, recording history, share)

Wave 5 (Integration + Polish — Depends on Wave 4):
├── Task 21: Recording flow integration (permission → service → scene → audio → encode/stream → stop → save)
├── Task 22: Recording lifecycle (process death, token revocation, rotation, low storage handling)
├── Task 23: Edge case handling (incoming call, device sleep, background restrictions, DRM content)
└── Task 24: GitHub Actions polish (release signing config, APK artifact, version bump)

Wave FINAL — 4 parallel reviews:
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high + skills)
└── Task F4: Scope fidelity check (deep)
→ Present results → Get explicit user okay
```

### Dependency Matrix (abbreviated)

- **W1 (1-5)**: - → W2 (6-10), W3 (11-15)
- **6**: 1,2,3 → 16-20
- **7**: 1 → 16-20
- **8**: 1,2 → 16
- **9**: 3 → 16, 18, 19
- **10**: 2 → 16, 21
- **11**: 3, 7 → 21, 22
- **12**: 3, 7 → 21, 22
- **13**: 3, 7 → 21, 22
- **14**: 3 → 17, 21
- **15**: 3 → 16, 17
- **16-20**: 6-15 → 21-24
- **21-24**: 16-20 → F1-F4
- **F1-F4**: 21-24 → user ok

### Agent Dispatch Summary

- **Wave 1 (5 tasks)**: T1-T5 → unspecified-high (build/config), quick (resources)
- **Wave 2 (5 tasks)**: T6 → visual-engineering, T7 → unspecified-high, T8 → unspecified-high, T9 → unspecified-high, T10 → unspecified-high
- **Wave 3 (5 tasks)**: T11-T13 → deep, T14 → deep (OpenGL expert needed), T15 → unspecified-high
- **Wave 4 (5 tasks)**: T16-T17 → visual-engineering, T18-T19 → visual-engineering, T20 → unspecified-high
- **Wave 5 (4 tasks)**: T21-T23 → deep, T24 → git
- **FINAL (4)**: F1 → oracle, F2 → unspecified-high, F3 → unspecified-high + playwright, F4 → deep

---

## TODOs

- [x] 1. **Gradle project setup + version catalog + build config**

  **What to do**:
  - Create complete Gradle project structure (single module `:app`)
  - Create `gradle/libs.versions.toml` with ALL dependencies:
    - AGP 9.1.0, Kotlin 2.3.20, Compose BOM 2026.04.01
    - Compose M3 1.4.0, Navigation Compose 2.9.7, Lifecycle 2.10.0
    - Hilt 2.59.2, KSP
    - RootEncoder 2.7.2 (via JitPack: `maven { url 'https://jitpack.io' }`)
    - `kotlinx-serialization-json` 1.10.0
    - Accompanist Permissions, DataStore Preferences, Coil Compose
    - Testing: JUnit 4, AndroidX Test, Compose UI Test
  - Create `settings.gradle.kts` with plugin management + dependency resolution (include JitPack)
  - Create root `build.gradle.kts` (NO `kotlin-android` plugin — AGP 9.x built-in)
  - Create `app/build.gradle.kts` applying: android application plugin, compose compiler, hilt, ksp, kotlin-serialization
  - Create `gradle/wrapper/gradle-wrapper.properties` pointing to Gradle 9.1.0+
  - Create `gradle.properties` (AndroidX, non-transitive R class, config cache)
  - Create `app/proguard-rules.pro` (Compose, Hilt, Kotlin serialization, RootEncoder rules)
  - Target SDK 34, Min SDK 29, Compile SDK 36

  **Must NOT do**:
  - NO `kotlin-android` plugin (AGP 9.x built-in Kotlin)
  - NO Groovy DSL (Kotlin DSL only)
  - NO multi-module structure
  - NO unnecessary dependencies

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: [] — pure build configuration
  - **Reason**: Complex Gradle DSL setup with AGP 9.x specifics

  **Parallelization**: Can Run In Parallel: YES | Wave 1

  **References**:
  - `libs.versions.toml` — Must follow Now in Android format with groups/bundles
  - AGP 9.1 release notes: Gradle 9.1.0+ required, built-in Kotlin
  - RootEncoder repo: `https://github.com/pedroSG94/RootEncoder` — JitPack dependency

  **Acceptance Criteria**:
  - `./gradlew projects` succeeds listing `:app`
  - `./gradlew assembleDebug` succeeds with no errors
  - No `kotlin-android` plugin found in any build file

  **QA Scenarios**:
  ```
  Scenario: Build verification
    Tool: Bash
    Preconditions: Working directory = project root, gradlew executable
    Steps:
      1. Run: ./gradlew assembleDebug
      2. Assert: Build SUCCESSFUL in output
      3. Run: ./gradlew dependencies --configuration debugRuntimeClasspath
      4. Assert: RootEncoder library listed (com.github.pedroSG94:rtmp-rtsp-stream-client-java)
    Expected Result: Gradle build completes with all dependencies resolved
    Evidence: .sisyphus/evidence/task-1-build-output.txt

  Scenario: AGP 9.x Kotlin check
    Tool: Bash
    Preconditions: Build files exist
    Steps:
      1. Run: grep -r "kotlin-android" app/build.gradle.kts build.gradle.kts || echo "NO_KOTLIN_ANDROID"
      2. Assert: Output contains "NO_KOTLIN_ANDROID" (plugin not applied)
    Expected Result: No kotlin-android plugin reference anywhere
    Evidence: .sisyphus/evidence/task-1-no-kotlin-android.txt
  ```

  **Commit**: YES
  - Message: `build: gradle project setup with version catalog and AGP 9.1`
  - Files: `gradle/`, `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `app/proguard-rules.pro`

- [x] 2. **AndroidManifest + resources + permissions strings**

  **What to do**:
  - Create `app/src/main/AndroidManifest.xml`:
    - Package: `com.zob.recorder`
    - Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `POST_NOTIFICATIONS` (API 33+), `RECORD_AUDIO`, `WAKE_LOCK`
    - `android:installLocation="internalOnly"`
    - Main activity (exported, launcher)
    - Service: `ScreenRecorderService` with `android:foregroundServiceType="mediaProjection"`
  - Create `res/values/strings.xml` with app name "Zob" and all user-facing strings
  - Create `res/values/themes.xml` material3 theme placeholder
  - Create `res/mipmap-*` placeholder icons (or use adaptive icon XML)
  - Create `res/xml/backup_rules.xml`

  **Must NOT do**:
  - NO `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE` (API 29+ scoped storage via MediaStore)
  - NO `ACCESS_BACKGROUND_LOCATION` or other unrelated permissions

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: Standard Android manifest + resources, straightforward

  **Parallelization**: Can Run In Parallel: YES | Wave 1

  **Acceptance Criteria**:
  - AndroidManifest contains all required permissions
  - `aapt dump badging app/build/outputs/apk/debug/app-debug.apk` (after build) shows correct package + permissions

  **QA Scenarios**:
  ```
  Scenario: Manifest verification
    Tool: Bash
    Preconditions: Build artifacts exist
    Steps:
      1. Run: aapt dump permissions app/build/outputs/apk/debug/app-debug.apk
      2. Assert: Output contains all required permissions
    Expected Result: FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, RECORD_AUDIO, POST_NOTIFICATIONS all present
    Evidence: .sisyphus/evidence/task-2-permissions.txt
  ```

  **Commit**: YES
  - Message: `feat: android manifest, resources, and permissions`
  - Files: `app/src/main/AndroidManifest.xml`, `app/src/main/res/`

- [x] 3. **Core data models**

  **What to do**:
  - Create `com.zob.recorder.model` package with sealed classes:
    - `Scene` (id, name, list of `Source`, transition type, sort order)
    - `Source` sealed class with exactly 3 variants:
      - `ScreenSource` (no config — full screen capture)
      - `TextSource` (text content, font size, color, position X/Y, width, height, opacity)
      - `ImageSource` (image URI/bitmap ref, position X/Y, width, height, opacity, scale type)
    - `RecordingState` sealed class: `Idle`, `Starting`, `Recording(duration, fileSize)`, `Streaming(duration, bitrate)`, `RecAndStream(duration)`, `Stopping`, `Error(message)`
    - `RecordingPreset` data class (resolution `Pair<Int,Int>`, fps, bitrate, codec `H264`/`H265`, enableAudio, enableStreaming)
    - `StreamConfig` data class (rtmpUrl, streamKey, selectedPresetId)
    - `AppSettings` data class (themeMode `Light`/`Dark`/`System`, defaultPresetId)
  - Use `@Serializable` for all data classes that go through Navigation routes
  - Add JSON serialization annotations where needed

  **Must NOT do**:
  - NO Room entities (DataStore for persistence)
  - NO interface-based source type system (sealed class only)
  - NO inheritance hierarchy for sources (sealed class variants are flat)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: Clean data modeling with Kotlin sealed classes + serialization

  **Parallelization**: Can Run In Parallel: YES | Wave 1

  **Acceptance Criteria**:
  - All model files compile
  - `@Serializable` annotations process correctly (no serialization runtime errors)

  **QA Scenarios**:
  ```
  Scenario: Model compilation verification
    Tool: Bash
    Preconditions: Build files in place
    Steps:
      1. Run: ./gradlew assembleDebug 2>&1 | tail -20
      2. Assert: BUILD SUCCESSFUL
    Expected Result: All model classes compile without errors
    Evidence: .sisyphus/evidence/task-3-model-build.txt
  ```

  **Commit**: YES (groups with T4)
  - Message: `feat: core data models for scenes, sources, and recordings`

- [x] 4. **Material 3 theme (Light/Dark + typography + shapes)**

  **What to do**:
  - Create `com.zob.recorder.ui.theme` package:
    - `Color.kt`: Define fixed color palette (brand colors, no dynamic color)
      - Light: Primary (#6750A4), Secondary, Tertiary, Background, Surface, Error, On-colors
      - Dark: Dark variants of each
    - `Type.kt`: Define type scale (Display, Headline, Title, Body, Label) using default M3 type
    - `Shape.kt`: Define shape scheme (ExtraSmall → ExtraLarge rounded)
    - `Theme.kt`: `ZobTheme` composable accepting `darkTheme: Boolean` parameter
      - Apply `MaterialTheme` with calculated color scheme (light or dark)
      - Use `isSystemInDarkTheme()` only when `darkTheme` param = `AppSettings.ThemeMode.System`
    - `ThemeViewModel.kt`: Manages current theme state via DataStore-backed `AppSettings`
  - Provide `ZobTheme` wrapper to `MainActivity`

  **Must NOT do**:
  - NO dynamic color / Monet / `dynamicColor` parameter
  - NO custom M3 color scheme beyond light/dark variants

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []
  - **Reason**: Material 3 theming with Compose, visual design decisions

  **Parallelization**: Can Run In Parallel: YES | Wave 1

  **Acceptance Criteria**:
  - `ZobTheme` renders with correct light/dark colors
  - Screenshots show correct theme application

  **QA Scenarios**:
  ```
  Scenario: Theme renders correctly
    Tool: Bash (adb)
    Preconditions: App installed on emulator
    Steps:
      1. adb shell am start -n com.zob.recorder/.MainActivity
      2. adb exec-out screencap -p > light-theme.png
      3. Assert: Screenshot shows correct light theme
    Expected Result: App renders with defined theme, no crashes
    Evidence: .sisyphus/evidence/task-4-theme-light.png
  ```

  **Commit**: YES (groups with T3)
  - Message: `feat: material 3 theme (light/dark) with typography`
  - Files: `app/src/main/java/com/zob/recorder/ui/theme/*.kt`

- [x] 5. **GitHub Actions CI/CD + README + repo init**

  **What to do**:
  - Create `.github/workflows/ci.yml`:
    - Trigger: push to main, pull_request
    - Setup: checkout JDK 17, Gradle cache
    - Steps: `./gradlew assembleDebug`, `./gradlew lint`, `./gradlew testDebugUnitTest`
    - Upload APK: `actions/upload-artifact` with `app/build/outputs/apk/debug/`
    - Save `libs.versions.toml` to CI cache key
  - Create `.github/workflows/release.yml`:
    - Manual trigger (`workflow_dispatch`)
    - Build release APK with signing (keystore from GitHub secrets)
    - Upload signed APK as artifact
  - Create `README.md` with:
    - Project name & description
    - Build instructions (clone → `./gradlew assembleDebug`)
    - Features list
    - Tech stack overview
    - License
  - Create `.gitignore` for Android project
  - Initialize git repo, create main branch, initial commit

  **Must NOT do**:
  - NO Play Store publish (out of scope)
  - NO expensive CI steps (lint takes long enough)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: [`customize-opencode`] for gh CLI operations
  - **Reason**: Standard CI/CD workflow configuration

  **Parallelization**: Can Run In Parallel: YES | Wave 1

  **Acceptance Criteria**:
  - CI workflow file is valid YAML
  - `.gitignore` excludes all build artifacts

  **QA Scenarios**:
  ```
  Scenario: CI workflow validation
    Tool: Bash
    Preconditions: GitHub Actions file created
    Steps:
      1. Run: yq eval '.name' .github/workflows/ci.yml
      2. Assert: Name field exists and is non-empty
      3. Run: grep -q "assembleDebug" .github/workflows/ci.yml
      4. Assert: Exit code 0
    Expected Result: Workflow file is valid and contains build step
    Evidence: .sisyphus/evidence/task-5-ci-validation.txt
  ```

  **Commit**: YES
  - Message: `ci: github actions workflow and project readme`
  - Files: `.github/`, `README.md`, `.gitignore`

- [x] 6. **Navigation Compose + screen scaffolds**

  **What to do**:
  - Create `com.zob.recorder.navigation` package:
    - Define type-safe route objects with `@Serializable`:
      - `HomeRoute`, `SceneEditorRoute(sceneId: String)`, `SettingsRoute`, `StreamingConfigRoute`, `RecordingPlaybackRoute(recordingId: String)`
    - Create `AppNavHost.kt` with `NavHost` and all route definitions
    - Use `NavController` for navigation (pass via scaffold-level state)
  - Create scaffold composables in `com.zob.recorder.ui.screens`:
    - `HomeScreen` — placeholder with top bar, FAB
    - `SceneEditorScreen` — placeholder with canvas area + source list
    - `SettingsScreen` — placeholder with preference list
    - `StreamingConfigScreen` — placeholder with URL/key inputs
    - `RecordingPlaybackScreen` — placeholder with video player
  - Create `ZobApp.kt` root composable with `Scaffold` + bottom navigation (Home, Scenes, Settings)
  - Wire up `MainActivity` to use `ZobApp()`

  **Must NOT do**:
  - NO Navigation 3 (use Navigation Compose 2.x)
  - NO nested nav graphs for V1

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []
  - **Reason**: Compose Navigation with type-safe routes, screen scaffolding

  **Parallelization**: Can Run In Parallel: YES | Wave 2 (blocks: 1,2,3)

  **Acceptance Criteria**:
  - App launches to Home screen
  - Bottom navigation switches between screens
  - Type-safe routes compile and deserialize correctly

  **QA Scenarios**:
  ```
  Scenario: Navigation works end-to-end
    Tool: Bash (adb + uiautomator)
    Preconditions: App installed
    Steps:
      1. adb shell am start -n com.zob.recorder/.MainActivity
      2. sleep 2
      3. adb exec-out screencap -p > nav-home.png
      4. adb shell uiautomator dump /sdcard/ui.xml && grep -q "Settings" /sdcard/ui.xml
      5. Assert: Home screen visible with navigation elements
    Expected Result: App boots to Home, bottom nav visible
    Evidence: .sisyphus/evidence/task-6-navigation.txt
  ```

  **Commit**: YES
  - Message: `feat: navigation setup with screen scaffolds`
  - Files: `app/src/main/java/com/zob/recorder/navigation/`, `ui/screens/*.kt`, `ZobApp.kt`

- [x] 7. **Hilt dependency injection modules**

  **What to do**:
  - Create `com.zob.recorder.di` package:
    - `AppModule.kt` with:
      - `@Singleton MediaProjectionManager` provider (from `@ApplicationContext`)
      - `@Singleton RootEncoder` / `DisplayBase` provider (configured for 1080p30 defaults)
      - `@Singleton CoroutineDispatchers` provider (Main, IO, Default)
      - `@Singleton DataStore<Preferences>` provider
    - `RecordingModule.kt` with:
      - `@Singleton AudioCapturer` provider (MIC + AudioPlaybackCapture)
      - `@Singleton SceneCompositor` provider (OpenGL ES engine)
      - `@Singleton RecordingStateManager` provider (shared state flow for recording state)
  - Annotate `ScreenRecorderService` with `@AndroidEntryPoint`
  - Annotate all ViewModels with `@HiltViewModel`
  - Create `ZobApplication.kt` with `@HiltAndroidApp`

  **Must NOT do**:
  - NO `@Module` without explicit `@InstallIn`
  - NO providers for things that should be created per-recording-session (VirtualDisplay, MediaCodec)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: Hilt DI wiring for service, ViewModels, and engine components

  **Parallelization**: Can Run In Parallel: YES | Wave 2 (blocks: 1)

  **Acceptance Criteria**:
  - `@HiltAndroidApp` application class compiles
  - All inject targets resolve at runtime

  **QA Scenarios**:
  ```
  Scenario: Hilt compiles and injects
    Tool: Bash
    Preconditions: Build configuration complete
    Steps:
      1. ./gradlew assembleDebug 2>&1 | tail -20
      2. Assert: BUILD SUCCESSFUL
      3. adb shell am start -n com.zob.recorder/.MainActivity
      4. Assert: No crash on startup (check logcat: adb logcat -d | grep -i "com.zob.recorder.*exception\|fatal")
    Expected Result: App boots without Hilt injection errors
    Evidence: .sisyphus/evidence/task-7-hilt-startup.txt
  ```

  **Commit**: YES
  - Message: `feat: hilt dependency injection modules`
  - Files: `app/src/main/java/com/zob/recorder/di/`, `ZobApplication.kt`

- [x] 8. **Permission handler + onboarding flow**

  **What to do**:
  - Create `com.zob.recorder.permission` package:
    - `PermissionManager.kt`:
      - Functions to request each permission:
        - `requestMediaProjection(activityResultLauncher)` — launches `createScreenCaptureIntent()`
        - `requestRecordAudio(permissionLauncher)` — standard runtime request
        - `requestPostNotifications(permissionLauncher)` — API 33+ only
      - Check functions: `hasMediaProjectionPermission()`, `hasRecordAudioPermission()`, `hasNotificationPermission()`
      - Proper ordering: MediaProjection consent must be obtained BEFORE starting foreground service (API 34+)
    - `PermissionGate.kt` composable:
      - `PermissionGate(content: @Composable () -> Unit)`:
        - If any permission missing: show explanation screen with "Grant" button
        - If all granted: show content
      - Step-by-step permission flow: Record Audio → Notifications (API33+) → MediaProjection
  - Integrate `PermissionGate` in `HomeScreen` (wrap recording controls)
  - Handle permission denial: show rationale dialog, provide settings intent shortcut

  **Must NOT do**:
  - NO `SYSTEM_ALERT_WINDOW` (excluded via guardrails)
  - NO `MANAGE_EXTERNAL_STORAGE` (using MediaStore API instead)
  - NO `READ_EXTERNAL_STORAGE` (API 29+ scoped storage)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: System-level permission flow with proper API-level branching

  **Parallelization**: Can Run In Parallel: YES | Wave 2 (blocks: 1,2)

  **Acceptance Criteria**:
  - Permission flow does not crash when user denies
  - App shows explanation screen when permission is denied
  - MediaProjection consent dialog appears when requested

  **QA Scenarios**:
  ```
  Scenario: Permission denial shows rationale
    Tool: Bash (adb)
    Preconditions: App installed
    Steps:
      1. adb shell appops set com.zob.recorder android:record_audio deny
      2. adb shell am start -n com.zob.recorder/.MainActivity
      3. sleep 2
      4. adb exec-out screencap -p > permission-denied.png
      5. Assert: Screenshot shows explanation/rationale UI (not crash)
    Expected Result: App shows permission rationale rather than crashing
    Evidence: .sisyphus/evidence/task-8-permission-denied.png
  ```

  **Commit**: YES (groups with T9, T10)
  - Message: `feat: permission handling flow with accompanist`

- [x] 9. **Settings repository (DataStore)**

  **What to do**:
  - Create `com.zob.recorder.data` package:
    - `SettingsRepository.kt`:
      - Uses `DataStore<Preferences>` to persist:
        - `themeMode`: `Light | Dark | System` (string key)
        - `defaultRecordingPresetId`: String
        - `rtmpUrl`: String (streaming server URL)
        - `streamKey`: String (store plaintext for V1 — can encrypt later)
        - `selectedCodec`: `H264 | H265`
        - `recordingResolution`: String (e.g. "1920x1080", "1280x720")
        - `recordingFps`: Int (24, 30, 60)
        - `recordingBitrate`: Int (in bps)
        - `hasCompletedOnboarding`: Boolean
      - Expose each setting as `Flow<T>` and suspend setter
      - Provide default values for all settings
      - Batch settings into logical groups (not individual DataStore keys per setting)
    - `RecordingRepository.kt`:
      - Uses app-specific directory + MediaStore to manage recording files
      - `getRecordings(): Flow<List<Recording>>` — scan MediaStore
      - `deleteRecording(uri: Uri)` — delete via ContentResolver
      - `getRecordingFileUri(fileName: String): Uri` — for playback

  **Must NOT do**:
  - NO Room database (DataStore is sufficient for settings)
  - NO encryption for stream key (V1 decision, can add later)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: DataStore repository pattern with Flow-based settings access

  **Parallelization**: Can Run In Parallel: YES | Wave 2 (blocks: 3)

  **Acceptance Criteria**:
  - Settings persist across app restarts
  - Settings default to correct values on first launch

  **QA Scenarios**:
  ```
  Scenario: Settings persist
    Tool: Bash (adb)
    Preconditions: App installed
    Steps:
      1. adb shell am start -n com.zob.recorder/.MainActivity
      2. Set theme to Dark (via UI if possible, else direct DataStore write via adb shell cmd)
      3. Force stop: adb shell am force-stop com.zob.recorder
      4. Restart: adb shell am start -n com.zob.recorder/.MainActivity
      5. sleep 2
      6. adb exec-out screencap -p > settings-persist.png
      7. Assert: Dark theme applied (verify screenshot)
    Expected Result: Theme preference persists across app restarts
    Evidence: .sisyphus/evidence/task-9-settings-persist.png
  ```

  **Commit**: YES (groups with T8, T10)

- [x] 10. **Notification channel + recording controls notification**

  **What to do**:
  - Create `com.zob.recorder.notification` package:
    - `NotificationHelper.kt`:
      - Create notification channel `recording_channel` (importance: LOW, show dots: true)
      - Build recording notification with:
        - Title: "Zob — Recording" or "Zob — Streaming" depending on mode
        - Content: elapsed time, resolution, bitrate (if streaming)
        - Actions: Stop (PendingIntent to ScreenRecorderService), Pause/Resume
        - Icon: mic/screen capture icon
        - Ongoing: true (can't swipe away)
      - Build stream-only notification variant
      - Build error notification (recording failed)
    - Call `createNotificationChannel()` on app startup (Application.onCreate)
    - Integrate with `RecordingStateManager` to update notification dynamically (MediaBrowser-style updates)

  **Must NOT do**:
  - NO notification with actions that don't have `PendingIntent.FLAG_IMMUTABLE`
  - NO custom layout notification (standard BigTextStyle is fine)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: Android notification system with dynamic updates

  **Parallelization**: Can Run In Parallel: YES | Wave 2 (blocks: 2)

  **Acceptance Criteria**:
  - Notification appears when recording starts
  - Notification updates show elapsed time
  - Stop action stops recording

  **QA Scenarios**:
  ```
  Scenario: Recording notification appears
    Tool: Bash (adb)
    Preconditions: Permissions granted via adb shell appops
    Steps:
      1. adb shell am start -n com.zob.recorder/.MainActivity
      2. Start recording (tap record button via uiautomator or adb input tap)
      3. sleep 3
      4. adb shell dumpsys notification | grep "Zob" | head -5
      5. Assert: Notification exists for package com.zob.recorder
    Expected Result: Notification "Zob — Recording" appears in notification shade
    Evidence: .sisyphus/evidence/task-10-notification.txt
  ```

  **Commit**: YES (groups with T8, T9)
  - Files: `app/src/main/java/com/zob/recorder/notification/`

- [x] 11. **MediaProjection foreground recording service**

  **What to do**:
  - Create `com.zob.recorder.service` package:
    - `ScreenRecorderService.kt`:
      - `@AndroidEntryPoint` annotated, extends `Service()`
      - Manifest: `android:foregroundServiceType="mediaProjection"`
      - `onStartCommand()`:
        - Extract `resultCode` and `data` (Intent) from intent extras (one-shot permission token)
        - Check `Build.VERSION.SDK_INT >= 34` → use `startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`
        - Get MediaProjection from `MediaProjectionManager.getMediaProjection(resultCode, data)`
        - Register `MediaProjection.Callback` BEFORE creating VirtualDisplay:
          - `onStop()` → stop recording gracefully, unregister callback, release resources
        - Create VirtualDisplay with `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR` using display metrics
        - Attach the VirtualDisplay surface to RootEncoder DisplayBase (or to the compositor)
        - Start recording/streaming pipeline
      - `stopRecording()`:
        - Stop RootEncoder
        - Release VirtualDisplay
        - Unregister MediaProjection callback
        - Stop foreground → `stopForeground(STOP_FOREGROUND_REMOVE)`
        - Stop self
      - Broadcast `RecordingState` changes to UI via `RecordingStateManager` (shared singleton)
  - Handle process death: If service restarts but MediaProjection token is null/gone → send notification "Recording interrupted" → stop gracefully
  - Handle token revocation: `MediaProjection.Callback.onStop()` fires → same as stop

  **Must NOT do**:
  - NO caching/saving the `resultData` Intent (one-shot on API 34+)
  - NO starting foreground service BEFORE obtaining MediaProjection token

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: Complex Android service lifecycle with API-level branching (29 vs 34)

  **Parallelization**: Can Run In Parallel: YES | Wave 3 (blocks: 3, 7)

  **References**:
  - MediaProjection API reference: `https://developer.android.com/media/grow/media-projection`
  - AOSP MediaProjection.java: token is one-shot on API 34+
  - Code pattern: `ScreenCaptureService` from Android media-samples

  **Acceptance Criteria**:
  - App registers foreground service during recording
  - Android 14+ FGS type check passes (no SecurityException)
  - Service survives app being removed from Recents
  - Token revocation triggers graceful stop

  **QA Scenarios**:
  ```
  Scenario: Foreground service starts correctly
    Tool: Bash (adb)
    Preconditions: Permissions granted
    Steps:
      1. adb shell am start -n com.zob.recorder/.MainActivity
      2. Start recording via UI
      3. sleep 2
      4. adb shell dumpsys activity services | grep -A 10 "ScreenRecorderService"
      5. Assert: Service is running with foreground type mediaProjection
    Expected Result: ScreenRecorderService shows as active foreground service
    Evidence: .sisyphus/evidence/task-11-service-running.txt

  Scenario: Service stops gracefully on token revocation
    Tool: Bash (adb)
    Preconditions: Recording active
    Steps:
      1. Record the PID of the recording process
      2. Simulate token revocation: adb shell am broadcast -a android.intent.action.MEDIA_PROJECTION_STOP
      3. sleep 2
      4. adb shell dumpsys activity services | grep "ScreenRecorderService"
      5. Assert: Service no longer running
    Expected Result: Service stops without crash, partial MP4 is valid
    Evidence: .sisyphus/evidence/task-11-token-revocation.txt
  ```

  **Commit**: YES
  - Message: `feat: mediaprojection foreground recording service`
  - Files: `app/src/main/java/com/zob/recorder/service/ScreenRecorderService.kt`

- [x] 12. **RootEncoder wrapper (DisplayBase integration)**

  **What to do**:
  - Create `com.zob.recorder.encoder` package:
    - `StreamEncoder.kt`:
      - Wraps RootEncoder's `DisplayBase` for screen capture
      - Initialize `DisplayBase` with:
        - Context, display metrics (1080p default)
        - RootEncoder `RtmpCamera1` or `DisplayBase` for screen input
      - Methods:
        - `startStream(rtmpUrl: String, streamKey: String)`:
          - Configure: H.264, 1080p@30fps, 5Mbps bitrate
          - Call `displayBase.startStream(rtmpUrl + streamKey)` (RTMP endpoint)
        - `startRecording(outputPath: String)`:
          - Configure same encoding settings
          - Call `displayBase.startRecord(outputPath)`
        - `startBoth(rtmpUrl, streamKey, outputPath)`:
          - Simultaneous streaming + local recording
          - RootEncoder supports dual output natively
        - `stopStream()` / `stopRecording()` / `stopBoth()`
        - `pauseRecording()` / `resumeRecording()` (RootEncoder supports this)
      - Expose stream status callbacks: `onConnected`, `onDisconnected`, `onFail(reason)`, `onBitrate(bitrate)`
      - Return `MediaCodec` input surface to the compositor (for OpenGL scene rendering pipeline)
    - `EncoderConfig.kt`:
      - Data class for RootEncoder configuration (resolution, fps, bitrate, codec, audio settings)
      - Builder pattern for creating configurations from user presets

  **Must NOT do**:
  - NO duplicating RootEncoder (use it directly, don't wrap in excessive abstraction)
  - NO custom MediaCodec pipeline (RootEncoder DisplayBase handles VirtualDisplay→MediaCodec)

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: RootEncoder API integration, DisplayBase pipeline configuration

  **References**:
  - RootEncoder repo: `https://github.com/pedroSG94/RootEncoder`
  - RootEncoder DisplayBase docs — check for `startStream`, `startRecord`, `stopStream`, callback interfaces

  **Acceptance Criteria**:
  - RootEncoder DisplayBase initializes without exception
  - Stream connects to RTMP endpoint
  - Recording produces valid MP4 file

  **QA Scenarios**:
  ```
  Scenario: DisplayBase initializes
    Tool: Bash (adb + logcat)
    Preconditions: App compiled with RootEncoder dependency
    Steps:
      1. adb shell am start -n com.zob.recorder/.MainActivity
      2. Start recording
      3. sleep 3
      4. adb logcat -d | grep -i "DisplayBase\|RootEncoder\|rtmp"
      5. Assert: No exceptions or errors in logcat
    Expected Result: DisplayBase initializes and starts encoder
    Evidence: .sisyphus/evidence/task-12-displaybase-init.txt

  Scenario: Recording produces valid MP4
    Tool: Bash (adb)
    Preconditions: Recording for 5+ seconds
    Steps:
      1. Record for 5 seconds
      2. Stop recording
      3. adb shell ls /sdcard/Android/media/com.zob.recorder/*.mp4 | head -1
      4. adb pull [mp4_path] recorded.mp4
      5. ffprobe recorded.mp4 2>&1 | grep -E "Duration|Stream"
      6. Assert: Duration > 4 seconds (close to 5), H.264 video stream present
    Expected Result: Valid MP4 file with H.264 video
    Evidence: .sisyphus/evidence/task-12-recording-probe.txt
  ```

  **Commit**: YES
  - Message: `feat: rootencoder displaybase integration for streaming and recording`
  - Files: `app/src/main/java/com/zob/recorder/encoder/`

- [x] 13. **Audio capture and mixing pipeline**

  **What to do**:
  - Create `com.zob.recorder.audio` package:
    - `AudioCapturer.kt`:
      - Manages 2 `AudioRecord` instances:
        - `micRecord`: source `MIC`, 44100Hz, MONO, PCM_16BIT
        - `internalRecord`: source `MediaRecorder.AudioSource.MEDIA_PROJECTION` via `AudioPlaybackCaptureConfiguration`
      - `AudioPlaybackCaptureConfiguration` setup:
        - Build `AudioMixingRule` with `addMatchingUsage(USAGE_MEDIA)` and `addMatchingUsage(USAGE_GAME)`
        - Create `AudioPlaybackCaptureConfiguration` with `MediaProjection` token
        - Build `AudioRecord` with `setAudioPlaybackCaptureConfig(config)`
      - Methods:
        - `startCapture()`: Start both AudioRecord instances, launch coroutine for PCM mixing
        - `stopCapture()`: Stop and release both AudioRecord instances
      - PCM mixing logic (run on `Dispatchers.Default` coroutine):
        ```
        mix(): ShortArray?
          Read PCM buffers from both AudioRecord instances
          For each sample: sum shorts, clamp to Short.MIN..Short.MAX
          Output mixed buffer to RootEncoder audio input
        ```
      - Handle `AudioPlaybackCapture` failure (OEMs blocking it) — fall back to MIC-only
    - `AudioConfig.kt`: Constants for sample rate, channels, format
    - Integrate with `RootEncoder` audio input (feed mixed PCM to RootEncoder's audio encoder)

  **Must NOT do**:
  - NO audio effects (noise gate, compressor, limiter)
  - NO resampling (both sources must use 44100)
  - NO multi-track audio output (MediaMuxer limitation — pre-mix only)

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: Complex audio pipeline, AudioPlaybackCapture API, PCM mixing algorithm

  **References**:
  - AudioPlaybackCapture: `https://developer.android.com/media/platform/av-capture`
  - PCM mixing algorithm: sample-by-sample sum with overflow clamping

  **Acceptance Criteria**:
  - Audio capture starts without exception
  - MIC audio appears in recording
  - Internal audio (media/game) appears in recording (on compatible devices)

  **QA Scenarios**:
  ```
  Scenario: MIC audio recorded
    Tool: Bash (adb)
    Preconditions: Recording active, speak into mic for 3 seconds
    Steps:
      1. Start recording with audio enabled
      2. Record 5 seconds (with audio)
      3. Stop recording
      4. adb pull [mp4_path] audio-test.mp4
      5. ffprobe audio-test.mp4 2>&1 | grep -c "Audio"
      6. Assert: >= 1 (audio track present)
      7. ffmpeg -i audio-test.mp4 -map a:0 -acodec pcm_s16le -f wav audio-test.wav -y 2>/dev/null
      8. Assert: audio-test.wav has non-zero size (audio data was captured)
    Expected Result: MP4 contains mixed audio track with MIC audio
    Evidence: .sisyphus/evidence/task-13-audio-test.txt
  ```

  **Commit**: YES
  - Message: `feat: audio capture and mixing pipeline`
  - Files: `app/src/main/java/com/zob/recorder/audio/`

- [x] 14. **OpenGL ES scene compositing engine**

  **What to do**:
  - Create `com.zob.recorder.compositor` package:
    - `SceneCompositor.kt`:
      - Manages OpenGL ES 3.0 context via `EGL14`
      - Creates `EGLContext`, `EGLSurface` (pBuffer or SurfaceTexture output)
      - Renders a scene graph to the output `Surface` (connected to RootEncoder MediaCodec input)
      - Scene graph rendering pipeline per frame:
        1. Render screen capture (`SurfaceTexture` from VirtualDisplay) as full-screen background
        2. For each source in scene (sorted by Z-order):
           - TextSource: Render text via `Canvas` → `Bitmap` → upload as GL_TEXTURE_2D → draw at configured position/size/opacity
           - ImageSource: Load image `Bitmap` → upload as GL_TEXTURE_2D → draw at configured position/size/opacity
        3. Apply transition overlay if transitioning between scenes:
           - Cut: instant swap (no overlay)
           - Fade: render previous scene + next scene with alpha blending (fade_duration_ms → 0 to 1 alpha over N frames)
    - `TextureRenderer.kt`:
      - Helper for rendering a `SurfaceTexture` (screen capture) as a full-screen quad
      - `drawFrame(surfaceTexture, transformMatrix)` — called per frame
    - `OverlayRenderer.kt`:
      - Helper for rendering text/image overlays with position, scale, opacity
    - `TransitionRenderer.kt`:
      - Manages cross-fade state between scene transitions
    - `GLCanvasUtil.kt`:
      - Bitmap → GL texture upload utility
      - Canvas drawing helper for text rendering
    - Compositing thread: dedicated GL thread running at 30fps (matching recording FPS)
      - Uses `Choreographer` or `HandlerThread` with ~33ms loop

  - **Thread synchronization model**:
    - GL thread owns EGL context
    - Service thread signals new screen frame via `SurfaceTexture.setOnFrameAvailableListener`
    - GL thread polls and renders on each vsync/interval
    - Audio thread runs independently

  **Must NOT do**:
  - NO rendering to display (offscreen rendering only — output goes to encoder)
  - NO CPU compositing (Canvas per frame is too slow at 30fps)
  - NO preview SurfaceView (not in V1 scope)

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: OpenGL ES 3.0 compositing, EGL context management, GPU rendering pipeline

  **References**:
  - Android `GLSurfaceView` pattern (even though not used directly, the GL thread pattern applies)
  - `SurfaceTexture` + OpenGL ES rendering: standard Android graphics pattern
  - OBS OpenGL shader compositing model (background → sources → transitions)

  **Acceptance Criteria**:
  - GL thread starts and renders frames without crash
  - Screen capture appears as background in output
  - Text overlay renders at configured position
  - Image overlay renders at configured position/size
  - Fade transition blends correctly

  **QA Scenarios**:
  ```
  Scenario: Scene compositor renders screen + overlay
    Tool: Bash (adb)
    Preconditions: App running, scene with text overlay configured
    Steps:
      1. Start recording with scene editor configured (screen + text "Hello Zob" at center)
      2. Record 5 seconds
      3. Stop and pull MP4
      4. ffmpeg -i [mp4] -vframes 1 frame.png -y 2>/dev/null
      5. Use image analysis to verify "Hello Zob" text appears in frame
      6. Assert: Frame contains rendered overlay text
    Expected Result: Output video shows screen capture with text overlay composited
    Evidence: .sisyphus/evidence/task-14-composited-frame.png
  ```

  **Commit**: YES
  - Message: `feat: opengl es scene compositing engine`
  - Files: `app/src/main/java/com/zob/recorder/compositor/`

- [x] 15. **Scene manager (CRUD + active scene state)**

  **What to do**:
  - Create `com.zob.recorder.scene` package:
    - `SceneManager.kt`:
      - `@Singleton` class injected via Hilt
      - State: `scenes: StateFlow<List<Scene>>`, `activeSceneId: StateFlow<String?>`, `isTransitioning: StateFlow<Boolean>`
      - CRUD operations:
        - `createScene(name: String): Scene` — creates with default ScreenSource
        - `deleteScene(id: String)` — removes scene, if active scene → set null
        - `updateScene(scene: Scene)` — replaces scene in list
        - `reorderScenes(sceneIds: List<String>)` — new order
        - `setActiveScene(id: String, transition: TransitionType?)` — sets active, triggers transition if specified
        - `duplicateScene(id: String): Scene` — deep copy with new ID
      - Source management per scene:
        - `addSource(sceneId: String, source: Source)` — adds source to scene
        - `removeSource(sceneId: String, sourceId: String)` — removes
        - `updateSource(sceneId: String, source: Source)` — updates position/size/opacity
        - `reorderSources(sceneId: String, sourceIds: List<String>)` — Z-order
    - Data persistence:
      - Save scene config as JSON file in app-specific directory (`scenes/`)
      - `kotlinx-serialization-json` for serialization
      - Auto-load on app start
      - Auto-save on any scene change (debounced 500ms)
    - `SceneDefaults.kt`:
      - Factory for default Scene setup (one scene with ScreenSource)
      - Example presets: "Full Screen", "Screen + Text", "Screen + Image"

  **Must NOT do**:
  - NO Room database
  - NO scene templates library (user creates their own)
  - NO undo/redo system (V1)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: Stateful scene management with CRUD and JSON persistence

  **Parallelization**: Can Run In Parallel: YES | Wave 3 (blocks: 3)

  **Acceptance Criteria**:
  - Scenes persist across app restarts
  - Active scene changes trigger compositor transition
  - Source add/remove works correctly

  **QA Scenarios**:
  ```
  Scenario: Scene CRUD works
    Tool: Bash (adb)
    Preconditions: App installed
    Steps:
      1. Open app, create new scene "Test Scene"
      2. Add text source "Hello" at center
      3. Switch back to home
      4. Kill app: adb shell am force-stop com.zob.recorder
      5. Reopen app
      6. Navigate to scene editor
      7. Assert: "Test Scene" exists with text source "Hello"
    Expected Result: Scene data persists across app restarts
    Evidence: .sisyphus/evidence/task-15-scene-persist.txt
  ```

  **Commit**: YES
  - Message: `feat: scene manager with crud operations`
  - Files: `app/src/main/java/com/zob/recorder/scene/`

- [x] 16. **Home screen (recording controls + history)**

  **What to do**:
  - Create `com.zob.recorder.ui.home` package:
    - `HomeScreen.kt`:
      - `@Composable HomeScreen(viewModel: HomeViewModel = hiltViewModel())`
      - UI State from ViewModel:
        - `permissionState` (all/partial/none)
        - `recordingState` (Idle/Recording/Streaming/Both)
        - `recordings: List<RecordingSummary>` (recent recordings)
        - `elapsedTime: String` (formatted duration)
        - `streamStatus: StreamStatus` (Disconnected/Connecting/Connected/Bitrate)
      - Layout:
        - TopAppBar: "Zob" title, settings icon
        - Recording status card: duration timer, stream status indicator
        - Main action: Large circular record/stop button (red pulse animation when recording)
        - Secondary: Stream toggle button
        - Quick presets: preset selector chips (Performance/Balanced/Quality)
        - Recent recordings list: thumbnails, duration, date, file size, tap to play
        - Empty state: "No recordings yet" illustration
      - States to handle:
        - Loading (permissions checking)
        - No permissions — show `PermissionGate` (reuse from Task 8)
        - Idle — show start button + presets
        - Recording — show timer + stop button + stream status
        - Error — show error card with retry
    - `HomeViewModel.kt`:
      - `@HiltViewModel class HomeViewModel`
      - Exposes `uiState: StateFlow<HomeUiState>`
      - Actions: `startRecording()`, `stopRecording()`, `startStream()`, `stopStream()`
      - Listens to `RecordingStateManager` for state changes
      - Loads recording history from `RecordingRepository`
      - Handles permission checks via `PermissionManager`
    - `RecordingSummaryCard.kt`:
      - Reusable card composable for recording history items

  **Must NOT do**:
  - NO floating action buttons that overlap with recording controls
  - NO swipe-to-delete on recording items (V1: long-press → context menu → delete)

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []
  - **Reason**: Complex Compose screen with multiple states, Material 3 theming

  **Parallelization**: Can Run In Parallel: YES | Wave 4 (blocks: 6,7,8,9,10,15)

  **Acceptance Criteria**:
  - Home screen shows recording controls
  - Start/stop button changes state correctly
  - Recent recordings list shows at least empty state

  **QA Scenarios**:
  ```
  Scenario: Home screen renders with all states
    Tool: Bash (adb + uiautomator)
    Preconditions: Permissions not yet granted (fresh install)
    Steps:
      1. adb shell am start -n com.zob.recorder/.MainActivity
      2. sleep 2
      3. adb exec-out screencap -p > home-permission-gate.png
      4. Assert: Permission gate screen shown (not crash)
      5. Grant permissions via adb shell appops
      6. Restart app
      7. sleep 2
      8. adb exec-out screencap -p > home-idle.png
      9. Assert: Record button visible
    Expected Result: Home handles both permission-denied and permission-granted states
    Evidence: .sisyphus/evidence/task-16-home-states.png
  ```

  **Commit**: YES
  - Message: `feat: home screen with recording controls and history`
  - Files: `app/src/main/java/com/zob/recorder/ui/home/`

- [x] 17. **Visual drag-and-drop scene editor**

  **What to do**:
  - Create `com.zob.recorder.ui.sceneeditor` package:
    - `SceneEditorScreen.kt`:
      - `@Composable SceneEditorScreen(sceneId: String, viewModel: SceneEditorViewModel = hiltViewModel())`
      - Layout:
        - Preview area (60% of screen): Real-time preview of the composed scene (or placeholder if not recording)
          - Uses the output of `SceneCompositor` rendered to a `TextureView` or `AndroidView` (GLSurfaceView)
          - Interactive: drag sources within preview to reposition them
          - Long-press source → context menu (edit, remove, duplicate, layer front/back)
        - Source list (30% of screen, bottom sheet):
          - Existing sources with drag-to-reorder (Z-order)
          - "+ Add Source" button → dropdown: Screen, Text, Image
          - Each source item shows type icon, name, visibility toggle
        - Source configuration (slide-up panel):
          - **TextSource**: text content field, font size slider, hex color picker, opacity slider
          - **ImageSource**: image picker (ActivityResultContracts.PickVisualMedia), scale type selector (fit/crop), opacity slider
          - **ScreenSource**: (no configuration — full screen)
          - Position: X/Y numeric fields with steppers
          - Size: Width/Height sliders or drag handles on preview
      - Interactions:
        - Pan/drag: `Modifier.pointerInput` for drag gesture → update source position
        - Pinch zoom on preview: `Modifier.pointerInput` for transform
        - Tap source in preview → select and show config panel
        - Drag handle resize corners (future, not V1)
    - `SceneEditorViewModel.kt`:
      - Exposes `uiState: StateFlow<SceneEditorUiState>` with:
        - `scene: Scene`, `selectedSourceId: String?`, `previewFrame: Bitmap?`, `isDirty: Boolean`
      - Actions: `addTextSource()`, `addImageSource(uri)`, `removeSource(id)`, `updateSourcePosition(id, x, y)`, `updateSourceConfig(id, config)`, `reorderSources(ids)`, `setTransition(type)`
      - Auto-saves changes (debounced 1s) to `SceneManager`
    - `SourceConfigPanel.kt`:
      - Bottom sheet panel for editing source properties
      - Dynamic content based on source type
    - `DraggablePreview.kt`:
      - Compose canvas with drag-to-move source overlays
      - Shows grid overlay when dragging

  **Must NOT do**:
  - NO real-time video preview in the editor (too expensive — use static frame snapshot)
  - NO animation preview (transition previews are V2)
  - NO undo/redo system
  - NO multi-select for sources

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []
  - **Reason**: Complex Compose custom interactions (drag, pinch), real-time preview, bottom sheets

  **Parallelization**: Can Run In Parallel: YES | Wave 4 (blocks: 6, 14, 15)

  **Acceptance Criteria**:
  - Scene editor shows preview area and source list
  - Drag gesture moves source in preview
  - Text source configuration updates rendered text
  - Adding/removing sources works

  **QA Scenarios**:
  ```
  Scenario: Add and configure text source
    Tool: Bash (adb + uiautomator)
    Preconditions: App open at scene editor
    Steps:
      1. Tap "+ Add Source" button
      2. Select "Text" from dropdown
      3. In config panel, enter text "Hello Zob"
      4. Set font size to 36
      5. Assert: Preview area shows "Hello Zob" at configured position
      6. adb exec-out screencap -p > scene-editor-text.png
    Expected Result: Text source appears and renders in preview
    Evidence: .sisyphus/evidence/task-17-scene-editor.png
  ```

  **Commit**: YES
  - Message: `feat: visual drag-and-drop scene editor`
  - Files: `app/src/main/java/com/zob/recorder/ui/sceneeditor/`

- [ ] 18. **Streaming configuration screen**

  **What to do**:
  - Create `com.zob.recorder.ui.streaming` package:
    - `StreamingConfigScreen.kt`:
      - `@Composable StreamingConfigScreen(viewModel: StreamingViewModel = hiltViewModel())`
      - Layout:
        - Server URL text field (with placeholder: "rtmp://live.twitch.tv/app/")
        - Stream Key text field (password-masked, with eye toggle)
        - "Test Connection" button (connect to RTMP, show success/failure)
        - Connection status indicator (Disconnected / Connecting / Connected / Failed)
        - Quality preset selector (Performance / Balanced / Quality) — maps to bitrate/resolution
        - "Start Streaming" primary button (also triggers recording if configured)
        - Info section: "Stream to Twitch, YouTube, or any RTMP server"
      - Validation:
        - URL format: must start with `rtmp://` or `rtmps://`
        - Stream key: non-empty, alphanumeric
        - Show inline errors for invalid input
    - `StreamingViewModel.kt`:
      - Exposes `uiState: StateFlow<StreamingUiState>`
      - Actions: `setStreamConfig(url, key)`, `testConnection()`, `startStreaming()`, `stopStreaming()`
      - Saves config to `SettingsRepository`
      - Uses `StreamEncoder` for actual streaming (Task 12)
    - `QualityPresetSelector.kt`:
      - Radio chip group: Performance (720p@30, 3Mbps) / Balanced (1080p@30, 5Mbps) / Quality (1080p@60, 10Mbps)

  **Must NOT do**:
  - NO stream health charts (bitrate graph, dropped frames — V2)
  - NO custom RTMP server list (user enters URL manually)
  - NO stream key encryption (V1: plaintext in DataStore)

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []
  - **Reason**: Form-based Compose screen with validation and connection status

  **Parallelization**: Can Run In Parallel: YES | Wave 4 (blocks: 6, 9, 12)

  **Acceptance Criteria**:
  - URL and stream key fields accept input
  - URL validation shows error for invalid URLs
  - Test connection button triggers connection attempt

  **QA Scenarios**:
  ```
  Scenario: Streaming config form validation
    Tool: Bash (adb + uiautomator)
    Preconditions: App installed
    Steps:
      1. Navigate to Streaming Config screen
      2. Enter invalid URL: "not-a-url"
      3. Assert: Error text displayed ("Must start with rtmp:// or rtmps://")
      4. Enter valid URL: "rtmp://live.twitch.tv/app/"
      5. Assert: Error clears
    Expected Result: Form validates URL format correctly
    Evidence: .sisyphus/evidence/task-18-stream-config.png
  ```

  **Commit**: YES (groups with T19)
  - Message: `feat: streaming configuration screen`

- [ ] 19. **Settings screen**

  **What to do**:
  - Create `com.zob.recorder.ui.settings` package:
    - `SettingsScreen.kt`:
      - `@Composable SettingsScreen(viewModel: SettingsViewModel = hiltViewModel())`
      - Recording section:
        - Default preset selector (dropdown: Performance/Balanced/Quality)
        - Codec selector (H.264 / H.265) — show note if H.265 unsupported on device
        - Resolution selector (720p, 1080p, device native)
        - FPS selector (24, 30, 60)
        - Audio toggle (enable/disable audio capture)
      - Theme section:
        - Theme toggle (Light / Dark / System) — radio buttons
      - Storage section:
        - Recording save location (info: "Saved to Zob/ in your files")
        - Clear cache button
      - About section:
        - App version (from BuildConfig)
        - Licenses (Libraries and Licenses dialog)
        - GitHub link
    - `SettingsViewModel.kt`:
      - Exposes `uiState: StateFlow<SettingsUiState>`
      - Actions: `setTheme(mode)`, `setPreset(id)`, `setCodec(codec)`, `setResolution(res)`, `setFps(fps)`
      - Persists all changes to `SettingsRepository`

  **Must NOT do**:
  - NO excessive settings (keep it focused on recording-relevant options)
  - NO setting for every possible encoding parameter

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []
  - **Reason**: Preference-style Compose screen with M3 components

  **Parallelization**: Can Run In Parallel: YES | Wave 4 (blocks: 6, 9)

  **Acceptance Criteria**:
  - Theme toggle changes app appearance immediately
  - Preset selection loaded as default in recording
  - Settings persist across restarts

  **QA Scenarios**:
  ```
  Scenario: Theme toggle works
    Tool: Bash (adb)
    Preconditions: App open
    Steps:
      1. Navigate to Settings → Theme → Dark
      2. Assert: UI immediately switches to dark theme
      3. adb exec-out screencap -p > settings-dark.png
      4. Navigate back and re-open Settings
      5. Assert: Dark theme still applied
    Expected Result: Theme toggle changes UI and persists
    Evidence: .sisyphus/evidence/task-19-settings-dark.png
  ```

  **Commit**: YES (groups with T18)
  - Message: `feat: settings screen with presets and theme toggle`

- [ ] 20. **Recording playback + file management**

  **What to do**:
  - Create `com.zob.recorder.ui.playback` package:
    - `RecordingPlaybackScreen.kt`:
      - `@Composable RecordingPlaybackScreen(recordingId: String, viewModel: PlaybackViewModel = hiltViewModel())`
      - Layout:
        - Full-screen ExoPlayer/Media3 `AndroidView` for video playback
        - Bottom controls: play/pause, seekbar, current/total time, mute toggle
        - Top bar: back arrow, file name, share button, delete (with confirmation dialog)
        - Video info panel: resolution, codec, bitrate, file size, date, duration
    - `PlaybackViewModel.kt`:
      - Exposes `uiState: StateFlow<PlaybackUiState>`
      - Actions: `play()`, `pause()`, `seekTo(position)`, `share()`, `delete()`
      - Uses `RecordingRepository` to get file URI
      - Uses Media3 ExoPlayer for video playback (full async via `PlayerView`)
    - `RecordingHistoryList.kt`:
      - Reusable list component showing thumbnails + metadata
      - Used both in Home screen and a "Library" tab
      - Long-press context menu: Play, Share, Delete, Rename

  **Must NOT do**:
  - NO video editor (trim, cut — out of scope)
  - NO transcoding or thumbnail generation (use system media store thumbnails)
  - NO delete without confirmation dialog

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: Media3/ExoPlayer integration with Compose, file management

  **Parallelization**: Can Run In Parallel: YES | Wave 4 (blocks: 6, 9)

  **Acceptance Criteria**:
  - Recorded video plays in ExoPlayer
  - Seek, pause, resume work
  - Share intent launches system share sheet
  - Delete removes file and updates list

  **QA Scenarios**:
  ```
  Scenario: Recorded video plays back
    Tool: Bash (adb)
    Preconditions: At least one recording exists
    Steps:
      1. Navigate to recording in history list
      2. Tap to open playback
      3. sleep 2
      4. adb exec-out screencap -p > playback-playing.png
      5. Assert: Playback view shows frame from video (not black/empty)
      6. Tap pause, wait 1s, tap play
      7. Observe: Video continues from pause position
    Expected Result: Video playback with play/pause/seek works
    Evidence: .sisyphus/evidence/task-20-playback.png
  ```

  **Commit**: YES
  - Message: `feat: recording playback and file management`
  - Files: `app/src/main/java/com/zob/recorder/ui/playback/`

- [ ] 21. **Recording flow integration (full pipeline)**

  **What to do**:
  - Integrate all components into a single recording flow:
    1. **Pre-flight check**: Permissions OK → storage available → no other recording active
    2. **Permission → Service start**:
       - User taps "Record" → permission gate check → launch `createScreenCaptureIntent()`
       - On consent → start `ScreenRecorderService` with `resultCode` and `data` extras
       - Service registers projection callback → creates VirtualDisplay
    3. **VirtualDisplay → Compositor → Encoder**:
       - VirtualDisplay surface goes to `SceneCompositor` (OpenGL ES)
       - Compositor renders scene (screen + overlays) to `MediaCodec` input surface
       - `RootEncoder DisplayBase` encodes video and optionally streams/records
    4. **Audio pipeline**:
       - `AudioCapturer` starts MIC + internal audio capture
       - PCM mixing coroutine feeds mixed audio to `RootEncoder` audio encoder
    5. **Recording + streaming (dual mode)**:
       - RootEncoder `startBoth(rtmpUrl, outputPath)` for simultaneous
       - Or `startRecord(outputPath)` / `startStream(rtmpUrl)` for single mode
    6. **State broadcast**:
       - `RecordingStateManager` emits state updates → UI observes via `StateFlow`
       - Notification updates with elapsed time and status
    7. **Stop flow**:
       - User taps stop OR `MediaProjection.Callback.onStop()` fires
       - Stop audio capture → Stop RootEncoder → Release VirtualDisplay → Release MediaProjection
       - Save recording metadata (duration, file path, date, scene used)
       - Insert into MediaStore for visible file
       - Update notification to "Recording saved"
    8. **Post-recording**:
       - Notification dismissed after 3 seconds (auto-dismiss)
       - Recording appears in home screen history
  - `RecordingStateManager.kt`:
    - `@Singleton` class
    - Exposes `recordingState: StateFlow<RecordingState>`
    - Methods: `updateState(newState)`, `reset()`
    - Shared singleton between Service (writes) and UI (reads)
    - Thread-safe via `MutableStateFlow` + `coroutineScope`

  **Must NOT do**:
  - NO recording without foreground service
  - NO blocking the UI thread during pipeline setup
  - NO file left behind on failed recording (clean up partial files)

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: Complex multi-component integration, state management across service ↔ UI boundaries

  **Parallelization**: Can Run In Parallel: YES | Wave 5 (blocks: 11,12,13,14,15,16)

  **Acceptance Criteria**:
  - Full recording flow: tap record → permission → service → record → stop → file saved
  - State propagates from service to UI correctly
  - No crashes during flow

  **QA Scenarios**:
  ```
  Scenario: Full recording flow end-to-end
    Tool: Bash (adb)
    Preconditions: Permissions pre-granted, one scene exists
    Steps:
      1. adb shell am start -n com.zob.recorder/.MainActivity
      2. Record UI elements coordinates to tap Record button
      3. adb shell input tap [x] [y]  (tap record button)
      4. sleep 2  (MediaProjection dialog appears)
      5. adb shell input tap [center_x] [center_y]  (tap "Start now" / allow on dialog)
      6. sleep 5  (record for 5 seconds)
      7. adb shell input tap [stop_x] [stop_y]  (tap stop)
      8. sleep 2
      9. adb shell ls /sdcard/Android/media/com.zob.recorder/*.mp4
      10. Assert: MP4 file exists
      11. ffprobe [mp4_path] 2>&1 | grep -c "Stream"
      12. Assert: At least 1 video stream
    Expected Result: Full recording flow completes, valid MP4 produced
    Evidence: .sisyphus/evidence/task-21-full-flow.txt

  Scenario: State propagates to UI
    Tool: Bash (adb + logcat)
    Preconditions: App open
    Steps:
      1. Start recording
      2. adb logcat -d | grep "RecordingState"
      3. Assert: State changes logged: Idle → Starting → Recording
      4. Stop recording
      5. Assert: State logged: Recording → Stopping → Idle
    Expected Result: Recording state flows correctly through all transitions
    Evidence: .sisyphus/evidence/task-21-state-flow.txt
  ```

  **Commit**: YES
  - Message: `feat: recording flow integration (full pipeline)`
  - Files: `app/src/main/java/com/zob/recorder/service/RecordingStateManager.kt`

- [ ] 22. **Recording lifecycle edge cases**

  **What to do**:
  - **Process death during recording**:
    - On service restart: check if MediaProjection token available (it won't be — one-shot)
    - Notify user: "Recording was interrupted. Partial file saved."
    - Save partial MP4 (RootEncoder handles this if configured correctly)
    - Clean up and transition to `Idle` state
  - **MediaProjection token revocation** (`onStop()`):
    - Already handled in Task 11 via `MediaProjection.Callback`
    - When fired: complete in-flight frame, flush encoder, close file, stop gracefully
  - **Device rotation**:
    - Lock orientation during recording OR handle `onCapturedContentResize(width, height)` callback
    - For V1: lock to portrait via manifest `android:screenOrientation="portrait"` on `MainActivity`
    - Note: user can still rotate fullscreen apps — the VirtualDisplay will trigger `onCapturedContentResize`
    - In `onCapturedContentResize`: update VirtualDisplay size, notify compositor of resolution change
  - **Low storage**:
    - Check `getFreeSpace()` on recording output directory before start
    - Warn if < 500MB free
    - During recording: check periodically (every 60s)
    - If < 50MB: auto-stop recording, show notification "Recording stopped: storage full"
  - **Incoming phone call**:
    - On `TelecomManager` callback or `PHONE_STATE` broadcast:
      - Pause recording (if RootEncoder supports it) or continue with muted audio
      - V1: continue recording (audio from call won't be captured by AudioPlaybackCapture — it's USAGE_VOICE_COMMUNICATION, excluded)
    - Resume recording when call ends
  - **Device sleep / screen off**:
    - Acquire `PARTIAL_WAKE_LOCK` during recording (via `PowerManager`)
    - Release on stop
    - Declare `WAKE_LOCK` permission in manifest
    - Note: VirtualDisplay pauses producing frames when display is off on some devices
    - On screen-off: show black frame in recording, notify user in log
  - **OEM background restrictions**:
    - On first recording: check if app is in battery optimization whitelist
    - If not: show dialog "For reliable recording, disable battery optimization for Zob"
    - Provide intent to `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (API 24+)

  **Must NOT do**:
  - NO Android 15 BOOT_COMPLETED auto-start (out of scope)
  - NO complex frame-dropping logic (V1 accepts < 5% frame drop as normal)

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: Critical-edge-case handling across many Android subsystems

  **Parallelization**: Can Run In Parallel: YES | Wave 5 (blocks: 11, 12, 13, 21)

  **Acceptance Criteria**:
  - Process death during recording produces valid partial MP4
  - Low storage warning appears before recording starts
  - Wake lock acquired during recording

  **QA Scenarios**:
  ```
  Scenario: Low storage warning
    Tool: Bash (adb)
    Preconditions: Emulator with limited storage
    Steps:
      1. adb shell df /data | tail -1  (check free space)
      2. Launch app and try to start recording
      3. If free < 500MB, assert: Warning dialog shown
      4. adb exec-out screencap -p > low-storage-warning.png
    Expected Result: App shows low storage warning before recording
    Evidence: .sisyphus/evidence/task-22-low-storage.png

  Scenario: Wake lock acquired during recording
    Tool: Bash (adb)
    Preconditions: Recording active
    Steps:
      1. adb shell dumpsys power | grep "LOCK_SCREEN_RECORDING\|WAKE_LOCK" | grep "com.zob.recorder"
      2. Assert: Wake lock held by com.zob.recorder
    Expected Result: PARTIAL_WAKE_LOCK prevents sleep during recording
    Evidence: .sisyphus/evidence/task-22-wakelock.txt
  ```

  **Commit**: YES
  - Message: `fix: recording lifecycle edge cases`
  - Files: `app/src/main/java/com/zob/recorder/service/`, `app/src/main/AndroidManifest.xml`

- [ ] 23. **Edge case handling (incoming call, background restrictions, DRM)**

  **What to do**:
  - **DRM / secure content**:
    - Detect secure surface (MediaProjection produces black frames for DRM content)
    - Log warning: "Secure content detected — black frames in recording"
    - Show in-app notification (not toast) after recording: "Some content was blocked from recording"
    - No crash, no error — just empty video segment
  - **AudioPlaybackCapture failure** (OEMs):
    - Wrap `AudioRecord.build()` in try-catch
    - On failure: fall back to MIC-only recording
    - Log: "Internal audio capture not available on this device. Recording with MIC only."
    - Show persistent info banner in app: "Internal audio unavailable"
  - **Bluetooth headset disconnect**:
    - `AudioRecord` handles source switching automatically for MIC
    - Log audio route change via `AudioManager.registerAudioDeviceCallback`
    - No special handling needed for V1
  - **Multiple displays / foldable**:
    - VirtualDisplay always targets primary display
    - Document: "Only the main screen is captured. External displays are not recorded."
    - On foldable state change: re-query display metrics
  - **Emulator testing**:
    - AudioPlaybackCapture often broken on emulator
    - Log detection: `"ro.kernel.qemu"` system property check
    - Show banner: "Some audio features may not work on emulator. Test on real device."

  **Must NOT do**:
  - NO retry logic for DRM content (can't bypass)
  - NO complicated error recovery beyond graceful fallback

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: Graceful degradation across many failure modes

  **Parallelization**: Can Run In Parallel: YES | Wave 5 (blocks: 11, 12, 13)

  **Acceptance Criteria**:
  - DRM content produces black frames but does not crash app
  - AudioPlaybackCapture failure falls back to MIC-only without crash
  - Emulator detection shows appropriate notice

  **QA Scenarios**:
  ```
  Scenario: Audio fallback on emulator
    Tool: Bash (adb)
    Preconditions: Running on emulator
    Steps:
      1. Start recording
      2. adb logcat -d | grep "AudioCapturer\|AudioPlaybackCapture"
      3. Assert: Log contains "Internal audio unavailable" or fallback message
    Expected Result: App falls back to MIC-only on emulator
    Evidence: .sisyphus/evidence/task-23-audio-fallback.txt
  ```

  **Commit**: YES (groups with T22)
  - Message: `fix: edge case handling for incoming calls, low storage, drm`

- [ ] 24. **GitHub Actions release signing + APK artifact**

  **What to do**:
  - **Release signing setup**:
    - Generate a debug keystore for development builds
    - Create `app/signing.properties` template (for local builds, not committed)
    - For CI: configure release signing via GitHub secrets:
      - `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
    - Create `app/build.gradle.kts` signing config:
      ```kotlin
      android {
          signingConfigs {
              create("release") {
                  storeFile = file(System.getenv("KEYSTORE_PATH") ?: "debug.keystore")
                  storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
                  keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
                  keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
              }
          }
          buildTypes {
              release {
                  signingConfig = signingConfigs.getByName("release")
                  isMinifyEnabled = true
                  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
              }
          }
      }
      ```
  - **CI workflow update** (from Task 5):
    - Add `release.yml` workflow:
      - Trigger: `workflow_dispatch` (manual)
      - Decode keystore from `${{ secrets.KEYSTORE_BASE64 }}`
      - Build: `./gradlew assembleRelease`
      - Sign: done by Gradle signing config
      - Upload APK as artifact with name `zob-release-${version}.apk`
    - Update `ci.yml` to also run `assembleDebug` and upload debug APK
  - **Version management**:
    - Create `version.properties` file: `major=1`, `minor=0`, `patch=0`
    - Build script reads and increments automatically
    - Version name: `1.0.0` | Version code: `1` (auto-increment)

  **Must NOT do**:
  - NO Google Play upload (out of scope)
  - NO obfuscation beyond ProGuard (R8 is fine)

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: [`customize-opencode`] for gh CLI + GitHub Actions
  - **Reason**: Standard CI/CD configuration, signing setup

  **Parallelization**: Can Run In Parallel: YES | Wave 5 (blocks: none — CI setup)

  **Acceptance Criteria**:
  - GitHub Actions workflow triggers on push
  - Debug APK is uploaded as artifact
  - Release workflow builds signed APK

  **QA Scenarios**:
  ```
  Scenario: CI workflow runs
    Tool: Bash (gh CLI)
    Preconditions: Pushed to GitHub, gh CLI authenticated
    Steps:
      1. gh run list --workflow=ci.yml --limit=1 --json status,conclusion
      2. Assert: Status is "completed" and conclusion is "success"
    Expected Result: GitHub Actions CI completes successfully
    Evidence: .sisyphus/evidence/task-24-ci-status.txt
  ```

  **Commit**: YES
  - Message: `ci: github actions release signing and apk artifact`
  - Files: `.github/workflows/`, `app/signing.properties.template`, `version.properties`

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, adb command, check APK). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in .sisyphus/evidence/. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew lint` + `./gradlew ktlintCheck` (or detekt). Review all changed files for: `as any`/`@Suppress`, empty catches, `printStackTrace()`, `Log.e` in release paths, commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction, generic names (data/result/item/temp).
  Output: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high` (+ `playwright` skill if UI)
  Start from clean state (emulator wipe). Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test cross-task integration (features working together, not isolation). Test edge cases: empty state, invalid input, rapid actions. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual git diff. Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Detect cross-task contamination: Task N touching Task M's files. Flag unaccounted changes.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

| Task(s) | Commit Message Pattern |
|---------|----------------------|
| T1 | `build: gradle project setup with version catalog and AGP 9.1` |
| T2 | `feat: android manifest, resources, and permissions` |
| T3 | `feat: core data models for scenes, sources, and recordings` |
| T4 | `feat: material 3 theme (light/dark) with typography` |
| T5 | `ci: github actions workflow and project readme` |
| T6 | `feat: navigation setup with screen scaffolds` |
| T7 | `feat: hilt dependency injection modules` |
| T8 | `feat: permission handling flow with accompanist` |
| T9 | `feat: settings repository with datastore` |
| T10 | `feat: notification channel and recording controls` |
| T11 | `feat: mediaprojection foreground recording service` |
| T12 | `feat: rootencoder displaybase integration for streaming and recording` |
| T13 | `feat: audio capture and mixing pipeline` |
| T14 | `feat: opengl es scene compositing engine` |
| T15 | `feat: scene manager with crud operations` |
| T16 | `feat: home screen with recording controls and history` |
| T17 | `feat: visual drag-and-drop scene editor` |
| T18 | `feat: streaming configuration screen` |
| T19 | `feat: settings screen with presets and theme toggle` |
| T20 | `feat: recording playback and file management` |
| T21 | `feat: recording flow integration (full pipeline)` |
| T22 | `fix: recording lifecycle edge cases` |
| T23 | `fix: edge case handling for incoming calls, low storage, etc` |
| T24 | `ci: github actions release signing and apk artifact` |

---

## Success Criteria

### Verification Commands
```bash
# Build check
./gradlew assembleDebug

# Lint
./gradlew lint

# Tests
./gradlew connectedAndroidTest

# APK artifact
ls app/build/outputs/apk/debug/app-debug.apk
```

### Final Checklist
- [ ] App builds successfully (assembleDebug)
- [ ] Screen recording produces valid MP4 with audio
- [ ] Scene composition renders correctly (screen + text + image)
- [ ] RTMP stream connects to a local or test server
- [ ] Audio mixer captures both mic and internal audio
- [ ] GitHub Actions workflow completes on every push
- [ ] APK downloadable as artifact
- [ ] All "Must Have" features present
- [ ] All "Must NOT Have" guardrails respected
- [ ] All evidence files in .sisyphus/evidence/
