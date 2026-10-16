package com.scanpdf.ocr.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dosya adı ve metin gövdesi biçimleri (saf Kotlin, Android yok).
 *
 * Beklenen değerler tam metin karşılaştırmasıyla doğrulanır; böylece `.txt` ve
 * `.md` çıktılarının içeriği birebir sabitlenir.
 */
class TextFormatTest {

    // ---------------------------------------------------------------- ad ---

    @Test
    fun `yasak karakterler alt cizgiye cevrilir`() {
        assertEquals("a_b_c_d_e_f_g_h", TextFormat.safeFileName("a/b\\c:d*e?f<g>h"))
        assertEquals("_tirnak_ ve _boru_", TextFormat.safeFileName("\"tirnak\" ve |boru|"))
    }

    @Test
    fun `turkce karakterler ve bosluk korunur`() {
        assertEquals("Türkçe Şğ İı özet.pdf", TextFormat.safeFileName("Türkçe Şğ İı özet.pdf"))
    }

    @Test
    fun `kontrol karakterleri temizlenir`() {
        assertEquals("satir_sonu", TextFormat.safeFileName("satir\nsonu"))
        assertEquals("sekme_karakteri", TextFormat.safeFileName("sekme\tkarakteri"))
    }

    @Test
    fun `bos ad varsayilana duser`() {
        assertEquals(TextFormat.DEFAULT_NAME, TextFormat.safeFileName(""))
        assertEquals(TextFormat.DEFAULT_NAME, TextFormat.safeFileName("   "))
    }

    @Test
    fun `yalnizca yasak karakterden olusan ad alt cizgiye doner`() {
        // "///" boş sayılmaz: karakter sayısı korunur, ad yine geçerlidir.
        assertEquals("___", TextFormat.safeFileName("///"))
    }

    @Test
    fun `ad seksen karaktere kisaltilir`() {
        val uzun = "a".repeat(200)
        val ad = TextFormat.safeFileName(uzun)
        assertEquals(80, ad.length)
        assertTrue(ad.all { it == 'a' })
    }

    // ------------------------------------------------------------- baslik ---

    @Test
    fun `baslik tek satira indirilir`() {
        assertEquals("Çok satırlı belge adı", TextFormat.documentTitle("  Çok\n  satırlı\t belge   adı  "))
        assertEquals(TextFormat.DEFAULT_NAME, TextFormat.documentTitle("   "))
    }

    // --------------------------------------------------------------- txt ---

    @Test
    fun `txt sayfa metinlerini ham halde birlestirir`() {
        assertEquals("bir\n\niki\n\nüç", TextFormat.rawText(listOf("bir", "iki", "üç")))
    }

    @Test
    fun `txt sayfa kenarlarindaki bosluklari kirpar`() {
        assertEquals("bir\n\niki", TextFormat.rawText(listOf("  bir  \n", "\n  iki ")))
    }

    @Test
    fun `txt hicbir baslik eklemez`() {
        val metin = TextFormat.rawText(listOf("ilk sayfa", "ikinci sayfa"))
        assertFalse(metin.contains("#"))
        assertFalse(metin.contains("Sayfa 1"))
    }

    @Test
    fun `txt sayfa yoksa bostur`() {
        assertEquals("", TextFormat.rawText(emptyList()))
    }

    // ---------------------------------------------------------------- md ---

    @Test
    fun `md basligi ve tek sayfa metnini yazar`() {
        assertEquals("# Belge\n\nmetin\n", TextFormat.markdownText("Belge", listOf(pageOfText("metin"))))
    }

    @Test
    fun `md tek sayfada sayfa basligi eklemez`() {
        val md = TextFormat.markdownText("Belge", listOf(pageOfText("metin")))
        assertFalse(md.contains("## "))
    }

    @Test
    fun `md cok sayfada numarali baslik ekler`() {
        assertEquals(
            "# Belge\n\n## Sayfa 1\n\nilk\n\n## Sayfa 2\n\nikinci\n",
            TextFormat.markdownText("Belge", listOf(pageOfText("ilk"), pageOfText("ikinci"))),
        )
    }

    @Test
    fun `md sayfa etiketi disaridan verilebilir`() {
        val md = TextFormat.markdownText("Belge", listOf(pageOfText("a"), pageOfText("b"))) { "Page $it" }
        assertTrue(md.contains("## Page 1"))
        assertTrue(md.contains("## Page 2"))
        assertFalse(md.contains("Sayfa"))
    }

    @Test
    fun `md sayfa numaralari birden baslar`() {
        val md = TextFormat.markdownText("B", listOf(pageOfText("a"), pageOfText("b"), pageOfText("c")))
        assertTrue(md.indexOf("## Sayfa 1") < md.indexOf("## Sayfa 2"))
        assertTrue(md.indexOf("## Sayfa 2") < md.indexOf("## Sayfa 3"))
    }

    @Test
    fun `md cok satirli basligi tek satira indirir`() {
        val md = TextFormat.markdownText("A\nB", listOf(pageOfText("x")))
        assertTrue(md, md.startsWith("# A B\n"))
    }

    @Test
    fun `md sayfa satirlarini degistirmez`() {
        val page = pageOfText("girinti korunur", "ve boş satırlar")
        val md = TextFormat.markdownText("Belge", listOf(page))

        assertTrue(md.contains("girinti korunur"))
        assertTrue(md.contains("ve boş satırlar"))
    }

    @Test
    fun `md sayfa yoksa yalnizca baslik yazar`() {
        assertEquals("# Belge\n", TextFormat.markdownText("Belge", emptyList()))
    }

    @Test
    fun `md cevap anahtari tablosunu markdown tablosu yapar`() {
        val answers = listOf("C", "C", "E", "B", "C", "A", "E", "C", "E", "A", "C", "A", "E", "E", "D", "B", "C", "D")
        val page = pageOfLines(
            textLine("CEVAP ANAHTARI", top = 0),
            gridLine((1..18).map { it.toString() }, top = 60),
            gridLine(answers, top = 140),
        )

        val md = TextFormat.markdownText("Belge", listOf(page))

        // Başlık düz satır olarak kalır.
        assertTrue(md, md.contains("CEVAP ANAHTARI"))
        // Izgara satırları markdown tablosuna dönüşür.
        assertEquals((1..18).map { it.toString() }, markdownRow(md, firstCell = "1"))
        assertEquals(answers, markdownRow(md, firstCell = "C"))
        assertTrue(md, md.lines().any { it.startsWith("| ---") })
    }

    @Test
    fun `txt tablo satirlarini ham birakir`() {
        val page = pageOfLines(
            gridLine(listOf("1", "2", "3", "4", "5")),
            gridLine(listOf("C", "C", "E", "B", "C"), top = 80),
        )

        assertEquals("1 2 3 4 5\nC C E B C", TextFormat.rawText(listOf(page.text)))
    }

    /** Markdown gövdesinde ilk hücresi [firstCell] olan tablo satırının hücrelerini döner. */
    private fun markdownRow(markdown: String, firstCell: String): List<String> {
        val row = markdown.lines().firstOrNull { line ->
            line.startsWith("|") && line.split('|').getOrNull(1)?.trim() == firstCell
        } ?: error("Satır bulunamadı: $firstCell\n$markdown")
        return row.split('|').drop(1).dropLast(1).map { it.trim() }
    }
}
