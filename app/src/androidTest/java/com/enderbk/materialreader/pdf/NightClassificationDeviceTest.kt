package com.enderbk.materialreader.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-PDF classification tests (run on device): synthetic documents built
 * with PdfBox-Android itself — text, photos, full-page scans, OCR layers,
 * rotation, multiple images, vector charts, nested Form XObjects, mixed
 * pages — analyzed through the production [pageImageRegionsBlocking] path.
 *
 * For every case: normal rendering is untouched by construction (these tests
 * assert the *night* decision), photos must survive, and full-page scans
 * must actually go dark.
 */
@RunWith(AndroidJUnit4::class)
class NightClassificationDeviceTest {

    private fun jpegBytes(w: Int, h: Int, baseColor: Int, blockColor: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(baseColor)
        val paint = Paint().apply { color = blockColor }
        canvas.drawRect(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f, paint)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun photo(doc: PDDocument, w: Int = 400, h: Int = 300): PDImageXObject =
        PDImageXObject.createFromByteArray(
            doc,
            jpegBytes(w, h, 0xFF3A6EA5.toInt(), 0xFFC0504D.toInt()),
            "photo.jpg"
        )

    private fun PDPageContentStream.paragraph(text: String, y: Float = 700f) {
        beginText()
        setFont(PDType1Font.HELVETICA, 12f)
        newLineAtOffset(72f, y)
        showText(text)
        endText()
    }

    @Test
    fun textOnlyInverts() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { it.paragraph("Hello, night.") }

        val out = pageImageRegionsBlocking(doc, 0)
        assertEquals(NightPageMode.INVERT_ALL, out.mode)
        assertTrue(out.rects.isEmpty())
        doc.close()
    }

    @Test
    fun textPlusSmallPhotoPreserved() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        val img = photo(doc, 200, 150)
        PDPageContentStream(doc, page).use {
            it.paragraph("A small photo below.")
            it.drawImage(img, 100f, 500f, 160f, 120f)
        }

        val out = pageImageRegionsBlocking(doc, 0)
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        assertEquals(1, out.rects.size)
        doc.close()
    }

    @Test
    fun textPlusLargePhotoPreserved() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        val img = photo(doc, 1200, 900)
        PDPageContentStream(doc, page).use {
            it.paragraph("A large photo below.")
            it.drawImage(img, 56f, 100f, 500f, 375f)
        }

        val out = pageImageRegionsBlocking(doc, 0)
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        assertEquals(1, out.rects.size)
        doc.close()
    }

    @Test
    fun fullPageScanDims() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        val img = photo(doc, 600, 780)
        PDPageContentStream(doc, page).use {
            // The giant image IS the page: no text at all.
            it.drawImage(img, 0f, 0f, 612f, 792f)
        }

        val out = pageImageRegionsBlocking(doc, 0)
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        assertEquals(1, out.rects.size)
        doc.close()
    }

    @Test
    fun scannedPageWithOcrLayerStillDims() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        val img = photo(doc, 600, 780)
        PDPageContentStream(doc, page).use {
            it.drawImage(img, 0f, 0f, 612f, 792f)
            it.paragraph("Invisible OCR text over the scan.")
        }

        val out = pageImageRegionsBlocking(doc, 0)
        // OCR text must not reclassify the scan as a photo page.
        assertEquals(NightPageMode.DIM, out.mode)
        doc.close()
    }

    @Test
    fun rotatedScanDims() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        page.rotation = 90
        doc.addPage(page)
        val img = photo(doc, 600, 780)
        PDPageContentStream(doc, page).use {
            it.drawImage(img, 0f, 0f, 612f, 792f)
        }

        val out = pageImageRegionsBlocking(doc, 0)
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        doc.close()
    }

    @Test
    fun multiplePhotosPreserved() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            cs.paragraph("Three photos.")
            cs.drawImage(photo(doc, 200, 150), 40f, 500f, 160f, 120f)
            cs.drawImage(photo(doc, 200, 150), 220f, 500f, 160f, 120f)
            cs.drawImage(photo(doc, 200, 150), 400f, 500f, 160f, 120f)
        }

        val out = pageImageRegionsBlocking(doc, 0)
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        assertEquals(3, out.rects.size)
        doc.close()
    }

    @Test
    fun nearFullPageImageDims() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        val img = photo(doc, 600, 760)
        PDPageContentStream(doc, page).use {
            // 95%+ coverage with a caption: effectively the page.
            it.drawImage(img, 0f, 0f, 590f, 760f)
            it.paragraph("Caption.", y = 40f)
        }

        val out = pageImageRegionsBlocking(doc, 0)
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        doc.close()
    }

    @Test
    fun vectorChartDims() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            cs.paragraph("Quarterly chart.")
            repeat(70) { i ->
                cs.addRect(72f + (i % 10) * 48f, 500f - (i / 10) * 40f, 40f, 32f)
                cs.fill()
            }
        }

        val out = pageImageRegionsBlocking(doc, 0)
        // Vector artwork cannot be masked out like raster regions: dim it so
        // chart hues survive instead of being inverted.
        assertEquals(NightPageMode.DIM, out.mode)
        doc.close()
    }

    @Test
    fun formNestedImagePreserved() {
        val doc = PDDocument()
        val page = PDPage(PDRectangle(612f, 792f))
        doc.addPage(page)
        val img = photo(doc, 200, 150)

        val form = PDFormXObject(doc)
        form.bBox = PDRectangle(0f, 0f, 200f, 150f)
        val formResources = com.tom_roush.pdfbox.pdmodel.PDResources()
        formResources.put(COSName.getPDFName("Im0"), img)
        form.resources = formResources
        val formStream = form.cosObject as com.tom_roush.pdfbox.cos.COSStream
        formStream.createOutputStream().use {
            it.write("q 200 0 0 150 0 0 cm /Im0 Do Q\n".toByteArray())
        }

        PDPageContentStream(doc, page).use { cs ->
            cs.paragraph("Logo in a form below.")
            cs.saveGraphicsState()
            cs.transform(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(100f, 400f))
            cs.drawForm(form)
            cs.restoreGraphicsState()
        }

        val out = pageImageRegionsBlocking(doc, 0)
        assertEquals(NightPageMode.INVERT_WITH_DIMMED_IMAGES, out.mode)
        assertEquals(1, out.rects.size)
        doc.close()
    }

    @Test
    fun mixedPagesClassifyIndependently() {
        val doc = PDDocument()
        val textPage = PDPage(PDRectangle(612f, 792f))
        doc.addPage(textPage)
        PDPageContentStream(doc, textPage).use { it.paragraph("Plain text page.") }
        val scanPage = PDPage(PDRectangle(612f, 792f))
        doc.addPage(scanPage)
        PDPageContentStream(doc, scanPage).use {
            it.drawImage(photo(doc, 600, 780), 0f, 0f, 612f, 792f)
        }

        assertEquals(NightPageMode.INVERT_ALL, pageImageRegionsBlocking(doc, 0).mode)
        assertEquals(
            NightPageMode.INVERT_WITH_DIMMED_IMAGES,
            pageImageRegionsBlocking(doc, 1).mode
        )
        doc.close()
    }
}
