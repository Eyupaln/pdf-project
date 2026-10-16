package com.scanpdf.ocr.pdf

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import java.util.Collections
import java.util.WeakHashMap

/**
 * Metin katmanı için gömülecek Unicode yazı tipini sağlar.
 *
 * `assets/fonts/NotoSans-Regular.ttf` Latin genişletilmiş aralığı kapsar:
 * Türkçe ş/ğ/ı/İ/ö/ü/ç dâhil olmak üzere Avrupa dilleri ve yaygın noktalama
 * (tırnak, tire, para birimi) desteklenir. Kiril/Arapça/CJK gibi diğer yazı
 * sistemleri bu yazı tipinde **yoktur**; o kod noktaları yazıcı tarafından
 * atlanır (bkz. [PdfTextLayerWriter]).
 *
 * `PDType0Font` bir belgeye bağlıdır; subset yazımı belge kaydedilirken
 * tamamlanır. Bu nedenle her [PDDocument] için tek örnek tutulur ve belge
 * çöp toplayıcıya bırakıldığında önbellek girdisi kendiliğinden düşer
 * (zayıf anahtar).
 */
class PdfFontProvider(private val context: Context) {

    private val fonts: MutableMap<PDDocument, PDFont> = WeakHashMap()

    // Yükleme bir kez başarısız olursa her kelimede yeniden denememek için.
    private val failedDocuments: MutableSet<PDDocument> =
        Collections.newSetFromMap(WeakHashMap<PDDocument, Boolean>())

    private var failureLogged = false

    /**
     * [document] için gömülebilir yazı tipini döner; yükleme başarısızsa `null`.
     * Aynı belge için tek örnek üretilir ve önbelleğe alınır.
     */
    @Synchronized
    fun fontFor(document: PDDocument): PDFont? {
        fonts[document]?.let { return it }
        if (failedDocuments.contains(document)) return null

        return try {
            val font = context.assets.open(FONT_ASSET).use { stream ->
                // embedSubset = true: yalnızca kullanılan glifler gömülür (dosya boyutu küçük kalır).
                PDType0Font.load(document, stream, true)
            }
            fonts[document] = font
            font
        } catch (e: Exception) {
            failedDocuments.add(document)
            if (!failureLogged) {
                failureLogged = true
                Log.w(TAG, "Gömülebilir yazı tipi yüklenemedi ($FONT_ASSET); metin katmanı yazılamayacak.", e)
            }
            null
        }
    }

    companion object {
        private const val TAG = "PdfFontProvider"
        private const val FONT_ASSET = "fonts/NotoSans-Regular.ttf"
    }
}
