# MaterialReader

**Open a PDF. Read it. That's it.**

MaterialReader is a free, open-source, privacy-first PDF reader for Android.
No accounts. No subscriptions. No ads. No analytics. No cloud. No internet
permission. Reading a PDF never requires paying money, creating an account,
watching an advertisement, or uploading the document — that is an
architectural guarantee, not a marketing claim.

- License: [Apache-2.0](LICENSE)
- Privacy design: everything stays on-device; see [Privacy](#privacy) below
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security: [SECURITY.md](SECURITY.md)
- Dependency licenses: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)

## Features

**Library**

- Recent + pinned/favorite PDFs, search by filename, sort by recent/name/size
- Prominent "Open PDF" action using the Android system document picker
  (Storage Access Framework — no broad filesystem access requested)
- Persistable URI grants, so documents stay in the library across restarts
- Useful empty state; the app never nags, never shows ads, never asks to rate

**Reader**

- Real rendering via the platform `PdfRenderer` (text, scanned/image PDFs,
  embedded fonts, transparency, mixed page sizes/orientations, annotations
  where the engine supports them)
- Lazy, on-demand page rendering with a bounded LRU bitmap cache — large
  documents do not load fully into memory
- Continuous scroll and single-page layouts
- Current page indicator, page count, reading progress, jump-to-page dialog
- Pinch-to-zoom, double-tap zoom, zoom in/out, fit-to-width, fit-to-page,
  actual (physical) size
- Night mode: inverts page colors for reading in the dark (dark text becomes
  light); a hue-restoring filter keeps colored figures recognizable, though
  photos/diagrams are still recolored — toggle in the reader menu or Settings
- In-document text search (offline, via the bundled PdfBox text layer)
- Per-page text sheet: selectable text, copy, share, and tappable
  http(s) link annotations (opened in the user's browser on explicit tap)
- Optional "remember reading position" (on-device only)
- Optional "keep screen awake while reading"
- Follows device rotation automatically; light/dark/dynamic-color aware
- Opens PDFs from the picker, from file managers (`VIEW`), and from share
  sheets (`SEND`)

**Settings**

- Appearance: system/light/dark theme, dynamic color (Android 12+),
  reader background (default/paper/dim)
- Reading: default zoom, page layout, keep-awake, remember position
- Privacy: plain-language statement of what the app does *not* do
- About: version, Apache-2.0 license, source repository, dependency licenses

## Privacy

MaterialReader is privacy-first **by architecture**:

- Declares **zero permissions** — notably, there is **no `INTERNET`
  permission**, so the app *cannot* transmit anything, even if it wanted to.
- No analytics, advertising, telemetry, crash-upload, tracking IDs, accounts,
  cloud sync, or background network calls.
- PDFs are read in place through `ContentResolver`; contents are never copied
  to app storage, never uploaded, never shared with third parties.
- Library metadata (filenames, positions), bookmarks, and settings live in
  app-private `DataStore` files on the device (included in the user's own
  Auto Backup; never sent to us — there is no "us" server).
- Opening a link annotation fires a normal `ACTION_VIEW` intent: the *user's
  browser* opens it, only after an explicit tap.

Verify it yourself: `grep -r INTERNET app/src/main/AndroidManifest.xml`
returns nothing, and the dependency list contains no proprietary SDKs.

## PDF compatibility — honest limits

"All PDFs readable" is a compatibility *goal*. What works and what does not:

| Works | Notes |
|---|---|
| Text PDFs, scanned/image PDFs, multi-page & large documents, embedded fonts, images, transparency, mixed sizes/orientations | Rendered by the platform engine |
| In-document search, copyable page text, external links | Offline text layer (PdfBox-Android) |
| Encrypted/password-protected PDFs | ❌ Clear error message; not supported yet |
| Interactive forms, JavaScript, video/audio, 3D | ❌ Rendered statically or not at all; engine limitation |
| In-place text selection *on the rendered bitmap* | ⚠️ By design: selection happens in the page-text sheet (selectable text + copy/share), because `PdfRenderer` exposes no text runs. Scanned pages show an honest "no extractable text" note |
| Very large search indexes | Search caps at 2,000 pages / 300 hits to bound time and memory |

## Tech stack

- Kotlin, Jetpack Compose, Material 3 (dynamic color, edge-to-edge,
  adaptive bottom-bar/rail navigation), Navigation Compose
- AndroidX (activity, lifecycle, datastore-preferences), coroutines,
  kotlinx.serialization, Gradle Kotlin DSL
- Rendering: `android.graphics.pdf.PdfRenderer` (platform, no extra SDK)
- Text layer: PdfBox-Android 2.0.27.0 (Apache-2.0) + Bouncy Castle
- Architecture: unidirectional data flow, ViewModels, `DocumentStore` /
  `SettingsStore` / `ReaderBackend` interfaces with DataStore-backed and
  in-memory implementations (the latter power previews and JVM tests)
- Settings row design (grouped `surfaceContainer` rows, whole-row toggle
  targets, press-morphing corners) inspired by
  [ReFra](https://github.com/IacobIonut01/ReFra) (Apache-2.0); segmented
  controls follow the official M3 segmented-button guidance
- Dependency versions follow the official AndroidX release notes
  (Compose BOM 2026.09.00, Navigation 2.10.2, DataStore 1.2.1, …)

Project layout:

```
app/src/main/java/com/enderbk/materialreader/
  MainActivity.kt  MaterialReaderApp.kt
  navigation/  ui/theme/
  library/  reader/  settings/
  data/  domain/  pdf/  util/
```

## Build & verify

Requirements: JDK 17+, Android SDK with `platforms;android-37` and
`build-tools;36.0.0` (see `local.properties` for `sdk.dir`).

```bash
./gradlew :app:assembleDebug      # build
./gradlew :app:testDebugUnitTest  # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # on-device UI tests
```

Manual checklist (also see CONTRIBUTING.md): open a real multi-hundred-page
PDF, scroll, rotate, toggle themes, revoke a URI grant, enable airplane mode
— everything must keep working offline.

## Permissions

None. The manifest intentionally declares no `<uses-permission>`.

## License

Copyright 2026 MaterialReader Contributors.
Licensed under the [Apache License, Version 2.0](LICENSE).
