<div align="center">

<img src="design/iconcenter-cropped.png" alt="MaterialReader" width="180">

# MaterialReader
**Open a PDF. Read it. That's it.**

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Release](https://img.shields.io/github/v/release/enderbk0/MaterialReader)
![Platform](https://img.shields.io/badge/platform-Android%2024%2B-3DDC84?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple?logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09-4285F4?logo=jetpackcompose)

![Ads](https://img.shields.io/badge/ads-none-green)
![Tracking](https://img.shields.io/badge/tracking-none-green)
![Accounts](https://img.shields.io/badge/accounts-none-green)

</div>



MaterialReader is a free and open-source PDF reader for Android. No accounts.
No subscriptions. No ads. No analytics. No cloud. Reading a PDF never
requires paying money, creating an account, watching an advertisement, or
uploading the document — that is an architectural guarantee, not a slogan.

## Contents

- [Features](#features)
- [Download](#download)
- [Privacy](#privacy)
- [Permissions](#permissions)
- [PDF compatibility](#pdf-compatibility)
- [Rendering architecture](#rendering-architecture)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Languages](#languages)
- [Build and verify](#build-and-verify)
- [Contributing](#contributing)
- [License](#license)

## Features

**Library**

- Recent and pinned/favorite PDFs, filename search, sorting, and folders
- System document picker (Storage Access Framework) — no broad file access
- Persisted access so documents stay in the library across restarts
- Missing files are flagged with one-tap relink or remove
- Opens PDFs from file managers (`VIEW`) and share sheets (`SEND`)

**Reader**

- Real rendering with lazy, on-demand pages and a bounded bitmap cache
- Continuous scroll and single-page layouts, reading progress, jump-to-page
- Pinch-to-zoom anchored at the fingers, double-tap zoom, zoom controls,
  fit-to-width, fit-to-page, actual size, free 2D panning while zoomed
- Offline in-document search, copyable page text, share, tappable links
- Night mode with picture preservation, print, share-the-file, details sheet
- Optional reading-position memory and keep-awake

**Settings**

- System/light/dark theme, dynamic color, rounded interface font
- Default zoom, page layout, reader background, per-app language
- Plain-language privacy statement and a full offline About screen

## Download

- **Stable APK:** get the latest signed release from the
  [Releases page](https://github.com/enderbk0/MaterialReader/releases).
- **Build it yourself:** see [Build and verify](#build-and-verify) — one
  command, no accounts or keys required for debug builds.

Note: release builds are signed with the maintainer key. If you previously
installed a debug build, uninstall it first — Android treats different
signatures as different apps.

## Privacy

Privacy-first by architecture:

- **Zero permissions declared** — notably no `INTERNET`, so the app cannot
  transmit anything even if it tried:

  ```bash
  grep -r INTERNET app/src/main/AndroidManifest.xml # returns nothing
  ```

- No analytics, advertising, telemetry, crash upload, tracking IDs,
  accounts, cloud sync, or background network calls.
- PDFs are read in place through the `ContentResolver`; contents are never
  copied to app storage, uploaded, or shared with third parties.
- Library metadata and settings live in app-private on-device storage only.
- Link annotations open in the user's browser on explicit tap only.

## Permissions

None. The manifest intentionally declares no `<uses-permission>`.

## PDF compatibility

"All PDFs readable" is treated as a compatibility goal. Current status:

| Status | Coverage |
|---|---|
| Supported | Text PDFs, scanned/image PDFs, multi-page and large documents, embedded fonts, images, transparency, mixed page sizes and orientations, links, annotations where the engine supports them, offline search and text extraction |
| Supported with adaptation | Night mode on photo pages (dimmed originals), scanned pages (dimmed), vector charts (dimmed to preserve hues), dark-background PDFs (dimmed instead of inverted) |
| Not supported | Password-protected PDFs (clear error message), interactive forms, JavaScript, embedded audio/video/3D |

Limitations are documented here rather than hidden. See
[Rendering architecture](#rendering-architecture) for how night mode
classifies pages.

## Rendering architecture

An earlier zoom regression was traced to our own gesture/state layer, not
the renderer — so the engine stayed, and only the plumbing was fixed:

- **One `PdfSession` per open document.** The platform `PdfRenderer` handle
  is created once at open and closed on exit — never per zoom update.
- **White paper guarantee.** `PdfRenderer` composites over the bitmap
  without clearing it, and transparent-background PDFs are common: every
  destination bitmap is pre-filled white, as mainstream readers do.
- **Pinch accumulates per gesture** against the scale at gesture start, then
  passes a small throttle; scale changes are compensated around the fingers'
  position, so content stays put.
- **No per-frame re-rendering:** settled page/width pairs only, with
  debouncing, width bucketing, resolution caps, OOM retry, and a shared LRU.
  Recomposition carries state, never pixels.

The official AndroidX PDF libraries (`androidx.pdf`, 1.0.0-beta01) were
evaluated and rejected on evidence: pre-release stability, minSdk 28
(vs 24 here), a transitive proprietary Play Services ML Kit dependency, and
an isolated-process service that custom compositing could not run inside.
PdfBox-Android remains only for offline text extraction, link annotations,
and per-page image-region analysis for night mode.

Night classification combines coverage, bleed, draw order vs. text,
curves/shading activity, filled-area share, and OCR presence: text PDFs
invert to true black, embedded photos are composited back as dimmed
originals, full-page scans darken readably with or without OCR layers, and
vector-heavy pages dim so chart hues survive. PDF appearance (Normal /
Night) is fully decoupled from the Material theme and covered by tests.

## Tech stack

- Kotlin, Jetpack Compose, Material 3 (dynamic color, edge-to-edge,
  adaptive bottom-bar/rail navigation), Navigation Compose
- AndroidX (activity, lifecycle, appcompat, datastore-preferences),
  coroutines, kotlinx.serialization, Gradle Kotlin DSL
- Rendering: `android.graphics.pdf.PdfRenderer` (platform, no extra SDK)
- Text layer: PdfBox-Android 2.0.27.0 (Apache-2.0)
- Dependency versions follow the official AndroidX release notes
  (Compose BOM 2026.09.00, Navigation 2.10.2, DataStore 1.2.1)

## Project structure

```
app/src/main/java/com/enderbk/materialreader/
  MainActivity.kt  MaterialReaderApp.kt
  navigation/  ui/theme/
  library/  reader/  settings/
  data/  domain/  pdf/  util/
```

UI state lives in ViewModels with unidirectional data flow; platform I/O
sits behind `DocumentStore`, `SettingsStore`, `ReaderBackend`, and
`DocumentMetaSource` interfaces with DataStore-backed and in-memory
implementations (the latter power previews and JVM tests).

## Languages

The UI is fully localizable through `res/values/strings.xml`, with
Vietnamese in `res/values-vi/`. The in-app selector (Settings → Language)
uses the official per-app language APIs (`localeConfig` plus the AppCompat
backport) with an explicit restart step, and system Settings changes are
detected and followed automatically. To add a language, copy
`values/strings.xml` into `values-<code>/`, translate it, and register the
code in `res/xml/locales_config.xml` — no code changes needed.

An opt-in rounded interface font (Nunito, OFL) ships in `res/font/` and is
toggled in Settings → Appearance. PDF content always renders with its own
embedded fonts.

## Build and verify

Requirements: JDK 17+, Android SDK with `platforms;android-37`
(see `local.properties` for `sdk.dir`).

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # signed release APK (needs keystore, below)
./gradlew :app:testDebugUnitTest  # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # on-device UI tests
```

Release signing is intentionally not committed: copy
`keystore.properties.example` to `keystore.properties`, point it at your
own keystore, and keep both out of version control. Debug builds need no
keys at all.

Manual checklist: open a large real PDF, scroll, rotate, toggle themes,
revoke a document grant, and run the whole app in airplane mode.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports that include the app
version (Settings → About), the document type, and the Details → Appearance
line get fixed fastest. Security issues: see [SECURITY.md](SECURITY.md).

## License

Copyright (C) 2026 EnderBK.
Licensed under the [Apache License, Version 2.0](LICENSE).
Dependency licenses: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
