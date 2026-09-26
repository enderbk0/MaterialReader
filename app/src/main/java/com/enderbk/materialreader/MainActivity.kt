package com.enderbk.materialreader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.enderbk.materialreader.data.DataStoreDocumentStore
import com.enderbk.materialreader.data.DataStoreSettingsStore
import com.enderbk.materialreader.library.SystemDocumentMetaSource
import com.enderbk.materialreader.navigation.MaterialReaderApp
import com.enderbk.materialreader.pdf.SystemReaderBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val documents by lazy { DataStoreDocumentStore(applicationContext) }
    private val settings by lazy { DataStoreSettingsStore(applicationContext) }
    private val meta by lazy { SystemDocumentMetaSource(applicationContext) }
    private val backend by lazy { SystemReaderBackend(applicationContext) }

    /** Content URI shared/opened into the app, consumed once by the nav host. */
    private var externalUri by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context?) {
        // Pre-33 reliability: AppCompat's locale backport does not always
        // re-wrap a plain ComponentActivity, so the stored locale is applied
        // to the base context as well. Blank (system default) passes through.
        val base = newBase ?: return super.attachBaseContext(newBase)
        super.attachBaseContext(wrapForLanguage(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply the stored per-app language before any UI exists. A single
        // blocking DataStore read (memory-cached after first load) so the
        // first frame already uses the right locale. Empty = system default.
        val languageTag = runCatching {
            runBlocking { settings.settings.first().appLanguage }
        }.getOrDefault("")
        com.enderbk.materialreader.util.applyAppLanguage(this, languageTag)
        if (savedInstanceState == null) {
            externalUri = intent.extractPdfUri()?.toString()
        }
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Truly transparent system navigation bar (no contrast scrim) so
            // the app draws edge-to-edge behind it. Safe because every screen
            // keeps an opaque Material bar above the system bar area.
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            MaterialReaderApp(
                documents = documents,
                settings = settings,
                meta = meta,
                backend = backend,
                externalUri = externalUri,
                onConsumeExternalUri = { externalUri = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.extractPdfUri()?.toString()?.let { externalUri = it }
    }

    /**
     * Accepts PDFs via VIEW (file managers, attachments, browsers) and SEND
     * (share sheets). Only content URIs with a PDF mime type — or a .pdf name
     * hint when the type is missing — are accepted.
     */
    private fun Intent.extractPdfUri(): Uri? {
        return when (action) {
            Intent.ACTION_VIEW -> data?.takeIf { it.looksLikePdf() }
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                getParcelableExtra<Uri?>(Intent.EXTRA_STREAM)?.takeIf { it.looksLikePdf() }
                    ?: getParcelableExtra<Uri?>(Intent.EXTRA_STREAM)
            }
            else -> null
        }
    }

    private fun Uri.looksLikePdf(): Boolean {
        val type = runCatching { contentResolver.getType(this) }.getOrNull()
        if (type == "application/pdf") return true
        if (type == "application/octet-stream") {
            val name = lastPathSegment.orEmpty()
            if (name.endsWith(".pdf", ignoreCase = true)) return true
        }
        // Some senders omit the mime type entirely; accept and let the reader
        // report a readable error if it is not a PDF.
        if (type == null) return true
        return false
    }

    companion object {
        /**
         * Wraps [base] with the stored per-app language, or returns it
         * untouched for system default. Used by [attachBaseContext] so the
         * locale holds on every API level.
         */
        fun wrapForLanguage(base: Context): Context {
            val tag = runCatching {
                kotlinx.coroutines.runBlocking {
                    DataStoreSettingsStore(base)
                        .settings
                        .first()
                        .appLanguage
                }
            }.getOrDefault("")
            if (tag.isBlank()) return base
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocale(java.util.Locale.forLanguageTag(tag))
            return base.createConfigurationContext(config)
        }
    }
}
