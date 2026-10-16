package com.scanpdf.ocr.core

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Çekirdek model testleri.
 *
 * Not: JVM birim testlerinde `android.graphics.Rect` bir "stub"tur; alanları
 * gerçek değer taşımaz (`testOptions.unitTests.isReturnDefaultValues = true`).
 * Bu nedenle burada yalnızca kutu değerlerine bağlı OLMAYAN davranışlar
 * doğrulanır: metin birleştirme, güven ortalaması ve döndürme kısayolu.
 */
class ModelsTest {

    private fun word(text: String, confidence: Float = 90f) =
        OcrWord(text = text, box = Rect(0, 0, 10, 10), confidence = confidence)

    @Test
    fun `OcrLine kelimeleri tek boslukla birlestirir`() {
        val line = OcrLine(words = listOf(word("Yedi"), word("İklim"), word("Matematik")), box = Rect())

        assertEquals("Yedi İklim Matematik", line.text)
    }

    @Test
    fun `PageOcrResult sayfalari satir sonuyla birlestirir`() {
        val result = PageOcrResult(
            pageIndex = 0,
            imageWidth = 100,
            imageHeight = 200,
            renderDpi = 300f,
            lines = listOf(
                OcrLine(listOf(word("birinci"), word("satır")), Rect()),
                OcrLine(listOf(word("ikinci"), word("satır")), Rect()),
            ),
        )

        assertEquals("birinci satır\nikinci satır", result.text)
        assertEquals(4, result.words.size)
    }

    @Test
    fun `meanConfidence kelime guvenlerinin ortalamasidir`() {
        val result = PageOcrResult(
            pageIndex = 0,
            imageWidth = 10,
            imageHeight = 10,
            renderDpi = 300f,
            lines = listOf(OcrLine(listOf(word("a", 80f), word("b", 100f)), Rect())),
        )

        assertEquals(90f, result.meanConfidence, 0.001f)
    }

    @Test
    fun `kelime yoksa guven sifirdir`() {
        val result = PageOcrResult(0, 10, 10, 300f, emptyList())

        assertEquals(0f, result.meanConfidence, 0.001f)
        assertEquals("", result.text)
    }

    @Test
    fun `donme yoksa ayni nesne doner`() {
        val result = PageOcrResult(0, 10, 10, 300f, listOf(OcrLine(listOf(word("türkçe")), Rect())))

        assertSame(result, result.withoutPreprocessRotation())
    }

    @Test
    fun `sayfa sonucu donme bilgisini tasir`() {
        val result = PageOcrResult(3, 10, 10, 200f, emptyList(), appliedRotationDegrees = 1.5f)

        assertEquals(3, result.pageIndex)
        assertEquals(1.5f, result.appliedRotationDegrees, 0.001f)
        assertEquals(200f, result.renderDpi, 0.001f)
    }

    @Test
    fun `OcrStage etiketleri bos degildir ve DONE son durumdur`() {
        OcrStage.entries.forEach { assertTrue(it.label.isNotBlank()) }

        assertTrue(OcrStage.DONE.isTerminal)
        assertEquals(1, OcrStage.entries.count { it.isTerminal })
        assertEquals(OcrStage.DONE, OcrStage.entries.last())
    }
}
