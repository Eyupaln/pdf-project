package com.scanpdf.ocr.core

/**
 * Tek bir OCR kelimesi.
 *
 * [box] daima OCR'ın çalıştığı görüntünün piksel koordinatıdır (sol, üst, sağ,
 * alt). Sayfaya eşleme [PageOcrResult] taşır.
 */
data class OcrWord(
    val text: String,
    val box: Box,
    val confidence: Float,
)

/** Aynı satırdaki kelimeler. */
data class OcrLine(
    val words: List<OcrWord>,
    val box: Box,
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
        else copy(
            lines = lines.map { line ->
                OcrLine(
                    words = line.words.map {
                        it.copy(box = it.box.rotateAboutCenter(-appliedRotationDegrees, imageWidth, imageHeight))
                    },
                    box = line.box.rotateAboutCenter(-appliedRotationDegrees, imageWidth, imageHeight),
                )
            },
            appliedRotationDegrees = 0f,
        )
}
