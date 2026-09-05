# Building OpenDeSqueeze

## Android Studio

1. Install Android Studio with Android SDK Platform 36 and Build Tools 36.0.0.
2. Open the repository root.
3. Use JDK 17.
4. Build the `app` debug variant.
5. The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

The project uses Android Gradle Plugin 9.3.0. Official compatibility documentation lists Gradle 9.5.0 as the minimum for AGP 9.3, so CI pins Gradle 9.5.0.

## Command line

With Android SDK and Gradle 9.5.0 available:

```bash
bash scripts/run-host-tests.sh
bash scripts/verify-source.sh
gradle :app:assembleDebug
```

## GitHub Actions

`.github/workflows/android.yml` installs SDK 36 / Build Tools 36.0.0, runs the host tests and source checks, builds the debug APK, and uploads it as an Actions artifact.

## Device requirements

- Android 8.0 / API 26 minimum.
- Android 8-9 additionally needs the legacy write-storage permission to publish MediaStore output.
- AV1 MP4 export is only enabled on Android 14 / API 34 or newer because platform MediaMuxer support begins there.
- HEVC/AVC availability and maximum dimensions are queried from MediaCodec at runtime.
