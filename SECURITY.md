# Security Policy

## Privacy posture

MaterialReader declares **no permissions** (including no `INTERNET`), ships
no analytics/telemetry/crash-upload SDKs, and keeps all documents, metadata,
and settings on-device. The most likely "vulnerability" in such an app is
someone re-introducing network exfiltration — treat any unexpected network
code as a security issue (see below).

## Reporting a vulnerability

- **Do not** open a public issue for a suspected vulnerability.
- Email the maintainers with: affected version, steps to reproduce, and the
  impact you see. Include logs only after redacting filenames if they are
  sensitive to you.
- Expect an acknowledgement within 7 days and a fix or mitigation plan
  within 30 days for confirmed issues.

## Scope

In scope: the app code in this repository, its Gradle configuration, and its
declared dependencies.

The maintainers will specifically prioritize:

- Anything causing PDF content, filenames, or reading history to leave the
  device.
- New permissions (especially network) or proprietary SDKs added without
  explicit opt-in design.
- Intent-handling issues (`VIEW`/`SEND`) that could expose content to other
  apps unintentionally.
- Memory-safety-adjacent crashes in rendering/caching of malformed PDFs
  (note: rendering itself is done by the platform `PdfRenderer`).

## Out of scope

- Feature requests (use the issue tracker).
- Reports that the app "phones home" — it cannot; there is no `INTERNET`
  permission. If you find otherwise, that *is* in scope: report it.

## Supported versions

Only the latest `main` and the latest tagged release receive fixes.
