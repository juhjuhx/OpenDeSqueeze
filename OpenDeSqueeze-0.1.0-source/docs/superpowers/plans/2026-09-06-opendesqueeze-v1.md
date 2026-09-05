# OpenDeSqueeze v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a small native Android app that batch-desqueezes anamorphic photos and videos locally on-device.

**Architecture:** Native framework-only Java app. Pure-Java domain logic is tested on the host JVM; Android media work uses SAF, MediaStore, foreground service, Bitmap APIs, MediaExtractor/MediaCodec/OpenGL ES/MediaMuxer.

**Tech Stack:** Java 17, Android framework API 26-36, Gradle 9.5.0, Android Gradle Plugin 9.3.0, MediaCodec, OpenGL ES 2.0, MediaMuxer.

**Spec:** `docs/superpowers/specs/2026-09-06-opendesqueeze-design.md`

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36.
- No INTERNET permission and no cloud processing.
- No runtime third-party libraries.
- Preserve Width is the default geometry policy.
- Geometry must never silently change to satisfy an encoder.
- AV1 is explicit opt-in in v1.
- HDR preservation is experimental and must be labeled as such.

---

### Task 1: Domain geometry and codec policy

**Files:**
- Create: `app/src/main/java/io/opendesqueeze/core/DesqueezeMath.java`
- Create: `app/src/main/java/io/opendesqueeze/core/CodecPolicy.java`
- Create: `host-tests/io/opendesqueeze/core/DesqueezeMathTest.java`
- Create: `host-tests/io/opendesqueeze/core/CodecPolicyTest.java`
- Create: `scripts/run-host-tests.sh`

**Interfaces:**
- Produces: `DesqueezeMath.calculate(int width, int height, double factor, Mode mode)` returning immutable `Size`.
- Produces: `CodecPolicy.choose(String requestedMime, List<Candidate> candidates, int width, int height)` returning `Candidate` or throwing `IllegalArgumentException`.

- [ ] Write failing geometry tests for 3840x2160 at 1.33x in both geometry modes, custom 1.55x, even-dimension rounding, and invalid factors.
- [ ] Run `bash scripts/run-host-tests.sh`; confirm missing production classes fail compilation.
- [ ] Implement `DesqueezeMath` minimally and rerun tests.
- [ ] Write failing codec tests for Auto hardware-HEVC preference, AVC fallback, explicit AV1, unsupported dimensions, and software fallback.
- [ ] Run tests and confirm codec tests fail for missing implementation.
- [ ] Implement `CodecPolicy` minimally and rerun all host tests.

### Task 2: Android project shell and picker UI

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/io/opendesqueeze/MainActivity.java`
- Create: `app/src/main/java/io/opendesqueeze/model/ExportSettings.java`
- Create: `app/src/main/java/io/opendesqueeze/model/SelectedMedia.java`
- Create: `app/src/main/java/io/opendesqueeze/media/MediaInspector.java`
- Create: `app/src/main/res/values/strings.xml`, `colors.xml`, `styles.xml`

**Interfaces:**
- Consumes: `DesqueezeMath`.
- Produces: serializable job settings and selected content URI list.

- [ ] Add Android build files for AGP 9.3.0, Java 17, minSdk 26, compileSdk/targetSdk 36.
- [ ] Add manifest with MainActivity and media-processing foreground service declaration but no INTERNET permission.
- [ ] Implement SAF multi-select and persisted URI grants.
- [ ] Implement native-widget UI for factor presets/custom factor, geometry mode, photo format/quality, video codec/bitrate, and selected-media list.
- [ ] Implement metadata inspection and source/output dimension preview.
- [ ] Add source-level manifest/structure checks to `scripts/verify-source.sh`.

### Task 3: Batch photo processing

**Files:**
- Create: `app/src/main/java/io/opendesqueeze/media/PhotoProcessor.java`
- Create: `app/src/main/java/io/opendesqueeze/media/MediaStoreWriter.java`

**Interfaces:**
- Consumes: content `Uri`, `ExportSettings`, `DesqueezeMath.Size`.
- Produces: output `Uri` in Pictures/OpenDeSqueeze.

- [ ] Add host-testable filename/format selection helpers and failing tests.
- [ ] Implement one-at-a-time Bitmap decoding, desqueeze resize, JPEG/PNG/WebP export, and memory guards.
- [ ] Implement MediaStore pending-row lifecycle and cleanup on failure.
- [ ] Implement best-effort EXIF copy for orientation-normalized output and common camera/date tags.
- [ ] Run host tests and source checks.

### Task 4: Codec capability scan and video transcoder

**Files:**
- Create: `app/src/main/java/io/opendesqueeze/media/CodecCapabilityScanner.java`
- Create: `app/src/main/java/io/opendesqueeze/media/VideoTranscoder.java`
- Create: `app/src/main/java/io/opendesqueeze/media/InputSurface.java`
- Create: `app/src/main/java/io/opendesqueeze/media/OutputSurface.java`
- Create: `app/src/main/java/io/opendesqueeze/media/TextureRenderer.java`

**Interfaces:**
- Consumes: input content `Uri`, output file descriptor/path, `CodecPolicy`, settings.
- Produces: MP4 with desqueezed video and copied compressed audio.

- [ ] Implement normalized MediaCodec capability scan with hardware/software classification and size checks.
- [ ] Implement EGL encoder input surface wrapper and decoder SurfaceTexture output wrapper.
- [ ] Implement pass-through external-OES renderer.
- [ ] Implement extractor -> decoder -> GL -> encoder -> muxer loop with presentation timestamps and EOS handling.
- [ ] Copy compressed audio track with a second extractor and preserve rotation hint.
- [ ] Propagate basic color metadata where accepted and surface an HDR experimental warning.
- [ ] Run host tests and source checks.

### Task 5: Foreground job service, CI, packaging, verification

**Files:**
- Create: `app/src/main/java/io/opendesqueeze/service/ProcessingService.java`
- Create: `app/src/main/java/io/opendesqueeze/service/JobStore.java`
- Create: `.github/workflows/android.yml`
- Create: `THIRD_PARTY_NOTICES.md`, `LICENSE`, `BUILDING.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: selected URIs and ExportSettings from MainActivity.
- Produces: progress notification and MediaStore outputs.

- [ ] Persist job input in app-private storage and start foreground service.
- [ ] Process selected items serially with cancel support and progress notification.
- [ ] Add GitHub Actions build using JDK 17, Gradle 9.5.0, SDK 36/build-tools 36.0.0, and upload debug APK artifact.
- [ ] Document codec fallback, HDR limitation, output locations, and build steps.
- [ ] Run `bash scripts/run-host-tests.sh` and `bash scripts/verify-source.sh`.
- [ ] Attempt a local Android build if SDK tooling can be obtained; otherwise record the exact environment blocker.
- [ ] Zip the complete source tree and, if available, copy the built APK beside it.
