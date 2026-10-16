package com.scanpdf.ocr.text

import com.scanpdf.ocr.core.PageOcrResult

/**
 * OCR metninin dosya adı ve gövde biçimleri.
 *
 * Saf Kotlin: Android sınıfı kullanmaz, bu yüzden JVM birim testleriyle
 * doğrulanabilir. Dosyayı yazma/paylaşma sorumluluğu [TextExporter]'dadır.
 *
 * İki gövde biçimi üretilir:
 *
 *  - **Ham metin (`-metin.txt`)**: sayfa metinleri hiç değiştirilmeden, aralarına
 *    bir boş satır konarak birleştirilir. Metni başka bir araca (örneğin bir
 *    yapay zekâ sohbetine) doğrudan vermek için en sade biçim.
 *  - **Markdown (`-metin.md`)**: `# belge adı` başlığı ve (birden çok sayfa
 *    varsa) sayfa başlıkları eklenir. Sayfa metni ham kalır; **yalnızca tablo
 *    bölgeleri** (bkz. [WordLayout]) gerçek markdown tablosuna dönüştürülür,
 *    çünkü düz metinde hücre-sütun ilişkisi kaybolur ve tek bir hatalı okuma tüm
 *    hizalamayı kaydırır.
 */
object TextFormat {

    /** Android/Linux dosya adlarında sorun çıkaran karakterler ve kontrol karakterleri. */
    private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** Başlıklarda satır sonlarını ve çoklu boşlukları tek boşluğa indirger. */
    private val WHITESPACE = Regex("\\s+")

    /** Dosya adı üst sınırı (dosya sistemi sınırının çok altında). */
    private const val MAX_NAME_LENGTH = 80

    /** Markdown başlığı üst sınırı. */
    private const val MAX_TITLE_LENGTH = 120

    /** GFM tablosunda ayırıcı satır için gereken en az tire sayısı. */
    private const val MIN_TABLE_DASHES = 3

    /** Ad ya da başlık tamamen boş kalırsa kullanılır. */
    const val DEFAULT_NAME = "belge"

    /**
     * [raw] belge adından dosya sistemi için güvenli bir taban ad üretir.
     *
     * Yasak karakterler `_` olur, ad 80 karaktere kısaltılır, baş/son boşluklar
     * atılır ve sonuç boş kalırsa [DEFAULT_NAME] döner. Böylece kaydetme hiçbir
     * zaman yalnızca ad yüzünden başarısız olmaz.
     */
    fun safeFileName(raw: String): String {
        val cleaned = raw.replace(ILLEGAL_NAME_CHARS, "_").trim().take(MAX_NAME_LENGTH).trim()
        return cleaned.ifBlank { DEFAULT_NAME }
    }

    /**
     * Markdown başlığı için [raw]'ı tek satıra indirir: satır sonları ve çoklu
     * boşluklar tek boşluk olur, uzunluk sınırlanır, boşsa [DEFAULT_NAME] döner.
     */
    fun documentTitle(raw: String): String {
        val single = raw.replace(WHITESPACE, " ").trim().take(MAX_TITLE_LENGTH).trim()
        return single.ifBlank { DEFAULT_NAME }
    }

    /**
     * `.txt` gövdesi: sayfa metinleri **ham** hâlde, aralarına bir boş satır
     * konarak birleştirilir. Hiçbir başlık, numara veya işaret eklenmez.
     */
    fun rawText(pageTexts: List<String>): String =
        pageTexts.joinToString("\n\n") { it.trim() }

    /**
     * `.md` gövdesi: `# belge adı` başlığı, ardından sayfalar.
     *
     * Sayfa birden fazlaysa her sayfanın önüne `## <pageLabel>` başlığı konur;
     * tek sayfalı belgede gereksiz başlık eklenmez. Sayfa metni olduğu gibi
     * kalır, yalnızca tablo bölgeleri markdown tablosuna çevrilir.
     *
     * @param pageLabel 1 tabanlı sayfa numarasından başlık üretir (varsayılan
     *   `Sayfa N`). Uygulama, seçili dildeki kaynak metni buraya geçirir.
     */
    fun markdownText(
        title: String,
        pages: List<PageOcrResult>,
        pageLabel: (Int) -> String = { page -> "Sayfa $page" },
    ): String = buildString {
        append("# ").append(documentTitle(title)).append('\n')
        val numberedPages = pages.size > 1
        pages.forEachIndexed { index, page ->
            append('\n')
            if (numberedPages) append("## ").append(pageLabel(index + 1)).append("\n\n")
            append(pageMarkdown(page)).append('\n')
        }
    }

    /**
     * Bir sayfanın markdown gövdesi: tablo bölgeleri markdown tablosu olur,
     * kalan satırlar ham kalır.
     */
    private fun pageMarkdown(page: PageOcrResult): String {
        val lines = page.lines
        if (lines.isEmpty()) return ""
        val blocks = WordLayout.tableBlocks(lines)
        if (blocks.isEmpty()) return lines.joinToString("\n") { it.text }

        val output = StringBuilder()
        var next = 0
        for (block in blocks) {
            for (index in next until block.startLine) {
                output.append(lines[index].text).append('\n')
            }
            output.append(markdownTable(block.rows)).append('\n')
            next = block.startLine + block.lineCount
        }
        for (index in next until lines.size) {
            output.append(lines[index].text).append('\n')
        }
        return output.toString().trimEnd('\n')
    }

    /**
     * GFM tablosu: ilk satır başlık olur, sütunlar boşlukla hizalanır. Eksik
     * hücreler boş bırakılır; ayırıcı satır en az [MIN_TABLE_DASHES] tire taşır.
     */
    private fun markdownTable(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val columns = rows.maxOf { it.size }
        val widths = IntArray(columns) { 1 }
        for (row in rows) {
            for (column in 0 until columns) {
                widths[column] = maxOf(widths[column], row.getOrElse(column) { "" }.length)
            }
        }

        fun render(cells: List<String>): String = buildString {
            append('|')
            for (column in 0 until columns) {
                val cell = cells.getOrElse(column) { "" }
                append(' ').append(cell.padEnd(widths[column])).append(" |")
            }
        }

        val separator = buildString {
            append('|')
            for (column in 0 until columns) {
                append(' ').append("-".repeat(maxOf(MIN_TABLE_DASHES, widths[column]))).append(" |")
            }
        }

        return buildString {
            append(render(rows.first())).append('\n')
            append(separator).append('\n')
            for (index in 1 until rows.size) {
                append(render(rows[index])).append('\n')
            }
        }.trimEnd('\n')
    }
}
