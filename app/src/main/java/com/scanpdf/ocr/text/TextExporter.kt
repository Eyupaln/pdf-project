package com.scanpdf.ocr.text

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * OCR metnini yerel dosyaya yazar ve paylaşılabilir hâle getirir.
 *
 * Dosya adları kullanıcıya ait belge adından üretildiği için dosya sistemi
 * açısından tehlikeli karakterler tek bir güvenli biçime indirgenir
 * ([safeFileName]); böylece kaydetme hiçbir zaman ad yüzünden başarısız olmaz.
 */
object TextExporter {

    /** Android/Linux dosya adlarında sorun çıkaran karakterler ve kontrol karakterleri. */
    private val ILLEGAL_NAME_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    private const val MAX_NAME_LENGTH = 80
    private const val DEFAULT_NAME = "belge"
    private const val TEXT_SUFFIX = "-metin.txt"
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /**
     * [raw] belge adından dosya sistemi için güvenli bir taban ad üretir.
     *
     * Yasak karakterler `_` olur, ad 80 karaktere kısaltılır ve tamamen boş
     * kalırsa [DEFAULT_NAME] döner.
     */
    fun safeFileName(raw: String): String {
        val cleaned = raw.replace(ILLEGAL_NAME_CHARS, "_").trim().take(MAX_NAME_LENGTH).trim()
        return cleaned.ifBlank { DEFAULT_NAME }
    }

    /**
     * [text]'i `<safe(baseName)>-metin.txt` olarak UTF-8 kodlamayla yazar ve
     * yazılan dosyayı döner. [dir] yoksa oluşturulur.
     */
    fun writeText(dir: File, baseName: String, text: String): File {
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, safeFileName(baseName) + TEXT_SUFFIX)
        file.writeText(text, Charsets.UTF_8)
        return file
    }

    /**
     * [file]'ı `ACTION_SEND` ile paylaşan bir intent üretir.
     *
     * URI, `res/xml/file_paths.xml` üzerinden tanımlı `FileProvider` ile alınır;
     * görünüm katmanı bunu `Intent.createChooser` ile gönderebilir.
     */
    fun shareUri(context: Context, file: File, mime: String): Intent {
        val authority = context.packageName + FILE_PROVIDER_SUFFIX
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(file.name, uri)
        }
    }
}
