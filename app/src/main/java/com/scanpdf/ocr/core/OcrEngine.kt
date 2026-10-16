package com.scanpdf.ocr.core

import android.content.Context
import android.graphics.Bitmap

/** Kullanıcının seçebileceği OCR motorları. */
enum class OcrEngineKind(val id: String, val label: String) {
    TESSERACT("tesseract", "Tesseract (KillerPDF ile aynı motor)"),
    MLKIT("mlkit", "ML Kit (Google, cihaz üstü)");

    companion object {
        fun fromId(id: String?): OcrEngineKind =
            entries.firstOrNull { it.id == id } ?: TESSERACT
    }
}

/**
 * OCR motoru sözleşmesi.
 *
 * Uygulama: [recognize] bloklayıcıdır ve arka plan iş parçacığından çağrılır.
 * Aynı anda tek bir görüntü işlenir; bu nedenle uygulamalar tek bir yerel
 * motor örneği tutabilir.
 */
interface OcrEngine : AutoCloseable {

    val kind: OcrEngineKind

    /** Verilen dilleri hazırlar (model kurulumu vb.). Birden çok kez çağrılabilir. */
    fun prepare(languages: List<String>)

    /** Tek bir sayfa görüntüsünü çözer. */
    fun recognize(pageIndex: Int, bitmap: Bitmap, renderDpi: Float): PageOcrResult

    companion object {
        fun create(context: Context, kind: OcrEngineKind): OcrEngine = when (kind) {
            OcrEngineKind.TESSERACT -> com.scanpdf.ocr.engine.tesseract.TesseractEngine(context)
            OcrEngineKind.MLKIT -> com.scanpdf.ocr.engine.mlkit.MlKitEngine(context)
        }
    }
}
