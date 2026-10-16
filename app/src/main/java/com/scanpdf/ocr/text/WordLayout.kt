package com.scanpdf.ocr.text

import com.scanpdf.ocr.core.OcrLine
import com.scanpdf.ocr.core.OcrWord
import kotlin.math.roundToInt

/**
 * Kelime kutularından tablo yapısını çıkarma (saf Kotlin, Android'sız).
 *
 * OCR satırları kelimeleri yalnızca tek boşlukla birleştirir; bu yüzden cevap
 * anahtarı gibi ızgara satırları sütun sınırlarını kaybeder ve tek bir hatalı
 * hücre tüm hizalamayı kaydırır. Burada hücreler **kutulardan** yeniden kurulur:
 * iki kelime arasındaki yatay boşluk, ortanca karakter genişliğine göre
 * belirgin biçimde büyükse yeni hücre başlar.
 */
object WordLayout {

    /** Tablo satırı sayılmak için gereken en az hücre sayısı. */
    const val MIN_CELLS = 4

    /** Bu uzunluktan kısa hücreler "ızgara hücresi" sayılır (ör. "12", "C"). */
    const val SHORT_CELL_CHARS = 3

    /** Satırın tablo sayılması için gereken en az kısa hücre oranı. */
    const val SHORT_CELL_RATIO = 0.7f

    /** Hücre ayırma eşiği: ortanca karakter genişliğinin bu katı. */
    private const val GAP_CHAR_FACTOR = 2.5

    /** Hücre ayırma eşiğinin alt sınırı (piksel). */
    private const val MIN_GAP_PX = 12

    private fun sortedWords(line: OcrLine): List<OcrWord> = line.words.sortedBy { it.box.left }

    /**
     * Satırın hücre ayırma eşiği (piksel): ortanca karakter genişliğinin
     * [GAP_CHAR_FACTOR] katı. Normal metinde kelime araları bu eşiğin altında
     * kalır, ızgara hücrelerinde boşluk eşiği aşar.
     */
    fun gapThreshold(line: OcrLine): Int {
        val widths = sortedWords(line)
            .mapNotNull { word ->
                val chars = word.text.count { !it.isWhitespace() }
                if (chars <= 0 || word.box.width <= 0) null else word.box.width.toFloat() / chars
            }
            .sorted()
        if (widths.isEmpty()) return MIN_GAP_PX
        val median = widths[widths.size / 2]
        return maxOf(MIN_GAP_PX, (median * GAP_CHAR_FACTOR).roundToInt())
    }

    /** Satırı hücrelere böler (kelimeler x'e göre sıralanır). */
    fun splitCells(line: OcrLine, threshold: Int = gapThreshold(line)): List<List<OcrWord>> {
        val words = sortedWords(line)
        if (words.isEmpty()) return emptyList()
        val cells = mutableListOf<MutableList<OcrWord>>()
        var current = mutableListOf(words.first())
        for (index in 1 until words.size) {
            val gap = words[index - 1].box.horizontalGap(words[index].box)
            if (gap > threshold) {
                cells += current
                current = mutableListOf(words[index])
            } else {
                current += words[index]
            }
        }
        cells += current
        return cells
    }

    /** Hücre metni (kelimeler tek boşlukla birleştirilir). */
    fun cellText(cell: List<OcrWord>): String = cell.joinToString(" ") { it.text }

    /** Satır, ızgara/tablo satırı gibi mi görünüyor? */
    fun isTableRow(line: OcrLine, threshold: Int = gapThreshold(line)): Boolean {
        val cells = splitCells(line, threshold)
        if (cells.size < MIN_CELLS) return false
        val short = cells.count { cellText(it).trim().length <= SHORT_CELL_CHARS }
        return short.toFloat() / cells.size >= SHORT_CELL_RATIO
    }

    /**
     * Sayfa tablo/ızgara sayfası gibi mi görünüyor? En az iki ızgara satırı
     * gerekir; tek bir kısa satır (örneğin tarih) tablo sayılmaz.
     */
    fun looksTabular(lines: List<OcrLine>): Boolean =
        lines.count { isTableRow(it) } >= 2

    /**
     * Okunan içeriğin verimi: karakter sayısı × güven (0..1) toplamı. İki OCR
     * denemesini karşılaştırmak için kullanılır; daha yüksek daha iyi.
     */
    fun yieldScore(lines: List<OcrLine>): Double =
        lines.sumOf { line ->
            line.words.sumOf { word ->
                word.text.count { !it.isWhitespace() } * (word.confidence / 100.0).coerceIn(0.0, 1.0)
            }
        }

    /** Ardışık ızgara satırlarından oluşan tablo bölgesi. */
    data class TableBlock(
        /** Bloğun başladığı satır indeksi. */
        val startLine: Int,
        /** Bloktaki satır sayısı. */
        val lineCount: Int,
        /** Hücre metinleri; tüm satırlar eşit sütun sayısına doldurulur. */
        val rows: List<List<String>>,
    )

    /**
     * Ardışık ızgara satırlarını tablo bölgesi olarak döner. En az iki satır
     * gerekir (tek satır tablo oluşturmaz).
     */
    fun tableBlocks(lines: List<OcrLine>): List<TableBlock> {
        val blocks = mutableListOf<TableBlock>()
        var start = -1
        var rows = mutableListOf<List<String>>()

        fun flush(endExclusive: Int) {
            if (start >= 0 && rows.size >= 2) {
                val columns = rows.maxOf { it.size }
                blocks += TableBlock(
                    startLine = start,
                    lineCount = endExclusive - start,
                    rows = rows.map { row -> row.padTo(columns) },
                )
            }
            rows = mutableListOf()
        }

        lines.forEachIndexed { index, line ->
            if (isTableRow(line)) {
                if (start < 0) start = index
                rows += splitCells(line).map { cellText(it).trim() }
            } else if (start >= 0) {
                flush(index)
                start = -1
            }
        }
        if (start >= 0) flush(lines.size)
        return blocks
    }

    private fun List<String>.padTo(columns: Int): List<String> =
        if (size >= columns) this else this + List(columns - size) { "" }
}
