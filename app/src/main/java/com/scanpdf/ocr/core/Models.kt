package com.scanpdf.ocr.core

import android.graphics.Rect

/**
 * Tek bir OCR kelimesi.
 *
 * [box] daima OCR'ın çalıştığı görüntünün piksel koordinatıdır
 * (sol, üst, sağ, alt). Sayfaya eşleme [PageOcrResult] taşır.
 */
data class OcrWord(
    val text: String,
    val box: Rect,
    val confidence: Float,
)

/** Aynı satırdaki kelimeler. */
data class OcrLine(
    val words: List<OcrWord>,
    val box: Rect,
) {
    /** Kelimeleri tek boşlukla birleştirir. */
    val text: String get() = words.joinToString(" ") { it.text }
}

/**
 * Bir sayfanın OCR sonucu.
 *
 * [imageWidth]/[imageHeight], [renderDpi] ile üretilmiş görüntünün boyutudur.
 * [appliedRotationDegrees] ön işlemede görüntüye uygulanan döndürmedir
 * (saat yönünün tersi pozitif); 0 ise geometri değişmemiştir. Metin katmanı
 * yazıcısı kelime kutularını sayfaya eşlerken bu açının tersini uygular.
 */
data class PageOcrResult(
    val pageIndex: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val renderDpi: Float,
    val lines: List<OcrLine>,
    val appliedRotationDegrees: Float = 0f,
) {
    val words: List<OcrWord> get() = lines.flatMap { it.words }

    /** Sayfanın düz metni (satır sonlarıyla). */
    val text: String get() = lines.joinToString("\n") { it.text }

    val meanConfidence: Float
        get() = if (words.isEmpty()) 0f else words.map { it.confidence }.average().toFloat()

    /** Kelime kutularını, uygulanan döndürmenin tersiyle geri taşır. */
    fun withoutPreprocessRotation(): PageOcrResult =
        if (appliedRotationDegrees == 0f) this
        else copy(lines = lines.map { line ->
            OcrLine(
                words = line.words.map { it.copy(box = it.box.rotateAboutCenter(-appliedRotationDegrees, imageWidth, imageHeight)) },
                box = line.box.rotateAboutCenter(-appliedRotationDegrees, imageWidth, imageHeight),
            )
        }, appliedRotationDegrees = 0f)
}

/** [Rect]'i görüntü merkezi etrafında döndürüp yeni sınırlayıcı kutuyu döner. */
internal fun Rect.rotateAboutCenter(degrees: Float, imageWidth: Int, imageHeight: Int): Rect {
    if (degrees == 0f) return Rect(this)
    val cx = imageWidth / 2.0
    val cy = imageHeight / 2.0
    val rad = Math.toRadians(degrees.toDouble())
    val cos = Math.cos(rad)
    val sin = Math.sin(rad)

    fun map(px: Double, py: Double): Pair<Double, Double> {
        val dx = px - cx
        val dy = py - cy
        return Pair(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos)
    }

    val corners = listOf(
        map(left.toDouble(), top.toDouble()),
        map(right.toDouble(), top.toDouble()),
        map(left.toDouble(), bottom.toDouble()),
        map(right.toDouble(), bottom.toDouble()),
    )
    val xs = corners.map { it.first }
    val ys = corners.map { it.second }
    return Rect(
        xs.min().toInt().coerceAtLeast(0),
        ys.min().toInt().coerceAtLeast(0),
        xs.max().toInt().coerceAtMost(imageWidth),
        ys.max().toInt().coerceAtMost(imageHeight),
    )
}
