package com.scanpdf.ocr.core

import java.io.File

/**
 * Sayfa bölütleme (düzen) tercihi.
 *
 * Ölçüm (gerçek Tesseract 5.3.4, cevap anahtarı tablosu): varsayılan otomatik
 * bölütleme ızgara satırlarının bir bölümünü düşürüyor (18 sütunun 11'i),
 * tek blok modu tablonun tamamını okuyor. Bu yüzden "otomatik" mod, tablo gibi
 * görünen sayfaları tek blok modunda yeniden dener ve daha çok içerik okuyan
 * sonucu korur.
 */
enum class PageLayout {
    /** Otomatik: tablo gibi görünen sayfalar tek blok modunda yeniden denenir. */
    AUTO,

    /** Tablo / ızgara: sayfa tek blok olarak okunur. */
    TABLE,
}

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
    /** Sayfa bölütleme tercihi (yalnızca Tesseract kullanır). */
    val layout: PageLayout = PageLayout.AUTO,
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
