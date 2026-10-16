package com.scanpdf.ocr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saf geometri ([Box]) testleri: kelime kutuları ve döndürme. */
class BoxTest {

    @Test
    fun `genislik ve yukseklik farktan hesaplanir`() {
        val box = Box(10, 20, 40, 70)

        assertEquals(30, box.width)
        assertEquals(50, box.height)
        assertTrue(box.isValid)
    }

    @Test
    fun `sifir alanli kutu gecersizdir`() {
        assertFalse(Box(10, 20, 10, 70).isValid)
        assertFalse(Box(10, 20, 40, 20).isValid)
        assertFalse(Box.EMPTY.isValid)
    }

    @Test
    fun `dikey ortusme kucuk yukseklige gore olculur`() {
        val a = Box(0, 0, 100, 40)
        val b = Box(0, 20, 100, 60)
        val c = Box(0, 100, 100, 140)

        assertEquals(0.5f, a.verticalOverlap(b), 0.001f)
        assertEquals(0f, a.verticalOverlap(c), 0.001f)
    }

    @Test
    fun `yatay bosluk negatifse kutular ortusuyor`() {
        assertEquals(20, Box(0, 0, 30, 40).horizontalGap(Box(50, 0, 80, 40)))
        assertEquals(-10, Box(0, 0, 40, 40).horizontalGap(Box(30, 0, 70, 40)))
    }

    @Test
    fun `union iki kutuyu kapsar`() {
        val union = Box(10, 10, 30, 30).union(Box(50, 5, 60, 80))

        assertEquals(Box(10, 5, 60, 80), union)
    }

    @Test
    fun `unionOf bos listede null doner`() {
        assertNull(Box.unionOf(emptyList()))
        assertEquals(Box(0, 0, 10, 10), Box.unionOf(listOf(Box(0, 0, 10, 10))))
    }

    @Test
    fun `sifir derece kutuyu degistirmez`() {
        val box = Box(10, 20, 40, 70)

        assertEquals(box, box.rotateAboutCenter(0f, 100, 200))
    }

    @Test
    fun `90 derece donme merkez etrafinda kutuyu tasir`() {
        // 200x200 görüntüde merkez (100,100); kutu dönünce taşınır ama kırpılmaz.
        val rotated = Box(10, 20, 30, 40).rotateAboutCenter(90f, 200, 200)

        assertTrue(rotated.isValid)
        assertBoxNear(Box(160, 10, 180, 30), rotated)
    }

    @Test
    fun `donme sonrasi kutu goruntu sinirlarina kirpilir`() {
        // 180° dönmede kutu sağ alt köşeden sol üste taşınır; sınırlar korunur.
        val rotated = Box(0, 0, 20, 20).rotateAboutCenter(180f, 100, 200)

        assertEquals(80, rotated.left)
        assertEquals(100, rotated.right)
        assertTrue(rotated.top >= 0)
        assertTrue(rotated.bottom <= 200)
    }

    /** Kayan nokta yuvarlamasına toleranslı kutu karşılaştırması (bkz. rotateAboutCenter). */
    private fun assertBoxNear(expected: Box, actual: Box, tolerance: Int = 1) {
        listOf(
            "left" to (expected.left to actual.left),
            "top" to (expected.top to actual.top),
            "right" to (expected.right to actual.right),
            "bottom" to (expected.bottom to actual.bottom),
        ).forEach { (name, pair) ->
            val difference = kotlin.math.abs(pair.first - pair.second)
            assertTrue("$name: beklenen ${pair.first}, gerçek ${pair.second}", difference <= tolerance)
        }
    }
}
