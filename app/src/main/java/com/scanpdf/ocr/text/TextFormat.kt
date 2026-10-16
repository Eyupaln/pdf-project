package com.scanpdf.ocr.text

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
 *    varsa) sayfa başlıkları eklenir; sayfa metinleri yine ham kalır. Böylece
 *    sayfa sınırları görünür olur ama metnin kendisi değişmez.
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
     * `.md` gövdesi: `# belge adı` başlığı, ardından sayfa metinleri.
     *
     * Sayfa birden fazlaysa her sayfanın önüne `## <pageLabel>` başlığı konur;
     * tek sayfalı belgede gereksiz başlık eklenmez. Sayfa metinleri değiştirilmez.
     *
     * @param pageLabel 1 tabanlı sayfa numarasından başlık üretir (varsayılan
     *   `Sayfa N`). Uygulama, seçili dildeki kaynak metni buraya geçirir.
     */
    fun markdownText(
        title: String,
        pageTexts: List<String>,
        pageLabel: (Int) -> String = { page -> "Sayfa $page" },
    ): String = buildString {
        append("# ").append(documentTitle(title)).append('\n')
        val numberedPages = pageTexts.size > 1
        pageTexts.forEachIndexed { index, pageText ->
            append('\n')
            if (numberedPages) append("## ").append(pageLabel(index + 1)).append("\n\n")
            append(pageText.trim()).append('\n')
        }
    }
}
