# FAQ

## Is 2.35:1 the desqueeze factor?

No. `2.35:1` is usually the resulting display aspect ratio. The lens has a squeeze factor such as 1.33× or 1.55×. Use the lens factor for desqueeze.

## Why does Preserve Width reduce height?

It corrects geometry while keeping the source width inside common mobile encoder limits. This is especially useful for 4K phone footage.

## Why no FFmpeg?

The current goal is a small Android-native utility using MediaCodec, OpenGL ES, MediaMuxer, and MediaStore. This avoids shipping native binaries and ABI variants.

## Does it use the NPU?

There is no portable Android NPU video-encoding API. The app uses MediaCodec, which is the standard interface through which Android devices expose vendor media hardware.

## Does it upload my media?

No. The current manifest does not request `INTERNET`.

## Can I fork it?

Yes. The repository is intended to be forked and modified under GPL-3.0.

## Can I make a closed-source commercial fork?

GPLv3 does not forbid charging money, but distribution of GPL-covered derivatives/binaries comes with GPL source and licensing obligations. Review the license before redistributing.
