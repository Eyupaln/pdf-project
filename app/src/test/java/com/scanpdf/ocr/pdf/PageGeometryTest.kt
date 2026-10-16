package com.scanpdf.ocr.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Döndürmeli sayfa koordinat matematiği (saf hesap, Android yok).
 *
 * Beklenen değerler PDF 32000-1:2008 §14.11.2'den türetilmiştir: `/Rotate`
 * sayfayı **görüntülerken saat yönünde** döndürür. Görüntü koordinatı yukarıdan
 * aşağı, sayfa uzayı alttan yukarı ölçülür.
 *
 * Aynı eşleme [PageGeometryRenderingTest] içinde gerçek PDFBox çıktısıyla da
 * doğrulanır.
 */
class PageGeometryTest {

    private val w = 600.0
    private val h = 800.0

    private fun assertAnchor(
        expectedX: Double,
        expectedY: Double,
        expectedDegrees: Double,
        dx: Double,
        dy: Double,
        rotation: Int,
    ) {
        val anchor = PageGeometry.anchor(dx, dy, w, h, rotation)
        assertEquals("x (r=$rotation, dx=$dx, dy=$dy)", expectedX, anchor.x, EPS)
        assertEquals("y (r=$rotation, dx=$dx, dy=$dy)", expectedY, anchor.y, EPS)
        assertEquals("açı (r=$rotation)", expectedDegrees, anchor.degrees, EPS)
    }

    @Test
    fun `donme degeri 0-90-180-270 araligina indirgenir`() {
        assertEquals(0, PageGeometry.normalizeRotation(0))
        assertEquals(90, PageGeometry.normalizeRotation(90))
        assertEquals(180, PageGeometry.normalizeRotation(180))
        assertEquals(270, PageGeometry.normalizeRotation(270))
        assertEquals(270, PageGeometry.normalizeRotation(-90))
        assertEquals(90, PageGeometry.normalizeRotation(450))
        assertEquals(0, PageGeometry.normalizeRotation(360))
        assertEquals(5, PageGeometry.normalizeRotation(725))
    }

    @Test
    fun `ceyrek donuslerde goruntulenen boyut yer degistirir`() {
        assertEquals(600.0, PageGeometry.displayWidth(w, h, 0), EPS)
        assertEquals(800.0, PageGeometry.displayHeight(w, h, 0), EPS)
        assertEquals(600.0, PageGeometry.displayWidth(w, h, 180), EPS)
        assertEquals(800.0, PageGeometry.displayHeight(w, h, 180), EPS)

        assertEquals(800.0, PageGeometry.displayWidth(w, h, 90), EPS)
        assertEquals(600.0, PageGeometry.displayHeight(w, h, 90), EPS)
        assertEquals(800.0, PageGeometry.displayWidth(w, h, 270), EPS)
        assertEquals(600.0, PageGeometry.displayHeight(w, h, 270), EPS)
    }

    @Test
    fun `ceyrek donus bayragi dogru`() {
        assertTrue(PageGeometry.isQuarterTurn(90))
        assertTrue(PageGeometry.isQuarterTurn(-90))
        assertTrue(PageGeometry.isQuarterTurn(270))
        assertFalse(PageGeometry.isQuarterTurn(0))
        assertFalse(PageGeometry.isQuarterTurn(180))
    }

    @Test
    fun `r0 goruntu koseleri sayfa koselerine eslenir`() {
        assertAnchor(0.0, 800.0, 0.0, 0.0, 0.0, 0)
        assertAnchor(600.0, 800.0, 0.0, 600.0, 0.0, 0)
        assertAnchor(0.0, 0.0, 0.0, 0.0, 800.0, 0)
        assertAnchor(600.0, 0.0, 0.0, 600.0, 800.0, 0)
    }

    @Test
    fun `r90 goruntu koseleri sayfa koselerine eslenir`() {
        // Saat yönünde 90°: görüntünün sol-üstü sayfanın sol-altıdır.
        assertAnchor(0.0, 0.0, 90.0, 0.0, 0.0, 90)
        assertAnchor(0.0, 800.0, 90.0, 800.0, 0.0, 90)
        assertAnchor(600.0, 0.0, 90.0, 0.0, 600.0, 90)
        assertAnchor(600.0, 800.0, 90.0, 800.0, 600.0, 90)
    }

