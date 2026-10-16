package com.scanpdf.ocr.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Rasterize edilmiş bir sayfa ve gerçekte kullanılan çözünürlük.
 *
 * [PdfRasterizer] çok büyük görüntülerde uzun kenarı sınırlamak için görüntüyü
 * küçültür; bu durumda [effectiveDpi] istenen DPI'dan küçük olur. Koordinat
 * eşlemesi yalnızca [bitmap] boyutunu kullandığı için bu değer çağıranın
 * bilgilendirilmesi/raporlanması içindir.
 */
data class RenderedPage(val bitmap: Bitmap, val effectiveDpi: Float)

/**
 * Bir [PDDocument] sayfasını bitmap'e çevirir.
 *
 * Aynı anda tek iş parçacığından kullanılmak üzere tasarlanmıştır (PDFRenderer
 * iş parçacığı güvenli değildir). [PDFRenderer] sayfa başına yeniden kurulmaz.
 */
class PdfRasterizer(private val document: PDDocument) {

    /** Belgedeki sayfa sayısı. */
    val pageCount: Int get() = document.numberOfPages

    // Pahalı bir nesne; belge başına tek kez kurulur.
    private val renderer: PDFRenderer by lazy { PDFRenderer(document) }

    /** [pageIndex] sayfasını [dpi] çözünürlükte bitmap olarak döner. */
    fun render(pageIndex: Int, dpi: Int): Bitmap = renderWithScale(pageIndex, dpi).bitmap

    /**
     * [pageIndex] sayfasını [dpi] isteğiyle çizer ve gerekirse küçültülmüş
     * sonucu gerçek çözünürlüğüyle birlikte döner.
     */
    fun renderWithScale(pageIndex: Int, dpi: Int): RenderedPage {
        val requestedDpi = dpi.coerceAtLeast(MIN_DPI)
        val rendered = renderer.renderImageWithDPI(pageIndex, requestedDpi.toFloat(), ImageType.RGB)

        val longestEdge = max(rendered.width, rendered.height)
        if (longestEdge <= MAX_LONG_EDGE) {
            return RenderedPage(rendered, requestedDpi.toFloat())
        }

        // Aşırı bellek kullanımını önle: uzun kenarı sınıra indir, en-boy korunur.
        val ratio = MAX_LONG_EDGE.toFloat() / longestEdge
        val targetWidth = (rendered.width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (rendered.height * ratio).roundToInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(rendered, targetWidth, targetHeight, true)
        if (scaled !== rendered) {
            rendered.recycle()
        }

        val newLongestEdge = max(targetWidth, targetHeight).toFloat()
        val effectiveDpi = requestedDpi * (newLongestEdge / longestEdge)
        return RenderedPage(scaled, effectiveDpi)
    }

    companion object {
        /** Üretilen görüntünün uzun kenarı için üst sınır (piksel). */
        const val MAX_LONG_EDGE = 6000

        private const val MIN_DPI = 1
    }
}
