# Building OpenDeSqueeze

## Requirements

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Gradle 9.5.0
- Android Gradle Plugin 9.3.0

Minimum app runtime: Android 8.0 / API 26.

## Android Studio

1. Clone the repository.
2. Open the repository root in Android Studio.
3. Install Android SDK Platform 36 and Build Tools 36.0.0.
4. Set the Gradle JDK to JDK 17.
5. Build the `app` debug variant.

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Command line

With Android SDK/JDK/Gradle available:

```bash
bash scripts/run-host-tests.sh
bash scripts/verify-source.sh
gradle :app:assembleDebug
```

## GitHub Actions

`.github/workflows/android.yml` performs:

```text
checkout
  ↓
JDK 17
  ↓
Android SDK 36
  ↓
Gradle 9.5
  ↓
host tests
  ↓
source checks
  ↓
:app:assembleDebug
  ↓
OpenDeSqueeze-debug-apk artifact
```

The CI artifact is a development/debug build. A persistent release key and formal release workflow should be added before treating CI output as a stable upgradable release channel.

## Runtime notes

- Android 8–9 use legacy write permission for MediaStore publication.
- AV1 MP4 export is limited to Android versions where the platform muxer supports it.
- HEVC/AVC availability and maximum output dimensions are queried at runtime.
- A codec capability advertisement is not trusted as final; configure/start is still allowed to fail and trigger fallback.
