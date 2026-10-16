package com.scanpdf.ocr.core

import java.io.File

/** Kullanıcının seçtiği işlem seçenekleri. */
data class OcrOptions(
    val engine: OcrEngineKind = OcrEngineKind.TESSERACT,
    /** Tesseract için "tur","eng"; ML Kit yalnızca Latin alfabesini destekler. */
    val languages: List<String> = listOf("tur", "eng"),
    /** Sayfayı görüntüye çevirme çözünürlüğü. KillerPDF 300 DPI kullanır. */
    val dpi: Int = 300,
    /** Eşikleme (binarize) — koordinatları değiştirmez. */
    val preprocess: Boolean = true,
    /** Eğim düzeltme — koordinatları değiştirir, ters döndürme uygulanır. */
    val deskew: Boolean = false,
    /** Aranabilir PDF üret (görünmez metin katmanı). */
    val writeTextLayer: Boolean = true,
)

enum class OcrStage(val label: String) {
    PREPARING("Hazırlanıyor"),
    RASTERIZING("Sayfa görüntüye çevriliyor"),
    PREPROCESSING("Görüntü iyileştiriliyor"),
    RECOGNIZING("Metin tanınıyor"),
    WRITING("Metin katmanı yazılıyor"),
    SAVING("Kaydediliyor"),
    DONE("Tamamlandı"),
    ;

    val isTerminal: Boolean get() = this == DONE
}

/** İlerleme bildirimi. Sayfa başına birden çok aşama yayınlanır. */
data class OcrProgress(
    val stage: OcrStage,
    val pageIndex: Int,
    val pageCount: Int,
    val message: String = "",
) {
    val fraction: Float
        get() = if (pageCount <= 0) 0f else (pageIndex.coerceIn(0, pageCount)).toFloat() / pageCount
}

/** İşlemin sonucu. */
data class OcrOutcome(
    val documentTitle: String,
    val pageCount: Int,
    val pages: List<PageOcrResult>,
    val text: String,
    /** Aranabilir PDF'in yolu; [OcrOptions.writeTextLayer] false ise null. */
    val outputPdf: File?,
    /** Ham metin (`.txt`) çıktısı; her işte üretilir. */
    val outputText: File,
    /** Markdown (`.md`) çıktısı; her işte üretilir. */
    val outputMarkdown: File,
    val engineKind: OcrEngineKind,
    val elapsedMs: Long,
)
