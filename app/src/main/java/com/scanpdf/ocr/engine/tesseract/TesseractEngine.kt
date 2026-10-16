package com.scanpdf.ocr.engine.tesseract

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import com.scanpdf.ocr.core.Box
import com.scanpdf.ocr.core.OcrEngine
import com.scanpdf.ocr.core.OcrEngineKind
import com.scanpdf.ocr.core.OcrLine
import com.scanpdf.ocr.core.OcrWord
import com.scanpdf.ocr.core.PageLayout
import com.scanpdf.ocr.core.PageOcrResult

/**
 * Tesseract (Tesseract4Android) tabanlı OCR motoru.
 *
 * [prepare] ile dil modelleri kurulur ve tek bir [TessBaseAPI] örneği hazırlanır;
 * [recognize] bu örneği yeniden kullanır. Çağıran taraf [recognize]'ı tek bir arka
 * plan iş parçacığından çağırmalıdır (arayüz sözleşmesi).
 */
class TesseractEngine(private val context: Context) : OcrEngine {

    override val kind: OcrEngineKind = OcrEngineKind.TESSERACT

    private var api: TessBaseAPI? = null
    private var preparedLanguages: List<String>? = null

    override fun prepare(languages: List<String>) {
        val resolved = languages.ifEmpty { listOf("eng") }
        // Aynı diller zaten hazırsa yeniden kurulum yapma.
        if (api != null && resolved == preparedLanguages) return

        close()

        val base = TessdataInstaller.ensure(context, resolved)
        val newApi = TessBaseAPI()
        // DİKKAT: Tesseract hazır bir OEM sabiti sunmuyor; 2 parametreli init kullan.
        val initialized = newApi.init(base.absolutePath, resolved.joinToString("+"))
        if (!initialized) {
            newApi.recycle()
            throw IllegalStateException(
                "Tesseract motoru başlatılamadı: ${resolved.joinToString("+")}",
            )
        }

        newApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
        api = newApi
        preparedLanguages = resolved
    }

    override fun recognize(
        pageIndex: Int,
        bitmap: Bitmap,
        renderDpi: Float,
        layout: PageLayout,
    ): PageOcrResult {
        val api = api ?: error("prepare() çağrılmadı")

        // Bitmap verildiğinde Tesseract DPI bilmezse 70 DPI varsayar ve kalite
        // çöker; en az 150 DPI bildir.
        val dpi = maxOf(renderDpi.toInt(), MIN_DPI)
        api.setVariable("user_defined_dpi", dpi.toString())
        api.setPageSegMode(pageSegMode(layout))
        api.setImage(bitmap)

        // getUTF8Text() tanımayı tetikler; metni kullanmasak da çağrılmalı.
        api.getUTF8Text()

        val lines = mutableListOf<OcrLine>()
        val iterator = api.resultIterator
            ?: return emptyResult(pageIndex, bitmap, renderDpi)

        try {
            val lineWords = mutableListOf<OcrWord>()
            var lineBox: Box? = null

            fun flushLine() {
                if (lineWords.isNotEmpty()) {
                    lines += OcrLine(lineWords.toList(), lineBox ?: lineWords.first().box)
                    lineWords.clear()
                    lineBox = null
                }
            }

            iterator.begin()
            do {
                val wordText = iterator.getUTF8Text(RIL_WORD).trim()
                // Satır sınırı kontrolü, kelime okunduktan SONRA yapılır.
                if (iterator.isAtBeginningOf(RIL_TEXTLINE)) flushLine()

                if (wordText.isNotEmpty()) {
                    val box = boundingRect(iterator, RIL_WORD)
                    if (box != null) {
                        lineWords += OcrWord(
                            text = wordText,
                            box = box,
                            // Tesseract güveni 0..100 aralığındadır; olduğu gibi tutulur.
                            confidence = iterator.confidence(RIL_WORD),
                        )
                        lineBox = union(lineBox, box)
                    }
                }
            } while (iterator.next(RIL_WORD))

            flushLine()
        } finally {
            iterator.delete()
        }

        return PageOcrResult(
            pageIndex = pageIndex,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            renderDpi = renderDpi,
            lines = lines,
        )
    }

    override fun close() {
        api?.recycle()
        api = null
        preparedLanguages = null
    }

    /** Kelimenin sınırlayıcı kutusu; `getBoundingRect` yoksa `getBoundingBox`'tan kurar. */
    private fun boundingRect(iterator: ResultIterator, level: Int): Box? {
        iterator.getBoundingRect(level)?.let { return Box(it.left, it.top, it.right, it.bottom) }
        val box = iterator.getBoundingBox(level) ?: return null
        return if (box.size >= 4) Box(box[0], box[1], box[2], box[3]) else null
    }

    /** İki kutunun birleşimi; [a] null ise [b]'nin kopyasını döner. */
    private fun union(a: Box?, b: Box): Box = if (a == null) b else a.union(b)

    /** Sayfa bölütleme tercihini Tesseract PSM sabitine eşler. */
    private fun pageSegMode(layout: PageLayout): Int = when (layout) {
        // Ölçüm: otomatik bölütleme ızgara satırlarında sütun düşürüyor (18'in 11'i);
        // tek blok modu tablonun tamamını okuyor.
        PageLayout.AUTO -> TessBaseAPI.PageSegMode.PSM_AUTO
        PageLayout.TABLE -> TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
    }

    private fun emptyResult(pageIndex: Int, bitmap: Bitmap, renderDpi: Float): PageOcrResult =
        PageOcrResult(
            pageIndex = pageIndex,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            renderDpi = renderDpi,
            lines = emptyList(),
        )

    private companion object {
        /** Tesseract DPI verilmediğinde 70 varsayar; en az 150 bildiriyoruz. */
        const val MIN_DPI = 150

        // Java sabitlerini doğrudan const val'a bağlamak yerine val olarak tut.
        val RIL_WORD = TessBaseAPI.PageIteratorLevel.RIL_WORD
        val RIL_TEXTLINE = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
    }
}
