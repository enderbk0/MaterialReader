package com.enderbk.materialreader

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

class MainActivity : ComponentActivity() {

    private val documents by lazy { DataStoreDocumentStore(applicationContext) }
    private val settings by lazy { DataStoreSettingsStore(applicationContext) }
    private val meta by lazy { SystemDocumentMetaSource(applicationContext) }
    private val backend by lazy { SystemReaderBackend(applicationContext) }

    /** Content URI shared/opened into the app, consumed once by the nav host. */
    private var externalUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
