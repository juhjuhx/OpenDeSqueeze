#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

python - "$MANIFEST" <<'PY'
import sys, xml.etree.ElementTree as ET
path=sys.argv[1]
ET.parse(path)
print('manifest XML: PASS')
PY

if grep -q 'android.permission.INTERNET' "$MANIFEST"; then
  echo 'FAIL: INTERNET permission must not be present' >&2
  exit 1
fi
if ! grep -q 'foregroundServiceType="mediaProcessing"' "$MANIFEST"; then
  echo 'FAIL: mediaProcessing foreground service type missing' >&2
  exit 1
fi
if grep -R -E 'import (java\.net|okhttp3|retrofit2)|android.permission.INTERNET' "$ROOT/app/src/main"; then
  echo 'FAIL: network surface detected' >&2
  exit 1
fi
if grep -R -E '\.isBlank\(|List\.copyOf\(|readAllBytes\(' "$ROOT/app/src/main/java"; then
  echo 'FAIL: Java library API newer than Android 8 compatibility floor detected' >&2
  exit 1
fi
if grep -qE 'implementation\(|api\(' "$ROOT/app/build.gradle.kts"; then
  echo 'FAIL: v1 must have no runtime third-party dependencies' >&2
  exit 1
fi
for f in \
  io/opendesqueeze/MainActivity.java \
  io/opendesqueeze/media/PhotoProcessor.java \
  io/opendesqueeze/media/VideoTranscoder.java \
  io/opendesqueeze/media/CodecCapabilityScanner.java \
  io/opendesqueeze/service/ProcessingService.java \
  io/opendesqueeze/service/JobStore.java; do
  test -f "$ROOT/app/src/main/java/$f" || { echo "FAIL: missing $f" >&2; exit 1; }
done

grep -q 'ACTION_OPEN_DOCUMENT' "$ROOT/app/src/main/java/io/opendesqueeze/MainActivity.java"
grep -q 'EXTRA_ALLOW_MULTIPLE' "$ROOT/app/src/main/java/io/opendesqueeze/MainActivity.java"
grep -q 'MediaCodec.createDecoderByType' "$ROOT/app/src/main/java/io/opendesqueeze/media/VideoTranscoder.java"
grep -q 'MediaCodec.createByCodecName' "$ROOT/app/src/main/java/io/opendesqueeze/media/VideoTranscoder.java"
grep -q 'MediaMuxer' "$ROOT/app/src/main/java/io/opendesqueeze/media/VideoTranscoder.java"
grep -q 'GLES11Ext.GL_TEXTURE_EXTERNAL_OES' "$ROOT/app/src/main/java/io/opendesqueeze/media/TextureRenderer.java"
grep -q 'MediaStore.MediaColumns.DATA' "$ROOT/app/src/main/java/io/opendesqueeze/media/MediaStoreWriter.java"
grep -q 'orderedCandidates' "$ROOT/app/src/main/java/io/opendesqueeze/media/VideoTranscoder.java"
grep -q 'onTimeout(int startId, int fgsType)' "$ROOT/app/src/main/java/io/opendesqueeze/service/ProcessingService.java"

echo 'source invariants: PASS'
