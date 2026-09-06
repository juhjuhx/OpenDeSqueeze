# Development workflow

## Local phone workflow

The project can be maintained entirely from an Android phone with Termux:

```text
Termux
  ↓ edit
git status
  ↓
host checks
  ↓
git add
  ↓
git commit
  ↓
git push
  ↓
GitHub Actions
  ↓
APK artifact
  ↓
download/install on phone
  ↓
real-device test
```

## Useful commands

```bash
cd ~/OpenDeSqueeze

git status --short

bash scripts/run-host-tests.sh
bash scripts/verify-source.sh

git add <files>
git commit -m "type: concise change"
git push origin main

gh run watch
```

Download the most recent APK artifact after a successful run:

```bash
rm -rf ~/OpenDeSqueeze-apk
mkdir -p ~/OpenDeSqueeze-apk

gh run download \
  -n OpenDeSqueeze-debug-apk \
  -D ~/OpenDeSqueeze-apk
```

## Debugging CI

Find failed steps:

```bash
gh run list --limit 5
gh run view <RUN_ID> --log-failed
```

For Java compiler errors:

```bash
gh run view <RUN_ID> --log-failed \
  | grep -n -B 8 -A 20 "error:"
```

Do not diagnose from the last lines of a Gradle stack trace alone. Find the first compiler/resource/task error that caused the failure.
