package com.scanpdf.ocr.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.scanpdf.ocr.R
import com.scanpdf.ocr.core.OcrEngine
import com.scanpdf.ocr.core.OcrOptions
import com.scanpdf.ocr.core.OcrOutcome
import com.scanpdf.ocr.core.OcrProgress
import com.scanpdf.ocr.core.OcrStage
import com.scanpdf.ocr.core.PageLayout
import com.scanpdf.ocr.core.PageOcrResult
import com.scanpdf.ocr.image.ImagePreprocessor
import com.scanpdf.ocr.pdf.PdfFontProvider
import com.scanpdf.ocr.pdf.PdfRasterizer
import com.scanpdf.ocr.pdf.PdfTextLayerWriter
import com.scanpdf.ocr.text.TextExporter
import com.scanpdf.ocr.text.TextFormat
import com.scanpdf.ocr.text.WordLayout
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * PDF → OCR → aranabilir PDF / ham metin (.txt) / markdown (.md) işlem hattı.
 *
 * Tüm ağır iş (rasterize, ön işleme, OCR) [Dispatchers.Default] üzerinde koşar;
 * yalnızca dosya girdi/çıktısı [Dispatchers.IO]'ya devredilir. [run] askıya
 * alınabilir bir çağrıdır ve iptali her sayfa sınırında denetler; kaynaklar
 * `finally` bloğunda serbest bırakılır.
 */
class OcrPipeline(private val context: Context) {

    suspend fun run(
        inputUri: Uri,
        workDir: File,
        displayName: String,
        options: OcrOptions,
        onProgress: (OcrProgress) -> Unit,
    ): OcrOutcome = withContext(Dispatchers.Default) {
        val startedAt = System.currentTimeMillis()
        val directory = workDir.apply { if (!isDirectory) mkdirs() }
        val tempPdf = File.createTempFile("ocr-input-", ".pdf", directory)

        var engine: OcrEngine? = null
        var document: PDDocument? = null
        try {
            // 1) Belgeyi yerel bir dosyaya kopyala ve aç.
            onProgress(OcrProgress(OcrStage.PREPARING, 0, 0, "PDF okunuyor"))
            copyToFile(inputUri, tempPdf)
            val doc = loadDocument(tempPdf)
            document = doc

            val pageCount = doc.numberOfPages
            if (pageCount <= 0) {
                throw IllegalStateException("PDF boş: hiç sayfa bulunamadı.")
            }

            // 2) OCR motorunu hazırla (ML Kit dil listesini yok sayar).
            currentCoroutineContext().ensureActive()
            val activeEngine = OcrEngine.create(context, options.engine)
            engine = activeEngine
            activeEngine.prepare(options.languages)

            val rasterizer = PdfRasterizer(doc)
            val results = ArrayList<PageOcrResult>(pageCount)

            // 3) Sayfa sayfa rasterize → ön işleme → OCR.
            for (pageIndex in 0 until pageCount) {
                currentCoroutineContext().ensureActive()
                results += recognizePage(pageIndex, pageCount, rasterizer, activeEngine, options, onProgress)
            }

            // Sayfa metinleri iki biçimde kullanılır: `.txt` ham, `.md` başlıklı.
            val pageTexts = results.map { it.text }
            val fullText = TextFormat.rawText(pageTexts)
            // 4) Aranabilir PDF (görünmez metin katmanı) üret.
            val outputPdf: File? = if (options.writeTextLayer) {
                onProgress(OcrProgress(OcrStage.WRITING, pageCount, pageCount, "Aranabilir PDF yazılıyor"))
                val target = File(directory, TextFormat.safeFileName(displayName) + "-aranabilir.pdf")
                withContext(Dispatchers.IO) {
                    val writer = PdfTextLayerWriter(doc, PdfFontProvider(context))
                    writer.writeAll(results)
                    writer.save(target)
                }
                target
            } else {
                null
            }

            // 5) Çıktı dosyaları: ham metin (`.txt`) ve markdown (`.md`).
            //    İkisi birlikte yazılır; kullanıcı sonuç ekranından istediğini indirir.
            onProgress(OcrProgress(OcrStage.SAVING, pageCount, pageCount, "Metin kaydediliyor"))
            val markdown = TextFormat.markdownText(displayName, results) { page ->
                context.getString(R.string.page_header, page)
            }
            val (textFile, markdownFile) = withContext(Dispatchers.IO) {
                TextExporter.writeText(directory, displayName, fullText) to
                    TextExporter.writeMarkdown(directory, displayName, markdown)
            }

            // 6) Bitti.
            onProgress(OcrProgress(OcrStage.DONE, pageCount, pageCount))
            OcrOutcome(
                documentTitle = displayName,
                pageCount = pageCount,
                pages = results,
                text = fullText,
                outputPdf = outputPdf,
                outputText = textFile,
                outputMarkdown = markdownFile,
                engineKind = options.engine,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )
        } finally {
            // İptal edilsek bile kaynakları bırak; CancellationException'ı yutma.
            runCatching { engine?.close() }
            val doc = document
            if (doc != null) {
                withContext(NonCancellable + Dispatchers.IO) { runCatching { doc.close() } }
            }
            if (tempPdf.exists()) tempPdf.delete()
        }
    }

