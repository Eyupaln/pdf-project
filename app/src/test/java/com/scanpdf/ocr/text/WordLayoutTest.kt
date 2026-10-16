package com.scanpdf.ocr.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tablo yapısı çıkarma testleri ([WordLayout]). */
class WordLayoutTest {

    private val answers = listOf(
        "C", "C", "E", "B", "C", "A", "E", "C", "E", "A", "C", "A", "E", "E", "D", "B", "C", "D",
    )

    @Test
    fun `izgara satiri hucrelere bolunur`() {
        val cells = WordLayout.splitCells(gridLine((1..18).map { it.toString() }))

        assertEquals(18, cells.size)
        assertEquals("1", WordLayout.cellText(cells.first()))
        assertEquals("18", WordLayout.cellText(cells.last()))
    }

    @Test
    fun `normal metin tek hucre kalir`() {
        val cells = WordLayout.splitCells(textLine("Yedi İklim Matematik Denemesi"))

        assertEquals(1, cells.size)
        assertEquals("Yedi İklim Matematik Denemesi", WordLayout.cellText(cells.first()))
    }

    @Test
    fun `izgara satiri tablo satiridir`() {
        assertTrue(WordLayout.isTableRow(gridLine(answers)))
        assertTrue(WordLayout.isTableRow(gridLine((1..18).map { it.toString() })))
    }

    @Test
    fun `uzun hucreli metin satiri tablo satiri degildir`() {
        assertFalse(WordLayout.isTableRow(textLine("Birinci sorunun cevabı C seçeneğidir")))
    }

    @Test
    fun `hizali secenek satiri tablo satiri degildir`() {
        // Sınav seçenekleri ızgara gibi hizalıdır ama hücreleri kısacık değildir.
        val options = lineOf(
            wordAt("A)", 0, 0, 40), wordAt("I", 60, 0, 20),
            wordAt("B)", 300, 0, 40), wordAt("II", 360, 0, 30),
            wordAt("C)", 600, 0, 40), wordAt("III", 660, 0, 45),
            wordAt("D)", 900, 0, 40), wordAt("IV", 960, 0, 30),
            wordAt("E)", 1200, 0, 40), wordAt("V", 1260, 0, 20),
        )

        assertEquals(5, WordLayout.splitCells(options).size)
        assertFalse(WordLayout.isTableRow(options))
    }

    @Test
    fun `tablo sayfasi en az iki izgara satiri ister`() {
        val numbers = gridLine((1..18).map { it.toString() })
        val letters = gridLine(answers, top = 80)

        assertTrue(WordLayout.looksTabular(listOf(numbers, letters)))
        assertFalse(WordLayout.looksTabular(listOf(numbers)))
        assertFalse(WordLayout.looksTabular(listOf(textLine("tek satır metin"))))
    }

    @Test
    fun `verim puani karakter ve guveni carpip toplar`() {
        val line = lineOf(
            wordAt("abc", 0, 0, 30, confidence = 50f),
            wordAt("de", 100, 0, 20, confidence = 100f),
        )

        // 3 karakter * 0.5 + 2 karakter * 1.0
        assertEquals(3.5, WordLayout.yieldScore(listOf(line)), 0.001)
    }

    @Test
    fun `tablo blogu esit sutunlara doldurulur`() {
        val lines = listOf(
            textLine("CEVAP ANAHTARI", top = 0),
            gridLine(listOf("1", "2", "3", "4"), top = 60),
            gridLine(listOf("C", "D", "E", "F", "G"), top = 140),
            textLine("son satır", top = 220),
        )

        val blocks = WordLayout.tableBlocks(lines)

        assertEquals(1, blocks.size)
        val block = blocks.single()
        assertEquals(1, block.startLine)
        assertEquals(2, block.lineCount)
        assertEquals(listOf("1", "2", "3", "4", ""), block.rows[0])
        assertEquals(listOf("C", "D", "E", "F", "G"), block.rows[1])
    }

    @Test
    fun `tek izgara satiri tablo blogu olusturmaz`() {
        val lines = listOf(textLine("başlık"), gridLine(listOf("1", "2", "3", "4")))

        assertTrue(WordLayout.tableBlocks(lines).isEmpty())
    }

    @Test
    fun `ardisik olmayan izgara satirlari ayri bloga ayrilir`() {
        val lines = listOf(
            gridLine(listOf("1", "2", "3", "4"), top = 0),
            gridLine(listOf("A", "B", "C", "D"), top = 80),
            textLine("ara başlık", top = 160),
            gridLine(listOf("5", "6", "7", "8"), top = 240),
            gridLine(listOf("E", "F", "G", "H"), top = 320),
        )

        val blocks = WordLayout.tableBlocks(lines)

        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].startLine)
        assertEquals(3, blocks[1].startLine)
    }
}
