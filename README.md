# Pary Android

Pary is an Android study companion built with Kotlin and Jetpack Compose.

## Features

- Daily overview, schedule, study records, attendance and grades screens
- Tasks and focus tools
- Platonus connection and synchronization
- Local data storage and background synchronization
- Android home-screen widgets and notifications
- Swiss Modern visual system

## Build

Requirements: JDK 17 and Gradle 8.7.

```bash
gradle testDebugUnitTest
gradle assembleDebug assembleRelease
```

APK outputs:

- `app/build/outputs/apk/debug/`
- `app/build/outputs/apk/release/`

GitHub Actions runs unit tests and builds both APK variants on pushes, pull requests and manual dispatches. Successful builds upload the debug APK as an Actions artifact. A GitHub Release is published only from `main` when all release-signing secrets are configured and the release APK passes `apksigner` verification; without signing secrets, no release is published.

## Release signing

Release signing is optional. Configure these GitHub Actions repository secrets:

- `PARY_KEYSTORE_BASE64`
- `PARY_STORE_PASSWORD`
- `PARY_KEY_ALIAS`
- `PARY_KEY_PASSWORD`

Do not commit keystores, passwords, tokens or personal credentials.

## Project structure

- `app/src/main/java/com/pary/app/data` — persistence, Platonus and app data
- `app/src/main/java/com/pary/app/ui` — Compose UI and theme
- `app/src/main/java/com/pary/app/notify` — notifications, sync and widgets
- `app/src/test` — unit tests
