package com.scanpdf.ocr.image

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.googlecode.leptonica.android.Binarize
import com.googlecode.leptonica.android.Pix
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.leptonica.android.Skew
import com.googlecode.leptonica.android.WriteFile
import kotlin.math.abs

/**
 * OCR öncesi görüntü iyileştirme yardımcıları (Leptonica tabanlı).
 *
 * Koordinat davranışı:
 *  - [binarize] **koordinatları değiştirmez** (yalnızca piksel değerleri).
 *  - [detectSkew] yalnızca ölçüm yapar; koordinatları değiştirmez.
 *  - [deskew] ve [rotate] **koordinatları değiştirir**; çağıran, döndürülen
 *    görüntü boyutuna göre kutuları telafi etmelidir (bkz. `Rect.rotateAboutCenter`).
 *
 * Tüm Leptonica `Pix` nesneleri `try/finally` içinde geri dönüştürülür.
 */
object ImagePreprocessor {

    private const val TAG = "ImagePreprocessor"

    /** Bu aralığın altındaki açı önemsiz, üstündeki açı tespit hatası sayılır. */
    private const val MIN_DESKEW_DEGREES = 0.3f
    private const val MAX_DESKEW_DEGREES = 10f

    /**
     * Belge görüntüleri için Otsu'ya göre daha dayanıklı, yerel (Sauvola)
     * eşikleme uygular. Hata durumunda girdiyi aynen döner.
     */
    fun binarize(src: Bitmap): Bitmap {
        var input: Pix? = null
        var output: Pix? = null
        try {
            val pix = ReadFile.readBitmap(src) ?: return src
            input = pix
            val binary = Binarize.sauvolaBinarizeTiled(pix) ?: return src
            output = binary
            return WriteFile.writeBitmap(binary) ?: src
        } catch (e: Exception) {
            Log.w(TAG, "Binarize başarısız; özgün görüntü döndürülüyor.", e)
            return src
        } finally {
            output?.recycle()
            input?.recycle()
        }
    }

    /**
     * Görüntünün eğim açısını derece cinsinden döner (saat yönünün tersi pozitif).
     * Hata durumunda 0 döner.
     */
    fun detectSkew(src: Bitmap): Float {
        val pix = try {
            ReadFile.readBitmap(src)
        } catch (e: Exception) {
            Log.w(TAG, "Eğim tespiti başarısız.", e)
            return 0f
        } ?: return 0f

        return try {
            Skew.findSkew(pix)
        } catch (e: Exception) {
            Log.w(TAG, "Eğim tespiti başarısız.", e)
            0f
        } finally {
            pix.recycle()
        }
    }

    /**
     * Eğim düzeltme sonucu: [bitmap] yeni görüntü, [appliedDegrees] uygulanan
     * döndürme (0 ise geometri değişmedi).
     */
    data class DeskewResult(val bitmap: Bitmap, val appliedDegrees: Float)

    /**
     * Eğimi ölçer ve anlamlıysa düzeltir.
     *
     * `Skew.findSkew` işaret kuralına körlemesine güvenilmez: bir yönde
     * döndürdükten sonra eğim yeniden ölçülür ve düzelmenin gerçekleştiği
     * doğrulanır. Yanlış yönde döndürmek eğimi iki katına çıkarıp OCR'ı
     * bozacağı için hiçbir yön iyileştirme sağlamıyorsa görüntü DEĞİŞTİRİLMEZ.
     *
     * Döndürme uygulandıysa dönen [DeskewResult.appliedDegrees] sıfırdan
     * farklıdır; çağıran bunu `PageOcrResult.appliedRotationDegrees` olarak
     * taşımalıdır (metin katmanı tersini uygular).
     */
    fun deskew(src: Bitmap): DeskewResult {
        val degrees = detectSkew(src)
        if (!degrees.isFinite() || abs(degrees) < MIN_DESKEW_DEGREES || abs(degrees) > MAX_DESKEW_DEGREES) {
            return DeskewResult(src, 0f)
        }

        val forward = rotate(src, -degrees)
        if (improves(forward, degrees)) return DeskewResult(forward, -degrees)

        val backward = rotate(src, degrees)
        if (improves(backward, degrees)) {
            recycleIfDerived(forward, src)
            return DeskewResult(backward, degrees)
        }

        // Ölçüm belirsiz: hiçbir yön iyileştirmedi, özgün görüntü korunur.
        recycleIfDerived(forward, src)
        recycleIfDerived(backward, src)
        return DeskewResult(src, 0f)
    }

    /** [candidate] görüntüsündeki kalan eğim, düzeltme öncesinden küçük mü? */
    private fun improves(candidate: Bitmap, before: Float): Boolean {
        val residual = detectSkew(candidate)
        return residual.isFinite() && abs(residual) < abs(before)
    }

    /** Yalnızca [src]'den farklı (türev) görüntüleri geri dönüştürür. */
    private fun recycleIfDerived(bitmap: Bitmap, src: Bitmap) {
        if (bitmap !== src && !bitmap.isRecycled) bitmap.recycle()
    }

    /**
     * [src] görüntüsünü **aynı boyutta** tutarak merkez etrafında [degrees] kadar
     * döndürür. Kelime kutuları bu işlemle taşınmaz; çağıran ters döndürmeyi
     * uygulamalıdır. [degrees] 0 ise [src] aynen döner.
     */
    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val width = src.width
        val height = src.height
        if (width <= 0 || height <= 0) return src

        val matrix = Matrix()
        matrix.setRotate(degrees, width / 2f, height / 2f)
        return try {
            Bitmap.createBitmap(src, 0, 0, width, height, matrix, true)
        } catch (e: Exception) {
            Log.w(TAG, "Döndürme başarısız; özgün görüntü döndürülüyor.", e)
            src
        }
    }
}
