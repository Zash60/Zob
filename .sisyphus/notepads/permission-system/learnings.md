# Permission System - Learnings

## Project State
- Early-stage project: no Gradle wrapper, SDK 29/34, Compose with Material 3
- Package: `com.zob.recorder.permission`
- Build dependencies already configured: accompanist-permissions, androidx.activity.compose, androidx.core.ktx

## Permission Architecture
- `PermissionManager` handles three permission types: RECORD_AUDIO (runtime), POST_NOTIFICATIONS (runtime, API 33+), MediaProjection (consent dialog, not runtime)
- `PermissionGate` is a Compose wrapper that gates content behind audio + notification permissions
- `rememberMediaProjectionLauncher` is a separate composable for requesting screen capture consent at recording time (not in PermissionGate since it needs activity result, not just runtime permission)
- MediaProjection is intentionally excluded from `hasAllRequiredPermissions()` since it's validated at recording start

## Key Decisions
- Min SDK 29 (Android 10), so POST_NOTIFICATIONS check needs API 33 guard
- No SYSTEM_ALERT_WINDOW or storage permissions
- Unused import `android.app.Activity` removed from PermissionManager.kt (not needed for any method)
