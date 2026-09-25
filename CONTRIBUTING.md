# Contributing to MaterialReader

Thanks for helping keep PDF reading simple, private, and free.

## Ground rules

1. **Privacy is non-negotiable.** No analytics, ads, tracking, accounts,
   cloud sync, or `INTERNET` permission. Any PR adding network access must
   make it explicit, opt-in, and off by default — and will face heavy scrutiny.
2. **No paywalls, limits, or nag screens.** Ever.
3. **Prefer official sources.** Follow
   [Compose documentation](https://developer.android.com/develop/ui/compose/documentation),
   [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3),
   and [AndroidX source](https://github.com/androidx/androidx). Do not copy
   outdated tutorial patterns; check current stable versions in the
   [AndroidX release notes](https://developer.android.com/jetpack/androidx/versions).
4. **Real renderer only.** Do not stub PDF rendering. Improvements must go
   through `PdfSession`/`PdfDocument` or the PdfBox text layer.

## Workflow

- Fork, branch from `main`, keep changes focused.
- Architecture: UI in `library/`/`reader/`/`settings/`, state in ViewModels
  (unidirectional data flow), platform I/O behind `DocumentStore`,
  `SettingsStore`, `ReaderBackend`, `DocumentMetaSource` interfaces.
- Small, reusable composables; ViewModels hold logic, never put app logic
  directly in composables.
- Keep URI handling as **strings** at the ViewModel boundary so unit tests
  run on plain JVM (no Robolectric).

## Checks before opening a PR

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # needs a device/emulator
```

Also verify manually: open a real large PDF, rotate, switch light/dark/
dynamic color, revoke a document grant, and run the whole app in airplane
mode. Document honest limitations in the README rather than hiding them.

## Code style

- `kotlin.code.style=official`, 4-space indents, meaningful names.
- Accessibility is a feature: content descriptions on icon buttons, ≥48dp
  touch targets, visible focus behavior, TalkBack walkthrough for new screens.

## License

By contributing, you agree your contributions are licensed under the
[Apache License 2.0](LICENSE).
