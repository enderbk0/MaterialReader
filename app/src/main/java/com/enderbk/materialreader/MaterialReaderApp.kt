package com.enderbk.materialreader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application class. Initializes the PdfBox-Android asset loader once so the
 * text layer (search / text extraction / link annotations) works offline.
 * No network, analytics, or account initialization happens here — on purpose.
 */
class MaterialReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
