#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO_DIR="${1:-$HOME/OpenDeSqueeze}"
REPO="juhjuhx/OpenDeSqueeze"

cd "$REPO_DIR"

if [ ! -d .git ]; then
  echo "ERROR: $REPO_DIR is not a Git repository." >&2
  exit 1
fi

echo "== GitHub auth =="
gh auth status

echo "== Current status =="
git status --short

echo
echo "== Basic secret / signing-material preflight =="
if find . -maxdepth 4 \( -name "*.jks" -o -name "*.keystore" -o -name ".env" \) -print | grep -q .; then
  echo "ERROR: signing material or .env found inside the repository tree. Review before publishing." >&2
  find . -maxdepth 4 \( -name "*.jks" -o -name "*.keystore" -o -name ".env" \) -print
  exit 2
fi

if git grep -nE 'github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9]+|-----BEGIN [A-Z ]*PRIVATE KEY-----' -- . ':!PUBLISH_FROM_TERMUX.sh' >/tmp/opendesqueeze-secret-scan.txt 2>/dev/null; then
  echo "ERROR: possible credential material detected. Review:" >&2
  cat /tmp/opendesqueeze-secret-scan.txt >&2
  exit 2
fi
echo "Preflight: no obvious committed token/private-key patterns found."

echo
echo "== Make repository public =="
gh repo edit "$REPO" \
  --visibility public \
  --accept-visibility-change-consequences

echo
echo "== Repository metadata =="
gh repo edit "$REPO" \
  --description "Local-first Android batch anamorphic desqueeze for photos and video. MediaCodec/OpenGL, no FFmpeg, no network." \
  --enable-issues=true \
  --add-topic android \
  --add-topic anamorphic \
  --add-topic desqueeze \
  --add-topic photography \
  --add-topic video \
  --add-topic mediacodec \
  --add-topic opengl \
  --add-topic local-first \
  --add-topic gplv3

echo
echo "== Stage public-release documentation =="
git add \
  README.md README.zh-TW.md BUILDING.md THIRD_PARTY_NOTICES.md \
  CONTRIBUTING.md CONTRIBUTORS.md CODE_OF_CONDUCT.md SECURITY.md CHANGELOG.md \
  .gitignore \
  assets \
  docs/ARCHITECTURE.md docs/DEVELOPMENT_WORKFLOW.md docs/FAQ.md \
  .github/ISSUE_TEMPLATE \
  .github/PULL_REQUEST_TEMPLATE.md \
  PUBLISH_FROM_TERMUX.sh

if git diff --cached --quiet; then
  echo "No documentation changes to commit."
else
  git commit -m "docs: prepare OpenDeSqueeze for public release"
  git push origin main
fi

echo
echo "== Verify repository =="
gh repo view "$REPO" --json nameWithOwner,visibility,url,description

echo
echo "== Latest CI =="
gh run list --limit 3 || true

echo
echo "DONE."
echo "README banner: assets/opendesqueeze-banner.svg"
echo "Square mark:  assets/opendesqueeze-mark.svg"
echo
echo "Manual GitHub step still recommended:"
echo "  Repo → Settings → General → Social preview → upload assets/opendesqueeze-banner.png"
