package com.enderbk.materialreader.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.enderbk.materialreader.data.AppSettings
import com.enderbk.materialreader.data.PageLayout
import com.enderbk.materialreader.data.ReaderBackground
import com.enderbk.materialreader.data.SettingsStore
import com.enderbk.materialreader.data.ThemeMode
import com.enderbk.materialreader.data.ZoomMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {
    val uiState = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private fun edit(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun setThemeMode(mode: ThemeMode) = edit { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = edit { it.copy(dynamicColor = enabled) }
    fun setZoomMode(mode: ZoomMode) = edit { it.copy(defaultZoomMode = mode) }
    fun setPageLayout(layout: PageLayout) = edit { it.copy(pageLayout = layout) }
    fun setKeepAwake(enabled: Boolean) = edit { it.copy(keepScreenAwake = enabled) }
    fun setRememberPosition(enabled: Boolean) = edit { it.copy(rememberReadingPosition = enabled) }
    fun setReaderBackground(background: ReaderBackground) = edit { it.copy(readerBackground = background) }
    fun setNightMode(enabled: Boolean) = edit { it.copy(nightMode = enabled) }
    fun setRoundedFont(enabled: Boolean) = edit { it.copy(roundedFont = enabled) }
    fun setAppLanguage(tag: String) = edit { it.copy(appLanguage = tag) }

    /**
     * Suspending write for flows that must persist BEFORE acting on the
     * value (e.g. applying a locale that recreates the activity on read).
     */
    suspend fun setAppLanguageSync(tag: String) {
        settings.update { it.copy(appLanguage = tag) }
    }
    fun setWelcomeSeen() = edit { it.copy(welcomeSeen = true) }
    fun setExperimentalEnabled(enabled: Boolean) = edit { it.copy(experimentalEnabled = enabled) }
    fun setFloatingNavBar(enabled: Boolean) = edit { it.copy(floatingNavBar = enabled) }
}

@Suppress("UNCHECKED_CAST")
fun settingsViewModelFactory(settings: SettingsStore): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settings) as T
        }
    }
