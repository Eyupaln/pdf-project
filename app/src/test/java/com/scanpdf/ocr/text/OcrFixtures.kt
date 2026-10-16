package com.scanpdf.ocr.text

import com.scanpdf.ocr.core.Box
import com.scanpdf.ocr.core.OcrLine
import com.scanpdf.ocr.core.OcrWord
import com.scanpdf.ocr.core.PageOcrResult

/**
 * Testler için küçük OCR verisi kurucuları.
 *
 * Gerçek kutu geometrisi kullanılır (`android.graphics.Rect` yerine saf
 * [Box]), bu yüzden JVM testlerinde ölçüler gerçek değer taşır.
 */

/** Sol üst köşesi ve boyutu verilen kelime. */
internal fun wordAt(
    text: String,
    left: Int,
    top: Int,
    width: Int,
    height: Int = 40,
    confidence: Float = 90f,
): OcrWord = OcrWord(text, Box(left, top, left + width, top + height), confidence)

/** Kelimelerden satır kurar; kutu kelimelerin birleşimidir. */
internal fun lineOf(vararg words: OcrWord): OcrLine =
    OcrLine(words.toList(), Box.unionOf(words.map { it.box }) ?: Box.EMPTY)

/**
 * Izgara (tablo) satırı: hücreler [cellWidth] piksel aralıkla aynı y'de durur.
 * Gerçek cevap anahtarı satırının ölçüleri örnek alınmıştır (300 DPI).
 */
internal fun gridLine(
    cells: List<String>,
    top: Int = 0,
    cellWidth: Int = 120,
    cellBoxWidth: Int = 30,
    confidence: Float = 90f,
): OcrLine {
    val words = cells.mapIndexed { index, text ->
        wordAt(text, index * cellWidth, top, cellBoxWidth, confidence = confidence)
    }
    return lineOf(*words.toTypedArray())
}

/** Normal metin satırı: kelimeler arasında yalnızca küçük bir boşluk vardır. */
internal fun textLine(text: String, top: Int = 0, confidence: Float = 90f): OcrLine {
    val words = text.split(' ').filter { it.isNotEmpty() }
        .mapIndexed { index, value -> wordAt(value, index * 100, top, 80, confidence = confidence) }
    return lineOf(*words.toTypedArray())
}

/** Verilen satırlardan tek sayfalık sonuç üretir. */
internal fun pageOfLines(vararg lines: OcrLine): PageOcrResult = PageOcrResult(
    pageIndex = 0,
    imageWidth = 2480,
    imageHeight = 3508,
    renderDpi = 300f,
    lines = lines.toList(),
)

/** Düz metin satırlarından sayfa üretir ([textLine] kullanır). */
internal fun pageOfText(vararg texts: String): PageOcrResult =
    pageOfLines(*texts.mapIndexed { index, text -> textLine(text, top = index * 60) }.toTypedArray())

/**
 * Kelimeleri dikey örtüşmeye göre satırlara kümeler (test altyapısı).
 *
 * Gerçek motorlar (Tesseract) satır sınırlarını kendileri bildirir; bu yardımcı
 * yalnızca dış araçlardan (örneğin Tess4J) gelen kelime listelerini ürün
 * modellerine çevirmek için kullanılır.
 */
internal fun groupIntoLines(words: List<OcrWord>, minOverlap: Float = 0.5f): List<OcrLine> {
    val sorted = words.sortedWith(compareBy({ it.box.top }, { it.box.left }))
    val rows = mutableListOf<MutableList<OcrWord>>()
    for (word in sorted) {
        val row = rows.lastOrNull()
        val fits = row != null && row.any { it.box.verticalOverlap(word.box) >= minOverlap }
        if (fits) row!!.add(word) else rows.add(mutableListOf(word))
    }
    return rows.map { row ->
        val ordered = row.sortedBy { it.box.left }
        OcrLine(ordered, Box.unionOf(ordered.map { it.box }) ?: Box.EMPTY)
    }
}
