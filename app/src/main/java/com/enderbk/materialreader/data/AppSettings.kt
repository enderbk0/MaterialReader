package com.enderbk.materialreader.data

import kotlinx.serialization.Serializable

/** Theme choice stored in Settings. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Default zoom applied when a document is opened. */
enum class ZoomMode { FIT_WIDTH, FIT_PAGE, ACTUAL_SIZE }

/** Page layout preference. */
enum class PageLayout { CONTINUOUS, SINGLE_PAGE }

/** Reader background preference. */
enum class ReaderBackground { DEFAULT, PAPER, DIM }

/** Library sort order. Persisted so it survives restarts. */
enum class SortOrder { RECENT, NAME, SIZE }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultZoomMode: ZoomMode = ZoomMode.FIT_WIDTH,
    val pageLayout: PageLayout = PageLayout.CONTINUOUS,
    val keepScreenAwake: Boolean = false,
    val rememberReadingPosition: Boolean = true,
    val readerBackground: ReaderBackground = ReaderBackground.DEFAULT,
    /** Night mode inverts page colors (dark text becomes light) for dark environments. */
    val nightMode: Boolean = false,
    val sortOrder: SortOrder = SortOrder.RECENT
)
