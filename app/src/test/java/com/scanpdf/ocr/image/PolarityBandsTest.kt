package com.scanpdf.ocr.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Koyu bant tespiti ve ters çevirme testleri ([PolarityBands]). */
class PolarityBandsTest {

    private fun gray(luma: Int, alpha: Int = 0xFF): Int =
        (alpha shl 24) or (luma shl 16) or (luma shl 8) or luma

    @Test
    fun `parlaklik bt601 ile hesaplanir`() {
        assertEquals(255, PolarityBands.luma(gray(255)))
        assertEquals(0, PolarityBands.luma(gray(0)))
        assertEquals(76, PolarityBands.luma(0xFFFF0000.toInt()))
    }

    @Test
    fun `satir koyu oranlari hesaplanir`() {
        val pixels = intArrayOf(
            gray(255), gray(255), gray(255), gray(255),
            gray(0), gray(0), gray(255), gray(255),
        )

        val fractions = PolarityBands.rowDarkFractions(pixels, width = 4, height = 2)

        assertEquals(0f, fractions[0], 0.001f)
        assertEquals(0.5f, fractions[1], 0.001f)
    }

    @Test
    fun `kisa koyu kumeler bant sayilmaz`() {
        val fractions = floatArrayOf(0.9f, 0.9f, 0.9f, 0.1f, 0.9f, 0.9f)

        assertEquals(listOf(0 until 3), PolarityBands.bands(fractions))
    }

    @Test
    fun `esik altindaki satirlar bant olmaz`() {
        val fractions = floatArrayOf(0.4f, 0.5f, 0.54f, 0.1f)

        assertTrue(PolarityBands.bands(fractions).isEmpty())
    }

    @Test
    fun `goruntu sonuna kadar suren kume banttir`() {
        val fractions = floatArrayOf(0f, 0.9f, 0.9f, 0.9f)

        assertEquals(listOf(1 until 4), PolarityBands.bands(fractions))
    }

    @Test
    fun `invertRows yalnizca verilen satirlari cevirir`() {
        val pixels = intArrayOf(
            gray(255), gray(0),
            gray(255), gray(0),
            gray(255), gray(0),
        )

        PolarityBands.invertRows(pixels, width = 2, bands = listOf(0 until 1))

        assertArrayEquals(
            intArrayOf(gray(0), gray(255), gray(255), gray(0), gray(255), gray(0)),
            pixels,
        )
    }

    @Test
    fun `invertRows alfayi korur`() {
        val pixels = intArrayOf(gray(10, alpha = 0x80))

        PolarityBands.invertRows(pixels, width = 1, bands = listOf(0 until 1))

        assertEquals(0x80, (pixels[0] ushr 24) and 0xFF)
        assertEquals(245, PolarityBands.luma(pixels[0]))
    }

    @Test
    fun `tamamen koyu sayfa tek bant olur`() {
        val fractions = FloatArray(20) { 0.99f }

        assertEquals(listOf(0 until 20), PolarityBands.bands(fractions))
    }
}
