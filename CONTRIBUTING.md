# Contributing

Thanks for helping improve OpenDeSqueeze.

## Good contributions

- Device compatibility reports.
- Reproducible codec failures.
- Geometry/output-size fixes.
- Android lifecycle/background-processing fixes.
- Photo format support.
- MediaCodec fallback improvements.
- Accessibility and small-screen UI improvements.
- Documentation and translations.

## Development setup

See [BUILDING.md](BUILDING.md).

Before opening a pull request:

```bash
bash scripts/run-host-tests.sh
bash scripts/verify-source.sh
gradle :app:assembleDebug
```

## Bug reports

Please include as much of this as possible:

```text
Device:
Android version:
SoC:
Source type: photo / video
Source codec:
Source resolution:
Source frame rate:
Squeeze factor:
Geometry mode: Preserve Width / Preserve Height
Requested output codec:
Actual output size:
Error message:
Reproducible every time?:
```

For video issues, `adb logcat` excerpts around `MediaCodec`, `MediaMuxer`, or `OpenDeSqueeze` are useful. Remove personal file names or unrelated device data before posting logs.

## Pull requests

Keep changes focused.

- Avoid unrelated formatting churn.
- Explain the user-visible behavior.
- Add or update a test when the behavior can be tested outside Android.
- For Android-only behavior, describe how it was verified.
- Do not add analytics, telemetry, trackers, or network dependencies without an explicit project-level decision.
- Do not vendor third-party source or binaries without a license review.

## License of contributions

By submitting a contribution, you agree that your contribution may be distributed under the repository's GNU GPL v3.0 license.

## AI-assisted contributions

AI-assisted development is allowed. The human submitter remains responsible for reviewing the generated code, licensing compatibility, correctness, and the claims made in the pull request.
