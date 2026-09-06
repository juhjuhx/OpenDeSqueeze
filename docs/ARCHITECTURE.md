# Architecture

OpenDeSqueeze is deliberately small. The architecture separates UI/state, pure geometry/codec policy, Android media I/O, and long-running processing.

## System map

```text
┌─────────────────────────────────────────────────────────────┐
│ MainActivity                                                │
│ SAF selection · presets · output options · job start        │
└────────────────────────────┬────────────────────────────────┘
                             │
                   SelectedMedia / ExportSettings
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│ ProcessingService                                           │
│ Foreground media-processing service · queue · notification  │
└─────────────────┬───────────────────────────┬───────────────┘
                  │                           │
              PHOTO PATH                 VIDEO PATH
                  │                           │
                  ▼                           ▼
          PhotoProcessor                MediaInspector
                  │                           │
                  │                    CodecCapabilityScanner
                  │                           │
                  │                       CodecPolicy
                  │                           │
                  │                           ▼
                  │                    VideoTranscoder
                  │                           │
                  │       MediaExtractor → MediaCodec decoder
                  │                           │
                  │             OutputSurface / SurfaceTexture
                  │                           │
                  │                    TextureRenderer
                  │                       OpenGL ES
                  │                           │
                  │                    InputSurface
                  │                           │
                  │              MediaCodec encoder
                  │                           │
                  │                    MediaMuxer
                  │                           │
                  └──────────────┬────────────┘
                                 ▼
                          MediaStoreWriter
                                 │
                    Pictures/OpenDeSqueeze
                      Movies/OpenDeSqueeze
```

## Core packages

### `io.opendesqueeze.core`

Pure or near-pure decision logic.

- `DesqueezeMath` — converts squeeze factor + source geometry into output geometry.
- `CodecPolicy` — orders codec candidates and bitrate/fallback behavior.
- `OutputNaming` — deterministic output file naming.

These classes are exercised by host-side JVM tests without Android runtime.

### `io.opendesqueeze.model`

Data passed between UI and processing layers.

- `SelectedMedia`
- `ExportSettings`

### `io.opendesqueeze.media`

Android media boundary.

- `MediaInspector` — source metadata.
- `CodecCapabilityScanner` — runtime encoder discovery and size capability checks.
- `PhotoProcessor` — bitmap/photo geometry + export.
- `VideoTranscoder` — decode → GPU transform → encode → mux.
- `OutputSurface`, `InputSurface`, `TextureRenderer` — EGL/OpenGL surface bridge.
- `MediaStoreWriter` — output publication.

### `io.opendesqueeze.service`

Long-running batch execution.

- `ProcessingService` — foreground lifecycle, queue execution, notifications, timeout behavior.
- `JobStore` — job state shared with the UI/service.

## Design constraints

### Local first

The app does not request `INTERNET`. The media path must stay functional with airplane mode enabled.

### No vendor codec names

A device may expose Qualcomm, MediaTek, Samsung, Google, or software codecs. Selection is capability-based through `MediaCodecList`, then validated by actual encoder startup.

### Preserve Width by default

Very wide outputs often exceed mobile encoder limits. For a 3840×2160 1.33× source, 3840×1624 is more portable than ~5108×2160.

### No bundled FFmpeg

v0.1 uses Android framework media APIs to keep the APK small, avoid native ABI packaging, and let the platform use its normal hardware media stack.

### HDR is guarded

The current GL path is not advertised as validated 10-bit HDR. HDR inputs are detected and protected by default.

## Failure model

Codec discovery is advisory. A vendor codec can claim support and still fail when configured.

The video path therefore treats startup as the real capability test:

```text
candidate 1
   │ configure/start
   ├─ success → transcode
   └─ failure → release → candidate 2
```

## Tests and CI

```text
push main
   ↓
GitHub Actions
   ↓
JDK 17
   ↓
Android SDK 36 + Build Tools 36
   ↓
host tests
   ↓
source invariants
   ↓
:app:assembleDebug
   ↓
APK artifact
```

Host tests cover geometry, output naming, and codec policy. The Android build catches platform API and resource integration errors. Real-device tests remain necessary for codec/driver behavior.
