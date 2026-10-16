package com.scanpdf.ocr.image

/**
 * Koyu zeminli bantları bulup ters çevirme (saf Kotlin, Android'sız).
 *
 * **Neden gerekli** (gerçek Tesseract 5.3.4 ile ölçüldü): Tesseract, "yazı koyu
 * mu, açık zemin üzerinde mi" kararını sayfanın **tamamı** için bir kez verir.
 * Cevap anahtarı gibi satırları dönüşümlü olan sayfalarda bu karar sayfanın
 * yarısını bozar: koyu bant üzerindeki beyaz yazı (başlık ve soru numaraları)
 * hiç okunmaz. Bantları **yerinde** ters çevirmek sayfayı tek polariteye indirir
 * ve içeriğin tamamı okunur.
 *
 * Bu nesne yalnızca piksel değerlerini değiştirir; **koordinatlar değişmez**,
 * bu yüzden kelime kutuları için telafi gerekmez.
 */
object PolarityBands {

    /** Bir satırın "koyu bant" sayılması için gereken en az koyu piksel oranı. */
    const val DARK_ROW_THRESHOLD = 0.55f

    /** Bant sayılması için gereken en az ardışık satır sayısı. */
    const val MIN_BAND_ROWS = 3

    /** Koyu sayılan en yüksek parlaklık (0..255). */
    const val DARK_LUMA = 128

    /** ARGB pikselin parlaklığı (0..255, BT.601). */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** Satır başına koyu piksel oranı (uzunluk = [height]). */
    fun rowDarkFractions(pixels: IntArray, width: Int, height: Int): FloatArray {
        val fractions = FloatArray(height.coerceAtLeast(0))
        if (width <= 0 || height <= 0) return fractions
        for (y in 0 until height) {
            val base = y * width
            var dark = 0
            for (x in 0 until width) {
                val i = base + x
                if (i >= pixels.size) break
                if (luma(pixels[i]) < DARK_LUMA) dark++
            }
            fractions[y] = dark.toFloat() / width
        }
        return fractions
    }

    /**
     * Eşiğin üzerindeki ardışık satır kümelerini döner. [minRows]'tan kısa
     * kümeler (örneğin tek bir kalın çizgi) bant sayılmaz.
     */
    fun bands(
        rowDarkFractions: FloatArray,
        threshold: Float = DARK_ROW_THRESHOLD,
        minRows: Int = MIN_BAND_ROWS,
    ): List<IntRange> {
        val bands = mutableListOf<IntRange>()
        var start = -1
        for (y in rowDarkFractions.indices) {
            if (rowDarkFractions[y] >= threshold) {
                if (start < 0) start = y
            } else if (start >= 0) {
                if (y - start >= minRows) bands += start until y
                start = -1
            }
        }
        if (start >= 0 && rowDarkFractions.size - start >= minRows) {
            bands += start until rowDarkFractions.size
        }
        return bands
    }

    /** [pixels] içindeki [bands] satırlarını ters çevirir; alfa korunur. */
    fun invertRows(pixels: IntArray, width: Int, bands: List<IntRange>): IntArray {
        if (width <= 0) return pixels
        for (band in bands) {
            for (y in band) {
                val base = y * width
                if (base < 0 || base >= pixels.size) continue
                for (x in 0 until width) {
                    val i = base + x
                    if (i >= pixels.size) break
                    val argb = pixels[i]
                    pixels[i] = (argb and 0xFF000000.toInt()) or (0xFFFFFF - (argb and 0xFFFFFF))
                }
            }
        }
        return pixels
    }
}
