package com.enderbk.materialreader.settings

/** Static, offline-safe dependency/license catalogue shown in Settings → About. */
data class DependencyLicense(val name: String, val version: String, val license: String)

val DependencyLicenses = listOf(
    DependencyLicense("Kotlin stdlib + coroutines (JetBrains)", "2.3.10 / 1.11.0", "Apache-2.0"),
    DependencyLicense("Jetpack Compose + Material 3 (Google)", "BOM 2026.09.00", "Apache-2.0"),
    DependencyLicense("Activity / Lifecycle / Navigation (AndroidX)", "1.13.0 / 2.11.0 / 2.10.2", "Apache-2.0"),
    DependencyLicense("DataStore Preferences (AndroidX)", "1.2.1", "Apache-2.0"),
    DependencyLicense("kotlinx.serialization (JetBrains)", "1.10.0", "Apache-2.0"),
    DependencyLicense("PdfBox-Android (Tom Roush, Apache PDFBox port)", "2.0.27.0", "Apache-2.0"),
    DependencyLicense("Bouncy Castle (via PdfBox-Android)", "1.72", "MIT-style (Bouncy Castle licence)"),
    DependencyLicense("JUnit 4 (tests only)", "4.13.2", "EPL-1.0")
)

const val REPOSITORY_URL = "https://github.com/enderbk0/MaterialReader"
