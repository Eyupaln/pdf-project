package com.scanpdf.ocr.core

/**
 * Saf (Android'sız) dikdörtgen: OCR kelime ve satır kutuları.
 *
 * `android.graphics.Rect` yerine bu tip kullanılır; böylece geometri kodu JVM
 * birim testlerinde **gerçek değerlerle** çalışır (stub `Rect` alanları 0 döner).
 * Koordinatlar OCR görüntüsünün piksel uzayındadır: sol/üst/sağ/alt.
 */
data class Box(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left

    val height: Int get() = bottom - top

    /** Sıfır alanlı kutular yazılamaz; çağıranlar atlar. */
    val isValid: Boolean get() = width > 0 && height > 0

    /**
     * [other] ile dikey örtüşme oranı: örtüşen yükseklik / küçük yükseklik.
     * 1'e yakın değer iki kutunun aynı satırda olduğunu gösterir.
     */
    fun verticalOverlap(other: Box): Float {
        val overlap = minOf(bottom, other.bottom) - maxOf(top, other.top)
        if (overlap <= 0) return 0f
        val smaller = minOf(height, other.height)
        return if (smaller <= 0) 0f else overlap.toFloat() / smaller
    }

    /** [other] ile yatay boşluk; negatifse kutular yatay olarak örtüşüyor. */
    fun horizontalGap(other: Box): Int =
        maxOf(left, other.left) - minOf(right, other.right)

    /** İki kutuyu kapsayan en küçük kutu. */
    fun union(other: Box): Box = Box(
        left = minOf(left, other.left),
        top = minOf(top, other.top),
        right = maxOf(right, other.right),
        bottom = maxOf(bottom, other.bottom),
    )

    /**
     * Kutuyu görüntü merkezi etrafında [degrees] kadar döndürüp yeni
     * sınırlayıcı kutuyu döner. Sonuç görüntü sınırlarına kırpılır.
     */
    fun rotateAboutCenter(degrees: Float, imageWidth: Int, imageHeight: Int): Box {
        if (degrees == 0f) return this
        val cx = imageWidth / 2.0
        val cy = imageHeight / 2.0
        val rad = Math.toRadians(degrees.toDouble())
        val cos = Math.cos(rad)
        val sin = Math.sin(rad)

        fun mapX(px: Double, py: Double): Double = cx + (px - cx) * cos - (py - cy) * sin
        fun mapY(px: Double, py: Double): Double = cy + (px - cx) * sin + (py - cy) * cos

        val xs = listOf(
            mapX(left.toDouble(), top.toDouble()),
            mapX(right.toDouble(), top.toDouble()),
            mapX(left.toDouble(), bottom.toDouble()),
            mapX(right.toDouble(), bottom.toDouble()),
        )
        val ys = listOf(
            mapY(left.toDouble(), top.toDouble()),
            mapY(right.toDouble(), top.toDouble()),
            mapY(left.toDouble(), bottom.toDouble()),
            mapY(right.toDouble(), bottom.toDouble()),
        )
        return Box(
            left = xs.min().toInt().coerceAtLeast(0),
            top = ys.min().toInt().coerceAtLeast(0),
            right = xs.max().toInt().coerceAtMost(imageWidth),
            bottom = ys.max().toInt().coerceAtMost(imageHeight),
        )
    }

    companion object {
        /** Boş kutu (0 alanlı); testlerde ve "yok" yerine kullanılır. */
        val EMPTY = Box(0, 0, 0, 0)

        /** Verilen kutuların birleşimi; liste boşsa null. */
        fun unionOf(boxes: List<Box>): Box? = boxes.reduceOrNull { a, b -> a.union(b) }
    }
}
