package com.enderbk.materialreader.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.io.MemoryUsageSetting

/**
 * Offline text layer powered by PdfBox-Android (Apache 2.0).
 *
 * The platform PdfRenderer rasterizes pages but exposes no text, so search,
 * copyable page text, and link annotations come from here. Work runs on
 * Dispatchers.IO, documents are closed promptly, and mixed memory/file
 * loading keeps RAM bounded for large PDFs. Everything stays on-device.
 */
data class TextHit(val pageIndex: Int, val snippet: String)

data class PageLink(val pageIndex: Int, val uri: String, val label: String?)

private const val MAX_SEARCH_PAGES = 2000
private const val MAX_HITS = 300
private const val SNIPPET_RADIUS = 72

private fun loadDocument(context: Context, uri: Uri): PDDocument {
    // Asset loader is initialized in MaterialReaderApp; init is idempotent.
    runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    val input = context.contentResolver.openInputStream(uri)
        ?: throw PdfOpenException(PdfOpenFailure.FileNotFound)
    return try {
        input.use { stream ->
            PDDocument.load(
                stream,
                MemoryUsageSetting.setupMixed(32L * 1024L * 1024L)
            )
        }
    } catch (e: PdfOpenException) {
        throw e
    } catch (e: Exception) {
        throw PdfOpenException(mapOpenError(e), e)
    }
}

private fun pageText(doc: PDDocument, pageIndex: Int): String {
    val stripper = PDFTextStripper()
    stripper.startPage = pageIndex + 1 // PDFBox pages are 1-based
    stripper.endPage = pageIndex + 1
    return stripper.getText(doc)
}

/** Case-insensitive search across pages. Returns hits ordered by page. */
suspend fun searchPdfText(context: Context, uri: Uri, query: String): List<TextHit> =
    withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val doc = loadDocument(context, uri)
        try {
            if (doc.isEncrypted) throw PdfOpenException(PdfOpenFailure.PasswordProtected)
            val pages = minOf(doc.numberOfPages, MAX_SEARCH_PAGES)
            val hits = ArrayList<TextHit>()
            for (i in 0 until pages) {
                val text = runCatching { pageText(doc, i) }.getOrDefault("")
                if (text.isEmpty()) continue
                var from = 0
                while (hits.size < MAX_HITS) {
                    val found = text.indexOf(q, from, ignoreCase = true)
                    if (found == -1) break
                    hits += TextHit(i, snippetAround(text, found, q.length))
                    from = found + q.length
                }
                if (hits.size >= MAX_HITS) break
            }
            hits
        } finally {
            runCatching { doc.close() }
        }
    }

/** Extracts selectable/copyable text for a single page (used by the text sheet). */
suspend fun extractPageText(context: Context, uri: Uri, pageIndex: Int): String =
    withContext(Dispatchers.IO) {
        val doc = loadDocument(context, uri)
        try {
            if (doc.isEncrypted) throw PdfOpenException(PdfOpenFailure.PasswordProtected)
            if (pageIndex !in 0 until doc.numberOfPages) return@withContext ""
            runCatching { pageText(doc, pageIndex).trim() }.getOrDefault("")
        } finally {
            runCatching { doc.close() }
        }
    }

/**
 * External (http/https) link annotations on a page, deduplicated.
 * Internal go-to destinations are skipped: tapping a result opens the URL in
 * the user's browser via a system intent (explicit user action).
 */
suspend fun extractPageLinks(context: Context, uri: Uri, pageIndex: Int): List<PageLink> =
    withContext(Dispatchers.IO) {
        val doc = loadDocument(context, uri)
        try {
            if (doc.isEncrypted || pageIndex !in 0 until doc.numberOfPages) return@withContext emptyList()
            val page = doc.getPage(pageIndex)
            val seen = LinkedHashSet<String>()
            val out = ArrayList<PageLink>()
            for (annotation in page.annotations) {
                if (annotation !is PDAnnotationLink) continue
                val uriAction = annotation.action as? PDActionURI ?: continue
                val target = uriAction.getURI()?.trim().orEmpty()
                if ((target.startsWith("http://") || target.startsWith("https://")) && seen.add(target)) {
                    out += PageLink(pageIndex, target, annotation.contents?.takeIf { it.isNotBlank() })
                }
            }
            out
        } catch (e: PdfOpenException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { doc.close() }
        }
    }

/** Collapses whitespace and returns a window of text around [matchAt]. */
fun snippetAround(text: String, matchAt: Int, matchLength: Int): String {
    val flat = text.replace(Regex("\\s+"), " ")
    val start = (matchAt - SNIPPET_RADIUS).coerceAtLeast(0)
    val end = (matchAt + matchLength + SNIPPET_RADIUS).coerceAtMost(flat.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < flat.length) "…" else ""
    return prefix + flat.substring(start, end).trim() + suffix
}
