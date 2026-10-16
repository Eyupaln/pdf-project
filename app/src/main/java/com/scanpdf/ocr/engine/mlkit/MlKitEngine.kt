package com.scanpdf.ocr.engine.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scanpdf.ocr.core.OcrEngine
import com.scanpdf.ocr.core.OcrEngineKind
import com.scanpdf.ocr.core.OcrLine
import com.scanpdf.ocr.core.OcrWord
import com.scanpdf.ocr.core.PageOcrResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google ML Kit (cihaz üstü metin tanıma) tabanlı OCR motoru.
 *
 * ML Kit yalnızca Latin alfabesini tanır; Türkçe bu alfabede olduğu için
 * [prepare] dil listesini yok sayar (imza sözleşme gereği korunur).
 */
class MlKitEngine(private val context: Context) : OcrEngine {

    override val kind: OcrEngineKind = OcrEngineKind.MLKIT

    private var recognizer: TextRecognizer? = null

    override fun prepare(languages: List<String>) {
        // Dil seçimi ML Kit tarafından desteklenmez; yalnızca tanıyıcı kurulur.
        // DEFAULT_OPTIONS Latin alfabesi modelini kullanır (Türkçe bu alfabededir).
        recognizer?.close()
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun recognize(pageIndex: Int, bitmap: Bitmap, renderDpi: Float): PageOcrResult {
        val recognizer = recognizer ?: error("prepare() çağrılmadı")

        val image = InputImage.fromBitmap(bitmap, 0)
        // Çağıran taraf bloklayıcı recognize()'ı arka plan iş parçacığından çağırır.
        val text = runBlocking { recognizer.process(image).awaitResult() }

        val lines = mutableListOf<OcrLine>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                appendLine(lines, line)
            }
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
        recognizer?.close()
        recognizer = null
    }

    /** Bir [Text.Line]'ı element (kelime) düzeyinde [lines] listesine ekler. */
    private fun appendLine(lines: MutableList<OcrLine>, line: Text.Line) {
        val words = mutableListOf<OcrWord>()
        var lineBox: Rect? = null

        for (element in line.elements) {
            val elementText = element.text.trim()
            if (elementText.isEmpty()) continue
            val box = element.boundingBox ?: continue
            words += OcrWord(
                text = elementText,
                box = Rect(box),
                // ML Kit güveni 0..1 aralığındadır; Tesseract gibi 0..100'e ölçekle.
                confidence = element.confidence * 100f,
            )
            lineBox = union(lineBox, box)
        }

        if (words.isEmpty()) {
            // Element yoksa kaybı önlemek için satırın kendi metni/kutusuyla tek kelime üret.
            val lineText = line.text.trim()
            val lineRect = line.boundingBox
            if (lineText.isNotEmpty() && lineRect != null) {
                val word = OcrWord(
                    text = lineText,
                    box = Rect(lineRect),
                    confidence = line.confidence * 100f,
                )
                lines += OcrLine(listOf(word), Rect(lineRect))
            }
            return
        }

        lines += OcrLine(words, lineBox ?: words.first().box)
    }

    /** İki kutunun birleşimi; [a] null ise [b]'nin kopyasını döner. */
    private fun union(a: Rect?, b: Rect): Rect =
        if (a == null) Rect(b) else Rect(a).apply { union(b) }
}

/** ML Kit [Task]'ini askıya alarak bekler; ek bağımlılık (play-services-tasks) gerekmez. */
private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
