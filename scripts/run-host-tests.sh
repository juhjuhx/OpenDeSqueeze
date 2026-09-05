#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.host-test-out"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/io/opendesqueeze/core/DesqueezeMath.java" \
  "$ROOT/app/src/main/java/io/opendesqueeze/core/CodecPolicy.java" \
  "$ROOT/app/src/main/java/io/opendesqueeze/core/OutputNaming.java" \
  "$ROOT/host-tests/io/opendesqueeze/core/DesqueezeMathTest.java" \
  "$ROOT/host-tests/io/opendesqueeze/core/CodecPolicyTest.java" \
  "$ROOT/host-tests/io/opendesqueeze/core/OutputNamingTest.java"
java -cp "$OUT" io.opendesqueeze.core.DesqueezeMathTest
java -cp "$OUT" io.opendesqueeze.core.CodecPolicyTest
java -cp "$OUT" io.opendesqueeze.core.OutputNamingTest
