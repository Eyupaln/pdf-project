package com.scanpdf.ocr.pdf

import com.scanpdf.ocr.core.PageOcrResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/**
 * OCR sonuçlarını sayfaya **görünmez** (aranabilir) metin katmanı olarak yazar.
 *
 * Koordinat akışı:
 *  1. Kelime kutuları, OCR görüntüsünün piksel uzayındadır (sol/üst/sağ/alt).
 *  2. Görüntü boyutu ile sayfanın görüntülenen (kırpma + döndürme uygulanmış)
 *     boyutu arasında ölçek uygulanır.
 *  3. Dönüşüm [PageGeometry] ile sayfa uzayına ters eşlenir (kırpma kutusu
 *     kaydırması dâhil).
 *
 * Ön işlemede görüntü döndürüldüyse ([PageOcrResult.appliedRotationDegrees])
 * kelimeler önce [PageOcrResult.withoutPreprocessRotation] ile geri taşınır.
 *
 * Aynı anda tek iş parçacığından kullanılmalıdır.
 */
class PdfTextLayerWriter(
    private val document: PDDocument,
    private val fontProvider: PdfFontProvider,
) {

    /**
     * [pageIndex] sayfasına [result] içindeki kelimeleri görünmez metin olarak
     * ekler. Yazı tipi yüklenemezse sessizce döner; tek bir kelime yazılamazsa
     * (glif yok) yalnızca o kelime atlanır.
     */
    fun writePage(pageIndex: Int, result: PageOcrResult) {
        val page = document.getPage(pageIndex)

        // Ön işleme döndürmesi varsa kutuları önce geri taşı.
        val source = if (result.appliedRotationDegrees != 0f) result.withoutPreprocessRotation() else result
        if (source.imageWidth <= 0 || source.imageHeight <= 0) return

        val crop = page.cropBox
        val w = crop.width.toDouble()
        val h = crop.height.toDouble()
        val ox = crop.lowerLeftX.toDouble()
        val oy = crop.lowerLeftY.toDouble()

        val rotation = PageGeometry.normalizeRotation(page.rotation)
        val dispW = PageGeometry.displayWidth(w, h, rotation)
        val dispH = PageGeometry.displayHeight(w, h, rotation)
        val kx = dispW / source.imageWidth
        val ky = dispH / source.imageHeight

        // PDPageContentStream her durumda kapatılır (use).
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { cs ->
            // NEITHER = ne dolgu ne çizim: yazı görünmez, yalnızca kopyalanabilir.
            cs.setRenderingMode(RenderingMode.NEITHER)

            val font = fontProvider.fontFor(document) ?: return

            cs.beginText()
            var currentSize = Float.NaN
            for (line in source.lines) {
                for (word in line.words) {
                    val text = sanitize(word.text)
                    if (text.isEmpty()) continue

                    val box = word.box
                    if (!box.isValid) continue

                    val dx = box.left * kx
                    val dy = box.bottom * ky // taban çizgisi = kutunun altı
                    val anchor = PageGeometry.anchor(dx, dy, w, h, rotation)
                    val size = (box.height * ky).toFloat().coerceAtLeast(MIN_FONT_SIZE)

                    try {
                        if (size != currentSize) {
                            cs.setFont(font, size)
                            currentSize = size
                        }
                        val matrix = Matrix.getRotateInstance(
                            Math.toRadians(anchor.degrees),
                            (ox + anchor.x).toFloat(),
                            (oy + anchor.y).toFloat(),
                        )
                        cs.setTextMatrix(matrix)
                        cs.showText(text)
                    } catch (e: IllegalArgumentException) {
                        // Yazı tipinde bu kod noktası için glif yok: kelimeyi atla,
                        // sayfanın kalanını yazmaya devam et.
                    }
                }
            }
            cs.endText()
        }
    }

    /** Birden çok sayfayı sırayla yazar. */
    fun writeAll(results: List<PageOcrResult>) {
        for (result in results) {
            writePage(result.pageIndex, result)
        }
    }

    /** Belgeyi [output] dosyasına kaydeder. */
    fun save(output: File) {
        document.save(output)
    }

    /** Kontrol ve görünmez karakterleri ayıklar; sonuç boşsa çağıran kelimeyi atlar. */
    private fun sanitize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            if (ch.isISOControl()) continue // \n, \r, \t ve diğer kontrol karakterleri
            val codePoint = ch.code
            // U+200B..U+200F (sıfır genişlikliler, yön işaretleri) ve U+FEFF (BOM).
            if (codePoint in 0x200B..0x200F || codePoint == 0xFEFF) continue
            sb.append(ch)
        }
        return sb.toString().trim()
    }

    companion object {
        private const val MIN_FONT_SIZE = 1f
    }
}
