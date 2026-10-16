package com.scanpdf.ocr.pipeline

import com.scanpdf.ocr.core.Box
import com.scanpdf.ocr.core.OcrLine
import com.scanpdf.ocr.core.OcrWord
import com.scanpdf.ocr.core.PageOcrResult
import com.scanpdf.ocr.image.ImagePreprocessor
import com.scanpdf.ocr.image.PolarityBands
import com.scanpdf.ocr.text.TextFormat
import com.scanpdf.ocr.text.WordLayout
import com.scanpdf.ocr.text.groupIntoLines
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.Tesseract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File

/**
 * **Gerçek Tesseract** ile tablo okuma testi (Tess4J + uygulamanın
 * `assets/tessdata` modelleri).
 *
 * Ölçülen sorun: koyu zeminli bantlarda beyaz yazı olan cevap anahtarı
 * sayfalarında Tesseract, polarite kararını sayfanın tamamı için verdiği için
 * tablo satırlarının tamamını kaybediyordu (yalnızca büyük başlıklar okunuyordu).
 *
 * Bu test, sözü edilen sayfanın birebir benzerini üretir (A4 @ 300 DPI, koyu
 * başlık bandı + koyu numara satırı, açık cevap satırı), [PolarityBands] ile
 * bantları ters çevirir ve sonucun okunabildiğini doğrular.
 *
 * Not: Leptonica eşikleme JVM'de bulunmadığı için eşikleme burada global eşikle
 * taklit edilir (üretimde Sauvola). Tek karakterlik hücrelerde kalan hatalar,
 * hücre çerçeve çizgilerinin eşiklemeden sonra gliflerle birleşmesinden gelir;
 * cevap harfleri ve iki basamaklı numaralar güvenilir okunur.
 */
class TableRecognitionTest {

    private val tessdataDir = File("src/main/assets/tessdata")

    @Test
    fun `koyu bantli cevap anahtari polarite normalizasyonu ile okunur`() {
        assumeTrue("tessdata bulunamadı: ${tessdataDir.absolutePath}", tessdataDir.isDirectory)

        // Üretim sırası: eşikleme → polarite normalizasyonu → OCR.
        val binarized = binarize(answerKeyPage())

        // 1) Normalizasyon olmadan koyu bant içeriği (numara satırı) hiç okunmaz.
        val beforeLines = recognize(binarized, psm = ITessAPI.TessPageSegMode.PSM_AUTO)
        assertNull("Normalizasyon öncesi numara satırı okunmamalı", digitRow(beforeLines))

        // 2) Polarite normalizasyonu + tek blok modu tablo satırlarını kurtarır.
        val afterLines = recognize(normalizePolarity(binarized), psm = ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK)
        val gridRows = afterLines.filter { WordLayout.splitCells(it).size >= 10 }
        assertTrue(
            "Normalizasyon sonrası tablo satırları bulunmalı: ${afterLines.map { it.text }}",
            gridRows.size >= 2,
        )

        // Cevap harfleri satırı: ilk harfi A-E olan en az 16 hücreli satır.
        val letterRow = gridRows.firstOrNull { row ->
            val cells = WordLayout.splitCells(row).map { WordLayout.cellText(it).trim() }
            cells.size >= 16 && cells.count { it.isNotEmpty() && it.first().uppercaseChar() in 'A'..'E' } >= 16
        }
        assertNotNull("Cevap harfleri satırı okunmalı: ${gridRows.map { it.text }}", letterRow)

        // Numara satırının kuyruğu (koyu bantta eskiden kaybolan içerik) okunabilmeli.
        val cells = gridRows.flatMap { WordLayout.splitCells(it).map { cell -> WordLayout.cellText(cell).trim() } }
        val recoveredTail = listOf("12", "13", "14", "15", "16", "17").count { cells.contains(it) }
        assertTrue(
            "Numara satırının kuyruğu okunmalı (en az 3 iki basamaklı sayı): $cells",
            recoveredTail >= 3,
        )

        // 3) Tablo, markdown çıktısında gerçek tablo olur.
        val page = PageOcrResult(0, binarized.width, binarized.height, 300f, afterLines)
        val markdown = TextFormat.markdownText("cevap-anahtari", listOf(page))
        assertTrue(markdown, markdown.lines().any { it.startsWith("| ---") })
        assertTrue(
            markdown,
            markdown.lines().any { it.count { character -> character == '|' } >= 16 },
        )
    }

    /** Hücreleri tümüyle sayı olan ilk ızgara satırı (numara satırı). */
    private fun digitRow(lines: List<OcrLine>): List<String>? =
        lines.firstNotNullOfOrNull { line ->
            val cells = WordLayout.splitCells(line).map { WordLayout.cellText(it).trim() }
            if (cells.size >= 16 && cells.all { it.toIntOrNull() != null }) cells else null
        }

    /**
     * Üretimde eşikleme Leptonica Sauvola ile yapılır; JVM'de Leptonica
     * bulunmadığı için aynı rolü global eşik üstlenir (koyu bantlar ve zemini
     * siyah/beyaza indirir).
     */
    private fun binarize(image: BufferedImage, threshold: Int = 128): BufferedImage {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        for (index in pixels.indices) {
            val value = if (PolarityBands.luma(pixels[index]) < threshold) 0 else 255
            pixels[index] = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }
        val output = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        output.setRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        return output
    }

