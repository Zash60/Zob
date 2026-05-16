# Zob — Android Screen Recorder (OBS-like)

Zob is an open-source, OBS-inspired screen recorder for Android. It brings professional-grade recording capabilities to mobile devices, including scene composition, audio mixing, and real-time streaming — all built with modern Android tooling.

## Features

- **Screen recording** — High-quality capture of device screen content
- **Scene composition** — Layer multiple sources (screen, camera, overlays) into a single scene
- **Audio mixing** — Capture and mix internal audio, microphone input, and sound effects
- **RTMP streaming** — Stream directly to platforms like Twitch, YouTube, or custom RTMP servers
- **Scene editor** — Drag-and-drop interface for arranging and customizing recording scenes
- **Light/Dark theme** — Full Material 3 theming support with dynamic color

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Build | AGP 9.1 |
| DI | Hilt (Dagger) |
| Media | RootEncoder |
| Navigation | Navigation Compose |
| Architecture | MVVM + UDF with StateFlow |

## Architecture

Zob follows **MVVM (Model-View-ViewModel)** with a **Unidirectional Data Flow (UDF)** pattern. UI state is exposed via `StateFlow` from ViewModels, and user actions flow downward through intents/events. This ensures predictable state management and testability.

## Build Instructions

```bash
git clone https://github.com/yourusername/zob.git
cd zob
./gradlew assembleDebug
```

The debug APK will be available at `app/build/outputs/apk/debug/app-debug.apk`.

## CI/CD

[![Zob CI](https://github.com/yourusername/zob/actions/workflows/ci.yml/badge.svg)](https://github.com/yourusername/zob/actions/workflows/ci.yml)

- **CI**: Triggered on push/PR to `main`. Runs `assembleDebug` and `lint`.
- **Release**: Manual workflow via GitHub Actions. Builds a signed release APK.

## License

MIT License — see [LICENSE](LICENSE) for details.
