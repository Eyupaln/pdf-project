package com.scanpdf.ocr.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.rendering.PDFRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color

/**
 * Koordinat matematiğinin **gerçek bir PDF** ile kanıtı.
 *
 * 600x800 noktalık sayfaya bilinen bir dikdörtgen çizilir, `/Rotate` ayarlanır,
 * sayfa PDFBox ile 72 DPI'da görüntülenir (1 piksel = 1 nokta) ve mürekkep
 * bölgesinin yeri [PageGeometry] öngörüsüyle karşılaştırılır. Öngörü ile
 * görüntü uyuşmuyorsa metin katmanı yanlış yere yazılır.
 *
 * Uygulama da PDFBox ile rasterize ettiği için (pdfbox-android 2.0.27.0,
 * masaüstü PDFBox 2.0.27'nin portudur) bu test üretim davranışını doğrular.
 */
class PageGeometryRenderingTest {

    private data class InkRegion(
        val imageWidth: Int,
        val imageHeight: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val text: String get() = "görüntü=${imageWidth}x$imageHeight mürekkep=[$left,$top]-[$right,$bottom]"
    }

    @Test
    fun `donmemis sayfada murekkep beklenen yerde`() = assertInkMatches(0)

    @Test
    fun `90 donmus sayfada murekkep beklenen yerde`() = assertInkMatches(90)

    @Test
    fun `180 donmus sayfada murekkep beklenen yerde`() = assertInkMatches(180)

    @Test
    fun `270 donmus sayfada murekkep beklenen yerde`() = assertInkMatches(270)

    @Test
    fun `ceyrek donuslerde goruntu boyutu yer degistirir`() {
        assertEquals(600, renderInk(0).imageWidth)
        assertEquals(800, renderInk(0).imageHeight)
        assertEquals(800, renderInk(90).imageWidth)
        assertEquals(600, renderInk(90).imageHeight)
        assertEquals(600, renderInk(180).imageWidth)
        assertEquals(800, renderInk(180).imageHeight)
        assertEquals(800, renderInk(270).imageWidth)
        assertEquals(600, renderInk(270).imageHeight)
    }

    @Test
    fun `murekkep bolgesi kucuk bir dikdortgendir`() {
        val ink = renderInk(0)
        val width = ink.right - ink.left + 1
        val height = ink.bottom - ink.top + 1
        assertEquals(INK_W.toDouble(), width.toDouble(), 2.0)
        assertEquals(INK_H.toDouble(), height.toDouble(), 2.0)
    }

    private fun assertInkMatches(rotation: Int) {
        val ink = renderInk(rotation)
        val expected = expectedInkRegion(rotation)

        assertEquals("görüntü genişliği", expectedDisplayWidth(rotation).toInt(), ink.imageWidth)
        assertEquals("görüntü yüksekliği", expectedDisplayHeight(rotation).toInt(), ink.imageHeight)
        assertEquals("sol kenar (r=$rotation, ${ink.text})", expected[0], ink.left.toDouble(), TOLERANCE)
        assertEquals("üst kenar (r=$rotation, ${ink.text})", expected[1], ink.top.toDouble(), TOLERANCE)
        assertEquals("sağ kenar (r=$rotation, ${ink.text})", expected[2], ink.right.toDouble(), TOLERANCE)
        assertEquals("alt kenar (r=$rotation, ${ink.text})", expected[3], ink.bottom.toDouble(), TOLERANCE)

        // Mürekkep, sayfanın tamamını kaplamadığından emin ol (yanlış ölçüm koruması).
        assertTrue(ink.left >= 0 && ink.top >= 0)
        assertTrue("mürekkep tüm sayfayı kaplamış: ${ink.text}", ink.right - ink.left < ink.imageWidth)
    }

    /** [INK_X],[INK_Y] köşeli dikdörtgenin görüntüdeki beklenen sınırlayıcı kutusu. */
    private fun expectedInkRegion(rotation: Int): DoubleArray {
        val corners = listOf(
            INK_X to INK_Y,
            INK_X + INK_W to INK_Y,
            INK_X to INK_Y + INK_H,
            INK_X + INK_W to INK_Y + INK_H,
        ).map { (x, y) ->
            PageGeometry.imagePoint(x.toDouble(), y.toDouble(), PAGE_W.toDouble(), PAGE_H.toDouble(), rotation)
        }

        return doubleArrayOf(
            corners.minOf { it.first },
            corners.minOf { it.second },
            corners.maxOf { it.first },
            corners.maxOf { it.second },
        )
    }

    private fun expectedDisplayWidth(rotation: Int): Float =
        if (PageGeometry.isQuarterTurn(rotation)) PAGE_H else PAGE_W

    private fun expectedDisplayHeight(rotation: Int): Float =
        if (PageGeometry.isQuarterTurn(rotation)) PAGE_W else PAGE_H

    /**
     * [rotation] ile döndürülmüş, bilinen mürekkep dikdörtgenini içeren bir sayfa
     * üretir ve mürekkebin görüntüdeki sınırlayıcı kutusunu bulur.
     */
    private fun renderInk(rotation: Int): InkRegion {
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(PAGE_W, PAGE_H))
            page.rotation = rotation
            document.addPage(page)

            PDPageContentStream(document, page).use { cs ->
                cs.setNonStrokingColor(Color.BLACK)
                cs.addRect(INK_X, INK_Y, INK_W, INK_H)
                cs.fill()
            }

            val image = PDFRenderer(document).renderImageWithDPI(0, RENDER_DPI)

            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = -1
            var bottom = -1
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val red = (image.getRGB(x, y) shr 16) and 0xFF
                    if (red < DARK_THRESHOLD) {
                        if (x < left) left = x
                        if (x > right) right = x
                        if (y < top) top = y
                        if (y > bottom) bottom = y
                    }
                }
            }
            check(right >= 0) { "Hiç mürekkep bulunamadı (r=$rotation)" }
            return InkRegion(image.width, image.height, left, top, right, bottom)
        }
    }

    private companion object {
        /** Sayfa boyutu (nokta) ve 72 DPI: 1 piksel = 1 nokta. */
        const val PAGE_W = 600f
        const val PAGE_H = 800f
        const val RENDER_DPI = 72f

        /** Sayfa uzayında sol-üst bölgeye çizilen mürekkep dikdörtgeni. */
        const val INK_X = 100f
        const val INK_Y = 700f
        const val INK_W = 40f
        const val INK_H = 40f

        /** Kenar yumuşatma ve piksel yuvarlaması için tolerans. */
        const val TOLERANCE = 2.0
        const val DARK_THRESHOLD = 128
    }
}