    /** Tess4J ile okur ve kelimeleri ürün modellerine çevirir. */
    private fun recognize(image: BufferedImage, psm: Int): List<OcrLine> {
        val engine = Tesseract()
        engine.setDatapath(tessdataDir.absolutePath)
        engine.setLanguage("tur+eng")
        engine.setPageSegMode(psm)
        engine.setVariable("user_defined_dpi", "300")

        val words = engine.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD).mapNotNull { word ->
            val text = word.text.trim()
            if (text.isEmpty()) return@mapNotNull null
            val rect = word.boundingBox
            OcrWord(
                text = text,
                box = Box(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height),
                confidence = word.confidence,
            )
        }
        return groupIntoLines(words)
    }

    /** [ImagePreprocessor.normalizePolarity] ile aynı saf algoritmayı uygular. */
    private fun normalizePolarity(image: BufferedImage): BufferedImage {
        val width = image.width
        val height = image.height
        val step = maxOf(1, width / ImagePreprocessor.POLARITY_SAMPLE_WIDTH)

        val sampled = BufferedImage(maxOf(1, width / step), maxOf(1, height / step), BufferedImage.TYPE_INT_RGB)
        sampled.createGraphics().apply {
            drawImage(image, 0, 0, sampled.width, sampled.height, null)
            dispose()
        }
        val sampledPixels = sampled.getRGB(0, 0, sampled.width, sampled.height, null, 0, sampled.width)
        val bands = PolarityBands.bands(PolarityBands.rowDarkFractions(sampledPixels, sampled.width, sampled.height))

        val pixels = image.getRGB(0, 0, width, height, null, 0, width)
        val fullBands = bands.map { band ->
            val top = (band.first * step).coerceIn(0, height - 1)
            val bottom = (((band.last + 1) * step) - 1).coerceIn(top, height - 1)
            top until (bottom + 1)
        }
        PolarityBands.invertRows(pixels, width, fullBands)

        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        output.setRGB(0, 0, width, height, pixels, 0, width)
        return output
    }

    /**
     * Kullanıcının bildirdiği sayfanın benzeri: her deneme için koyu başlık bandı,
     * "GENEL KÜLTÜR", koyu zemin üzerinde beyaz numara satırı ve açık zemin
     * üzerinde koyu cevap harfleri.
     */
    private fun answerKeyPage(): BufferedImage {
        val width = 2480
        val height = 3508
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = Color.BLACK
        g.font = Font("Arial", Font.BOLD, 54)
        g.drawString("CEVAP ANAHTARI", 150, 170)

        val cellWidth = 120
        val cellHeight = 76
        val x0 = 150
        var y = 250
        answers.forEachIndexed { group, letters ->
            val bandWidth = cellWidth * letters.size

            g.color = Color(74, 74, 74)
            g.fillRect(x0, y, bandWidth, 118)
            g.color = Color.WHITE
            g.font = Font("Arial", Font.BOLD, 52)
            val title = "DENEME SINAVI - ${group + 1}"
            g.drawString(title, x0 + (bandWidth - g.fontMetrics.stringWidth(title)) / 2, y + 80)

            g.color = Color.BLACK
            g.font = Font("Arial", Font.BOLD, 38)
            g.drawString("GENEL KÜLTÜR", x0, y + 175)

            // Numara satırı: koyu zemin + beyaz rakamlar (kayıp içeriğin kaynağı).
            val numberTop = y + 195
            g.color = Color(58, 58, 58)
            g.fillRect(x0, numberTop, bandWidth, cellHeight)
            g.font = Font("Arial", Font.BOLD, 34)
            letters.indices.forEach { column ->
                g.color = Color(150, 150, 150)
                g.drawRect(x0 + column * cellWidth, numberTop, cellWidth, cellHeight)
                g.color = Color.WHITE
                val number = (column + 1).toString()
                g.drawString(
                    number,
                    x0 + column * cellWidth + (cellWidth - g.fontMetrics.stringWidth(number)) / 2,
                    numberTop + 52,
                )
            }

            // Cevap harfi satırı: açık zemin + koyu harfler.
            val answerTop = numberTop + cellHeight
            g.color = Color.WHITE
            g.fillRect(x0, answerTop, bandWidth, cellHeight)
            g.font = Font("Arial", Font.BOLD, 40)
            letters.forEachIndexed { column, letter ->
                g.color = Color(150, 150, 150)
                g.drawRect(x0 + column * cellWidth, answerTop, cellWidth, cellHeight)
                g.color = Color.BLACK
                g.drawString(
                    letter,
                    x0 + column * cellWidth + (cellWidth - g.fontMetrics.stringWidth(letter)) / 2,
                    answerTop + 54,
                )
            }
            y = answerTop + cellHeight + 70
        }
        g.dispose()
        return image
    }

    private companion object {
        /** Örnek cevap anahtarı: 7 deneme × 18 cevap. */
        val answers: List<List<String>> = listOf(
            listOf("C", "C", "E", "B", "C", "A", "E", "C", "E", "A", "C", "A", "E", "E", "D", "B", "C", "D"),
            listOf("C", "E", "A", "D", "D", "D", "C", "B", "A", "B", "C", "E", "C", "B", "E", "E", "A", "E"),
            listOf("C", "D", "A", "C", "D", "A", "D", "E", "B", "E", "C", "D", "B", "A", "B", "A", "C", "B"),
            listOf("C", "D", "B", "C", "A", "B", "E", "A", "C", "A", "E", "E", "B", "C", "D", "A", "E", "A"),
            listOf("E", "A", "D", "E", "B", "A", "A", "B", "C", "C", "B", "C", "C", "B", "E", "D", "C", "C"),
            listOf("D", "C", "B", "E", "E", "A", "E", "E", "C", "D", "D", "D", "A", "E", "D", "A", "C", "C"),
            listOf("D", "C", "E", "D", "E", "A", "B", "A", "A", "E", "D", "C", "D", "B", "E", "A", "C", "D"),
        )
    }
}
