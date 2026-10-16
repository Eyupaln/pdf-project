package com.scanpdf.ocr.core

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** İşlem seçenekleri, ilerleme ve motor seçimi davranışları. */
class PipelineTypesTest {

    @Test
    fun `varsayilan secenekler KillerPDF davranisini yansitir`() {
        val options = OcrOptions()

        assertEquals(OcrEngineKind.TESSERACT, options.engine)
        assertEquals(listOf("tur", "eng"), options.languages)
        assertEquals(300, options.dpi)
        assertTrue(options.preprocess)
        assertTrue(options.writeTextLayer)
    }

    @Test
    fun `ilerleme orani sayfa sayisina gore hesaplanir`() {
        assertEquals(
            0.5f,
            OcrProgress(OcrStage.RECOGNIZING, pageIndex = 3, pageCount = 6).fraction,
            0.0001f,
        )
        assertEquals(
            1f,
            OcrProgress(OcrStage.DONE, pageIndex = 6, pageCount = 6).fraction,
            0.0001f,
        )
    }

    @Test
    fun `ilerleme orani sinirlari asmaz`() {
        assertEquals(
            1f,
            OcrProgress(OcrStage.SAVING, pageIndex = 99, pageCount = 6).fraction,
            0.0001f,
        )
        assertEquals(
            0f,
            OcrProgress(OcrStage.PREPARING, pageIndex = -4, pageCount = 6).fraction,
            0.0001f,
        )
    }

    @Test
    fun `sayfa sayisi sifirsa oran sifirdir`() {
        assertEquals(0f, OcrProgress(OcrStage.PREPARING, 0, 0).fraction, 0.0001f)
        assertEquals(0f, OcrProgress(OcrStage.PREPARING, 5, 0).fraction, 0.0001f)
    }

    @Test
    fun `motor kimliginden cozumleme bilinmeyen degeri Tesseract yapar`() {
        assertEquals(OcrEngineKind.MLKIT, OcrEngineKind.fromId("mlkit"))
        assertEquals(OcrEngineKind.TESSERACT, OcrEngineKind.fromId("tesseract"))
        assertEquals(OcrEngineKind.TESSERACT, OcrEngineKind.fromId("bilinmeyen"))
        assertEquals(OcrEngineKind.TESSERACT, OcrEngineKind.fromId(null))
    }

    @Test
    fun `motor etiketleri ve kimlikleri benzersizdir`() {
        val ids = OcrEngineKind.entries.map { it.id }
        val labels = OcrEngineKind.entries.map { it.label }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
    }

    @Test
    fun `sonuc ozeti sayfa metnini ve motoru tasir`() {
        val pages = listOf(
            PageOcrResult(0, 10, 10, 300f, listOf(OcrLine(listOf(OcrWord("a", Rect(), 50f)), Rect()))),
        )
        val outcome = OcrOutcome(
            documentTitle = "deneme.pdf",
            pageCount = 1,
            pages = pages,
            text = pages.joinToString("\n\n") { it.text },
            outputPdf = null,
            engineKind = OcrEngineKind.TESSERACT,
            elapsedMs = 1234L,
        )

        assertEquals("deneme.pdf", outcome.documentTitle)
        assertEquals("a", outcome.text)
        assertEquals(null, outcome.outputPdf)
        assertEquals(1234L, outcome.elapsedMs)
    }
}