    /**
     * Tek bir sayfayı işler: rasterize, (istenirse) eşikleme ve eğim düzeltme,
     * polarite normalizasyonu, ardından OCR. Yalnızca ön işlemenin ürettiği ara
     * bitmap'ler geri dönüştürülür; ham rasterize çıktısı korunur (aşağı akışta
     * yeniden kullanılabilir).
     */
    private fun recognizePage(
        pageIndex: Int,
        pageCount: Int,
        rasterizer: PdfRasterizer,
        engine: OcrEngine,
        options: OcrOptions,
        onProgress: (OcrProgress) -> Unit,
    ): PageOcrResult {
        onProgress(OcrProgress(OcrStage.RASTERIZING, pageIndex, pageCount))
        val rendered = rasterizer.renderWithScale(pageIndex, options.dpi)
        var working: Bitmap = rendered.bitmap
        val derived = mutableListOf<Bitmap>()

        if (options.preprocess) {
            onProgress(OcrProgress(OcrStage.PREPROCESSING, pageIndex, pageCount, "Eşikleme"))
            val binarized = ImagePreprocessor.binarize(working)
            if (binarized !== working) derived += binarized
            working = binarized
        }

        // Eğim düzeltme: ölçüm, yön doğrulaması ve döndürme [ImagePreprocessor.deskew]
        // içindedir; uygulanan açı metin katmanının ters çevirebilmesi için taşınır.
        var appliedRotation = 0f
        if (options.deskew) {
            onProgress(OcrProgress(OcrStage.PREPROCESSING, pageIndex, pageCount, "Eğim düzeltme"))
            val deskewed = ImagePreprocessor.deskew(working)
            if (deskewed.bitmap !== working) derived += deskewed.bitmap
            working = deskewed.bitmap
            appliedRotation = deskewed.appliedDegrees
        }

        try {
            // Koyu zeminli bantları ters çevir: dönüşümlü polariteli sayfalarda
            // Tesseract bir yarıyı hiç okumuyor (ölçüldü: 18 rakam ve 18 harfin
            // tamamı kayıp). Koordinatlar değişmez.
            val normalized = ImagePreprocessor.normalizePolarity(working)
            if (normalized !== working) derived += normalized
            working = normalized

            onProgress(OcrProgress(OcrStage.RECOGNIZING, pageIndex, pageCount))
            var result = engine.recognize(pageIndex, working, rendered.effectiveDpi, options.layout)

            // Tablo/ızgara sayfalarında otomatik bölütleme sütun düşürüyor
            // (ölçüldü: 18 sütunun 11'i). Tek blok modunu deneyip daha çok içerik
            // okuyan sonucu koru.
            if (options.layout == PageLayout.AUTO && WordLayout.looksTabular(result.lines)) {
                onProgress(
                    OcrProgress(
                        OcrStage.RECOGNIZING,
                        pageIndex,
                        pageCount,
                        context.getString(R.string.progress_table_retry),
                    ),
                )
                val retry = engine.recognize(pageIndex, working, rendered.effectiveDpi, PageLayout.TABLE)
                if (WordLayout.yieldScore(retry.lines) > WordLayout.yieldScore(result.lines)) {
                    result = retry
                }
            }

            return if (appliedRotation == 0f) {
                result
            } else {
                result.copy(appliedRotationDegrees = appliedRotation)
            }
        } finally {
            derived.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /** [uri]'yi akış olarak [target] dosyasına kopyalar (IO dispatcher). */
    private suspend fun copyToFile(uri: Uri, target: File) {
        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Seçilen dosya açılamadı; okuma izni olmayabilir.")
            try {
                input.use { source ->
                    target.outputStream().use { sink -> source.copyTo(sink) }
                }
            } catch (e: IOException) {
                throw IllegalStateException("Seçilen dosya okunamadı.", e)
            }
        }
    }

    /**
     * [file]'ı PDFBox ile açar; şifreli veya bozuk belgeler için anlaşılır bir
     * [IllegalStateException] fırlatır.
     */
    private suspend fun loadDocument(file: File): PDDocument =
        try {
            withContext(Dispatchers.IO) { PDDocument.load(file) }
        } catch (e: IOException) {
            throw IllegalStateException("PDF açılamadı; belge şifreli veya bozuk olabilir.", e)
        }
}
