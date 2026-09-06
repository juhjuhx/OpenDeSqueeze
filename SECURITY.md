# Security Policy

## Supported branch

Security fixes target the current `main` branch while OpenDeSqueeze is pre-1.0.

## Reporting a vulnerability

Do not post credentials, private media, or a working exploit containing sensitive user data in a public issue.

Preferred path:

1. Use GitHub's private vulnerability reporting / Security Advisory flow if enabled for the repository.
2. If that is unavailable, open a minimal public issue that says a security report is needed, without including exploit details or private data.

## Security boundaries

OpenDeSqueeze currently:

- does not request Android `INTERNET`;
- does not contain an analytics SDK;
- does not upload media;
- reads user-selected media through Android storage/document APIs;
- writes output through MediaStore.

A dependency or feature that introduces network access, telemetry, remote code, native binaries, or new file-system privileges should receive explicit security and licensing review.
