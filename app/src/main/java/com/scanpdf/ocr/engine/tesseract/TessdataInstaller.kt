package com.scanpdf.ocr.engine.tesseract

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream

/**
 * Tesseract dil modeli (`.traineddata`) dosyalarını `assets/tessdata` içinden
 * uygulamanın özel dizinine kurar.
 *
 * Tesseract, dil modelini `tessdata` klasörünün ÜST dizinini vererek arar; bu
 * nedenle [baseDir] üst dizini (`<filesDir>/tesseract`) döndürür ve modeller
 * `<filesDir>/tesseract/tessdata/<dil>.traineddata` altına yazılır.
 */
object TessdataInstaller {

    private const val ASSET_DIR = "tessdata"
    private const val MODEL_SUFFIX = ".traineddata"
    private const val TMP_SUFFIX = ".tmp"

    /** Tesseract kök dizini; `tessdata` alt klasörü de oluşturulur. */
    fun baseDir(context: Context): File {
        val base = File(context.filesDir, "tesseract")
        File(base, ASSET_DIR).mkdirs()
        return base
    }

    /** `assets/tessdata` içindeki hazır `.traineddata` dillerinin adları (uzantısız). */
    fun bundledLanguages(context: Context): List<String> {
        val entries = try {
            context.assets.list(ASSET_DIR) ?: emptyArray()
        } catch (ignored: Exception) {
            return emptyList()
        }
        return entries
            .filter { it.endsWith(MODEL_SUFFIX, ignoreCase = true) }
            .map { it.substringBeforeLast('.') }
            .filter { it.isNotEmpty() }
            .sorted()
    }

    /**
     * İstenen her dil modelini hedefe kurar ve Tesseract kök dizinini döndürür.
     *
     * Hedefte aynı boyutta bir dosya varsa kopyalama atlanır. Eksik bir dil
     * varsa (assets'te yok) [IllegalStateException] fırlatılır.
     */
    fun ensure(context: Context, languages: List<String>): File {
        val base = baseDir(context)
        val tessdataDir = File(base, ASSET_DIR)
        val assets = context.assets

        for (lang in languages) {
            val fileName = "$lang$MODEL_SUFFIX"
            val assetPath = "$ASSET_DIR/$fileName"
            if (!assetExists(assets, assetPath)) {
                throw IllegalStateException("$lang dil modeli bulunamadı (assets/tessdata)")
            }

            val target = File(tessdataDir, fileName)
            if (target.exists()) {
                val assetSize = assetSize(assets, assetPath)
                // Boyut eşleşiyorsa mevcut kurulum geçerlidir; 15 MB'a kadar
                // olan dosyaları tekrar kopyalamaya gerek yok.
                if (assetSize > 0L && target.length() == assetSize) continue
            }
            copyAtomically(assets, assetPath, target)
        }
        return base
    }

    /** Asset'in `tessdata` klasöründe bulunup bulunmadığını ucuz yolla denetler. */
    private fun assetExists(assets: AssetManager, assetPath: String): Boolean {
        val fileName = assetPath.substringAfterLast('/')
        return try {
            assets.list(ASSET_DIR)?.any { it == fileName } == true
        } catch (ignored: Exception) {
            false
        }
    }

    /**
     * Asset'in sıkıştırılmamış boyutunu döner.
     *
     * `openFd` yalnızca sıkıştırılmamış asset'lerde çalışır; sıkıştırılmış bir
     * asset'te akıştan bayt sayarak gerçek boyutu buluruz. Başarısız olursa -1.
     */
    private fun assetSize(assets: AssetManager, assetPath: String): Long {
        try {
            assets.openFd(assetPath).use { return it.length }
        } catch (ignored: Exception) {
            // Sıkıştırılmış asset olabilir; akıştan say.
        }
        return try {
            assets.open(assetPath).use { input ->
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                }
                total
            }
        } catch (ignored: Exception) {
            -1L
        }
    }

    /**
     * Model dosyasını önce `.tmp` olarak yazar, ardından hedefe taşır. Böylece
     * yarıda kalan kopyalama geçerli bir kurulumu bozmaz.
     *
     * Model dosyaları ikili (binary) veridir; bayt akışı olarak kopyalanır.
     */
    private fun copyAtomically(assets: AssetManager, assetPath: String, target: File) {
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        try {
            assets.open(assetPath).use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            if (target.exists() && !target.delete()) {
                throw IllegalStateException("${target.name} güncellenemedi (eski dosya silinemedi)")
            }
            if (!tmp.renameTo(target)) {
                throw IllegalStateException("${target.name} kopyalanamadı (taşıma başarısız)")
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }
}
