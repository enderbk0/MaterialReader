package com.enderbk.materialreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(name = "library")
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val DocumentsJson = stringPreferencesKey("documents_json_v1")

private val json = Json { ignoreUnknownKeys = true }

/**
 * DataStore-backed [DocumentStore]. The whole library is a small JSON array of
 * metadata in the app-private preferences file — included in Auto Backup, never
 * sent anywhere.
 */
class DataStoreDocumentStore(private val context: Context) : DocumentStore {

    override val documents: Flow<List<DocumentEntry>> =
        context.libraryDataStore.data
            .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .map { prefs -> decode(prefs[DocumentsJson]) }

    override suspend fun upsert(entry: DocumentEntry) {
        mutate { list ->
            val index = list.indexOfFirst { it.id == entry.id }
            if (index == -1) list + entry else list.toMutableList().also { it[index] = entry }
        }
    }

    override suspend fun remove(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    override suspend fun setPinned(id: String, pinned: Boolean) {
        mutate { list -> list.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    }

    override suspend fun setReadingPosition(id: String, page: Int, pageCount: Int?) {
        mutate { list ->
            list.map {
                if (it.id == id) {
                    it.copy(
                        lastPage = page,
                        pageCount = pageCount ?: it.pageCount,
                        lastOpenedEpochMillis = System.currentTimeMillis()
                    )
                } else {
                    it
                }
            }
        }
    }

    override suspend fun get(id: String): DocumentEntry? = documents.first().firstOrNull { it.id == id }

    private suspend fun mutate(transform: (List<DocumentEntry>) -> List<DocumentEntry>) {
        context.libraryDataStore.edit { prefs ->
            prefs[DocumentsJson] = json.encodeToString(ListSerializer(DocumentEntry.serializer()), transform(decode(prefs[DocumentsJson])))
        }
    }

    private fun decode(raw: String?): List<DocumentEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(DocumentEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }
}

private object ThemeKeys {
    val themeMode = stringPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
    val zoomMode = stringPreferencesKey("default_zoom_mode")
    val pageLayout = stringPreferencesKey("page_layout")
    val keepAwake = booleanPreferencesKey("keep_screen_awake")
    val rememberPosition = booleanPreferencesKey("remember_reading_position")
    val readerBackground = stringPreferencesKey("reader_background")
    val nightMode = booleanPreferencesKey("night_mode")
    val sortOrder = stringPreferencesKey("sort_order")
}

/** DataStore-backed [SettingsStore]. */
class DataStoreSettingsStore(private val context: Context) : SettingsStore {

    override val settings: Flow<AppSettings> =
        context.settingsDataStore.data
            .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .map { prefs ->
                AppSettings(
                    themeMode = prefs[ThemeKeys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                        ?: AppSettings().themeMode,
                    dynamicColor = prefs[ThemeKeys.dynamicColor] ?: true,
                    defaultZoomMode = prefs[ThemeKeys.zoomMode]?.let { runCatching { ZoomMode.valueOf(it) }.getOrNull() }
                        ?: AppSettings().defaultZoomMode,
                    pageLayout = prefs[ThemeKeys.pageLayout]?.let { runCatching { PageLayout.valueOf(it) }.getOrNull() }
                        ?: AppSettings().pageLayout,
                    keepScreenAwake = prefs[ThemeKeys.keepAwake] ?: false,
                    rememberReadingPosition = prefs[ThemeKeys.rememberPosition] ?: true,
                    readerBackground = prefs[ThemeKeys.readerBackground]?.let { runCatching { ReaderBackground.valueOf(it) }.getOrNull() }
                        ?: AppSettings().readerBackground,
                    nightMode = prefs[ThemeKeys.nightMode] ?: false,
                    sortOrder = prefs[ThemeKeys.sortOrder]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
                        ?: AppSettings().sortOrder
                )
            }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val next = transform(current)
        context.settingsDataStore.edit { prefs ->
            prefs[ThemeKeys.themeMode] = next.themeMode.name
            prefs[ThemeKeys.dynamicColor] = next.dynamicColor
            prefs[ThemeKeys.zoomMode] = next.defaultZoomMode.name
            prefs[ThemeKeys.pageLayout] = next.pageLayout.name
            prefs[ThemeKeys.keepAwake] = next.keepScreenAwake
            prefs[ThemeKeys.rememberPosition] = next.rememberReadingPosition
            prefs[ThemeKeys.readerBackground] = next.readerBackground.name
            prefs[ThemeKeys.nightMode] = next.nightMode
            prefs[ThemeKeys.sortOrder] = next.sortOrder.name
        }
    }
}
