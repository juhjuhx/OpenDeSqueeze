# OpenDeSqueeze v1 Design

## Purpose

OpenDeSqueeze is a local-only Android utility for correcting anamorphic photo and video footage directly on a phone. It is intentionally smaller than a timeline editor: select one or many media files, choose the lens squeeze factor, inspect the resulting dimensions, choose an output codec policy, and export.

## User-visible behavior

- Pick multiple photos and/or videos with Android's Storage Access Framework.
- Presets: 1.33x, 1.50x, 1.55x, 1.80x, 2.00x, plus a custom factor from 1.01x to 3.00x.
- Two geometry modes:
  - Preserve width (default): output width = input width; output height = input height / factor. This avoids creating ultra-wide resolutions above common encoder limits.
  - Preserve height: output width = input width * factor; output height = input height.
- Show source dimensions and calculated output dimensions before export when metadata is available.
- Photo output formats: JPEG (quality 80-100), PNG, WebP lossless when supported.
- Video output policy: Auto, AVC/H.264, HEVC/H.265, AV1. Auto chooses a hardware encoder that accepts the requested dimensions, preferring HEVC, then AVC. AV1 is opt-in because encoder availability is still device-dependent.
- Audio is copied without re-encoding when present.
- Jobs run serially in a foreground media-processing service so the phone can keep working while the app is backgrounded.
- Output is written through MediaStore into Pictures/OpenDeSqueeze or Movies/OpenDeSqueeze.
- The app never uploads media and requests no network permission.

## Architecture

### UI

One native `Activity` built with Android framework widgets. No Compose or AndroidX runtime dependency is required. The UI owns selection and settings only; it does not process media.

### Domain

Pure Java classes calculate output geometry, validate factors, and choose codec candidates from a normalized codec capability list. These classes are JVM-testable without Android.

### Scheduling

`ProcessingService` is a foreground service. It receives a persisted job description, processes one item at a time, publishes progress notifications, and reports completion back to the activity with local broadcasts via an explicit app callback intent. The job file lives in app-private storage and contains content URIs plus export settings.

### Photos

`PhotoProcessor` decodes one image at a time, applies a non-uniform geometry correction by resizing to the calculated dimensions, writes through MediaStore, and copies a safe subset of EXIF fields where the platform `ExifInterface` API supports them. Memory is bounded by serial processing and a dimension guard.

### Video

`VideoTranscoder` uses only Android platform APIs:

`MediaExtractor -> MediaCodec decoder -> SurfaceTexture/OpenGL ES -> MediaCodec encoder -> MediaMuxer`

The OpenGL stage renders the decoded frame into an encoder input surface whose dimensions already represent the desqueezed geometry. Rendering full-frame into that new surface performs the required non-uniform stretch without needing a complex shader.

A second extractor copies the compressed audio samples into the muxer. Rotation metadata is preserved with `MediaMuxer.setOrientationHint` when available.

### Codec policy

`CodecCapabilityScanner` inspects `MediaCodecList`. For each encoder it records MIME support, hardware/software status, maximum supported size/rate when queryable, and whether the requested dimensions are accepted.

Policy:
1. User-selected MIME, if supported.
2. Auto: hardware HEVC, hardware AVC, software HEVC, software AVC.
3. AV1 only when explicitly selected or when a later version adds an opt-in preference.
4. If the chosen codec cannot support Preserve Height dimensions, the job fails with an actionable message suggesting Preserve Width. Geometry is never silently changed.

NPU/GPU selection is not exposed because Android does not standardize a direct NPU video-encoding API. Vendor hardware encoders are reached through MediaCodec.

### HDR

v1 detects HDR transfer/color metadata and warns that the OpenGL ES path is validated for SDR 8-bit. It attempts to propagate color metadata for HEVC/AV1, but HDR preservation is marked experimental until tested on real devices with 10-bit EGL surfaces. It must never claim bit-exact HDR preservation.

## Compatibility

- minSdk: 26 (Android 8.0)
- targetSdk/compileSdk: 36
- Java 17 source toolchain for builds; runtime bytecode remains Android-compatible.
- No network permission.
- No native ABI binaries, so one APK works across arm64, armv7, x86/x86_64 as long as the Android framework supports the device.

## Open-source research and licensing

Behavior and architecture were researched from:
- `devhyper/open-video-editor` (GPL-3.0), for Media3-based editing UX patterns.
- `brarcher/video-transcoder` (GPL-3.0), for the focused transcoder product shape.
- `T8RIN/ImageToolbox` (Apache-2.0), for batch-image workflow ideas.
- `hanscappelle/anamorphic-desqueeze-android-app`, for desqueeze UX behavior only. Its repository does not expose a clear license in the inspected root, so no source code is copied from it.
- AndroidX Media3 Transformer documentation and source examples, for the MediaCodec/OpenGL transformation model.

OpenDeSqueeze v1 is a clean-room implementation using Android platform APIs. No third-party source files are copied into the app. The project is released under GPL-3.0-or-later so future contributors may safely integrate GPL-compatible code if desired.

## Acceptance criteria

1. JVM tests prove geometry math for standard and custom squeeze factors.
2. JVM tests prove codec preference order and unsupported-codec failure behavior.
3. App source includes multi-select SAF, factor presets/custom input, preserve-width/preserve-height modes, output format/codec controls, source/output dimension display, foreground processing, MediaStore export, and progress reporting.
4. Photo processor handles a batch serially and never mutates the source URI.
5. Video processor contains a complete MediaExtractor/MediaCodec/OpenGL/MediaMuxer pipeline with compressed-audio copy.
6. Manifest has no INTERNET permission.
7. CI workflow builds a debug APK using AGP 9.3.0 / Gradle 9.5.0 / Build Tools 36.0.0.
8. If an APK cannot be built in this execution environment due missing Android SDK/network access, the limitation is reported explicitly and source/CI artifacts are still packaged.
