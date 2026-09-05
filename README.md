# OpenDeSqueeze

A tiny local-first Android app for batch anamorphic desqueeze of photos and videos.

## What it does

- Select one or many photos/videos through Android's Storage Access Framework.
- Presets: 1.33x, 1.50x, 1.55x, 1.80x, 2.00x, or custom 1.01-3.00x.
- **Preserve Width** (default): keeps width and reduces height, e.g. 3840x2160 at 1.33x -> 3840x1624.
- **Preserve Height**: keeps height and expands width, e.g. 3840x2160 at 1.33x -> 5108x2160.
- Batch JPEG/PNG/WebP Lossless photos.
- Batch H.264, H.265, or AV1 video with hardware-codec discovery.
- Auto video policy: hardware HEVC -> hardware AVC -> software HEVC -> software AVC.
- Original compressed audio can be copied without re-encoding.
- Processing continues in an Android foreground media-processing service.
- Outputs go to `Pictures/OpenDeSqueeze` or `Movies/OpenDeSqueeze` on Android 10+.
- No network permission; media stays on the device.

## Why Preserve Width is the default

Many mobile encoders accept 3840-wide 4K but do not accept a 5108-wide output. Reducing 2160 to ~1624 produces the same corrected geometry without creating a >5K stream, so it is much more likely to stay on the device's hardware codec path.

## Video engine

OpenDeSqueeze v1 intentionally has no FFmpeg/native binary dependency:

```text
MediaExtractor
    -> MediaCodec decoder
    -> SurfaceTexture / OpenGL ES
    -> MediaCodec encoder
    -> MediaMuxer (MP4)
```

The app queries `MediaCodecList` at runtime instead of assuming a Snapdragon/Dimensity/Exynos-specific encoder name. Vendor GPU/video blocks are therefore reached through Android's standard MediaCodec abstraction. Android does not expose a portable NPU video-encoding API, so the UI does not pretend to offer one.

## HDR status

HDR is **experimental** in v1. The app detects common HDR transfer metadata and refuses to process it by default. If experimental HDR is enabled, color metadata is forwarded where possible, but the current OpenGL ES 8-bit EGL path is not claimed to be a validated 10-bit HDR pipeline.

## Build

See [BUILDING.md](BUILDING.md). CI builds a debug APK automatically.

## License

GPL-3.0-or-later. See `LICENSE` and `THIRD_PARTY_NOTICES.md`.