    @Test
    fun `r180 goruntu koseleri sayfa koselerine eslenir`() {
        // 180°: her iki eksen de ters çevrilir (eski kod dikey ekseni çevirmiyordu).
        assertAnchor(600.0, 0.0, 180.0, 0.0, 0.0, 180)
        assertAnchor(0.0, 0.0, 180.0, 600.0, 0.0, 180)
        assertAnchor(600.0, 800.0, 180.0, 0.0, 800.0, 180)
        assertAnchor(0.0, 800.0, 180.0, 600.0, 800.0, 180)
    }

    @Test
    fun `r270 goruntu koseleri sayfa koselerine eslenir`() {
        // 270° (saat yönünde) = 90° sola: görüntünün sol-üstü sayfanın sağ-üstüdür.
        assertAnchor(600.0, 800.0, -90.0, 0.0, 0.0, 270)
        assertAnchor(600.0, 0.0, -90.0, 800.0, 0.0, 270)
        assertAnchor(0.0, 800.0, -90.0, 0.0, 600.0, 270)
        assertAnchor(0.0, 0.0, -90.0, 800.0, 600.0, 270)
    }

    @Test
    fun `tum donmelerde goruntu koseleri sayfa dikdortgeni icinde kalir`() {
        for (rotation in intArrayOf(0, 90, 180, 270)) {
            val dispW = PageGeometry.displayWidth(w, h, rotation)
            val dispH = PageGeometry.displayHeight(w, h, rotation)
            for (dx in doubleArrayOf(0.0, dispW / 2, dispW)) {
                for (dy in doubleArrayOf(0.0, dispH / 2, dispH)) {
                    val anchor = PageGeometry.anchor(dx, dy, w, h, rotation)
                    assertTrue(
                        "x taştı: r=$rotation dx=$dx dy=$dy → ${anchor.x}",
                        anchor.x >= -EPS && anchor.x <= w + EPS,
                    )
                    assertTrue(
                        "y taştı: r=$rotation dx=$dx dy=$dy → ${anchor.y}",
                        anchor.y >= -EPS && anchor.y <= h + EPS,
                    )
                }
            }
        }
    }

    @Test
    fun `metin acisi her donmede dogru`() {
        assertEquals(0.0, PageGeometry.anchor(10.0, 10.0, w, h, 0).degrees, EPS)
        assertEquals(90.0, PageGeometry.anchor(10.0, 10.0, w, h, 90).degrees, EPS)
        assertEquals(180.0, PageGeometry.anchor(10.0, 10.0, w, h, 180).degrees, EPS)
        assertEquals(-90.0, PageGeometry.anchor(10.0, 10.0, w, h, 270).degrees, EPS)
        // 360° ve 450° indirgenmiş gibi davranır.
        assertEquals(0.0, PageGeometry.anchor(10.0, 10.0, w, h, 360).degrees, EPS)
        assertEquals(90.0, PageGeometry.anchor(10.0, 10.0, w, h, 450).degrees, EPS)
    }

    @Test
    fun `anchor ile imagePoint birbirinin tersidir`() {
        val samples = listOf(0.0 to 0.0, 600.0 to 800.0, 123.0 to 456.0, 599.0 to 1.0)
        for (rotation in intArrayOf(0, 90, 180, 270)) {
            val dispW = PageGeometry.displayWidth(w, h, rotation)
            val dispH = PageGeometry.displayHeight(w, h, rotation)
            for ((ix, iy) in samples.map { (a, b) -> a * dispW / 600.0 to b * dispH / 800.0 }) {
                val anchor = PageGeometry.anchor(ix, iy, w, h, rotation)
                val (backX, backY) = PageGeometry.imagePoint(anchor.x, anchor.y, w, h, rotation)
                assertEquals("r=$rotation x geri dönüşü", ix, backX, 1e-6)
                assertEquals("r=$rotation y geri dönüşü", iy, backY, 1e-6)
            }
        }
    }

    @Test
    fun `kucuk kirpma kutusu kaydirmasi eslemeyi degistirmez`() {
        // Kırpma kutusu kaydırması (ox, oy) çağıran tarafından eklenir; eşleme
        // yalnızca kırpma kutusunun kendi koordinatlarında hesaplanır.
        val anchor = PageGeometry.anchor(0.0, 800.0, w, h, 0)
        assertEquals(0.0, anchor.x, EPS)
        assertEquals(0.0, anchor.y, EPS)
    }

    private companion object {
        const val EPS = 1e-6
    }
}
