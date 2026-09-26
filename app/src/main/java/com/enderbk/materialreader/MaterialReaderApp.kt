package com.enderbk.materialreader

import android.app.Application
import android.content.res.Configuration
import com.enderbk.materialreader.util.restartApp
import com.enderbk.materialreader.util.shouldFollowSystemLocale
import com.enderbk.materialreader.util.systemPrimaryLanguage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application class. Initializes the PdfBox-Android asset loader once so the
 * text layer (search / text extraction / link annotations) works offline.
 * No network, analytics, or account initialization happens here — on purpose.
 *
 * Also watches for system-side language changes (Android Settings → App info
 * → Language): a stale in-app override would otherwise re-apply on every
 * start and clobber the system choice, so a detected divergence clears the
 * override and restarts into the system language automatically.
 */
class MaterialReaderApp : Application() {

    private var lastSystemLanguage: String? = null

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        lastSystemLanguage = systemPrimaryLanguage(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val systemLanguage = systemPrimaryLanguage(newConfig)
        if (systemLanguage.isBlank() || systemLanguage == lastSystemLanguage) return
        lastSystemLanguage = systemLanguage
        val stored = runCatching {
            runBlocking {
                com.enderbk.materialreader.data.DataStoreSettingsStore(this@MaterialReaderApp)
                    .settings
                    .first()
                    .appLanguage
            }
        }.getOrDefault("")
        if (shouldFollowSystemLocale(stored, systemLanguage)) {
            runCatching {
                runBlocking {
                    com.enderbk.materialreader.data.DataStoreSettingsStore(this@MaterialReaderApp)
                        .update { it.copy(appLanguage = "") }
                }
            }
            restartApp(this)
        }
    }
}
