# Third-party research notices

OpenDeSqueeze v1 is a clean-room implementation built on Android framework APIs. No third-party source file is vendored into the app.

The following open-source projects informed product and architecture decisions:

- **Open Video Editor** — https://github.com/devhyper/open-video-editor — GPL-3.0. Referenced for focused Android video-editing UX and Media3 usage patterns.
- **Video Transcoder** — https://github.com/brarcher/video-transcoder — GPL-3.0. Referenced for the small, task-focused transcoder product model.
- **ImageToolbox** — https://github.com/T8RIN/ImageToolbox — Apache-2.0. Referenced for batch image workflow and export-format ideas.
- **AndroidX Media** — https://github.com/androidx/media — Apache-2.0. Official Android media project and documentation were consulted for MediaCodec/OpenGL transformation architecture.
- **anamorphic-desqueeze-android-app** — https://github.com/hanscappelle/anamorphic-desqueeze-android-app — source visible, but no clear root license was found during review. **No code from this repository is copied or incorporated.** Only the user-facing idea of factor presets/custom desqueeze was considered.

Android framework APIs and Android developer documentation are used according to their respective platform/documentation terms.
