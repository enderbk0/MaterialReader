# Third-party notices

MaterialReader contains no proprietary SDKs. All dependencies below are
free/open-source with the listed licenses. The full Apache-2.0 text applying
to most of them ships in [LICENSE](LICENSE).

| Dependency | Version | License |
|---|---|---|
| Kotlin stdlib, `org.jetbrains.kotlin.android` / compose / serialization Gradle plugins (JetBrains) | 2.3.10 | Apache-2.0 |
| kotlinx.coroutines-android, kotlinx-coroutines-test (JetBrains) | 1.11.0 | Apache-2.0 |
| kotlinx-serialization-json (JetBrains) | 1.10.0 | Apache-2.0 |
| Jetpack Compose BOM + Compose UI/Foundation/Material + Material 3 (Google) | BOM 2026.09.00 | Apache-2.0 |
| AndroidX activity-compose (Google) | 1.13.0 | Apache-2.0 |
| AndroidX lifecycle runtime/viewmodel-compose (Google) | 2.11.0 | Apache-2.0 |
| AndroidX navigation-compose (Google) | 2.10.2 | Apache-2.0 |
| AndroidX datastore-preferences (Google) | 1.2.1 | Apache-2.0 |
| AndroidX core-ktx (Google) | 1.19.1 | Apache-2.0 |
| PdfBox-Android, `com.tom-roush:pdfbox-android` (Tom Roush / Apache PDFBox port) | 2.0.27.0 | Apache-2.0 |
| Bouncy Castle `bcprov/bcpkix/bcutil-jdk15to18` (transitive via PdfBox-Android) | 1.72 | Bouncy Castle licence (MIT-style) |
| JUnit 4 (tests only) | 4.13.2 | EPL-1.0 |
| AndroidX Test: ui-test-junit4, test-ext junit (instrumented tests only) | BOM / 1.3.0 | Apache-2.0 |
| Android Gradle Plugin (build only) | 9.2.1 | Apache-2.0 |

PDF rendering itself uses the Android platform (`android.graphics.pdf.PdfRenderer`),
which requires no additional license.
