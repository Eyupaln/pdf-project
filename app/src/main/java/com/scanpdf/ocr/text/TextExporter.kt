package com.scanpdf.ocr.text

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * OCR çıktısını yerel dosyaya yazar ve paylaşılabilir hâle getirir.
 *
 * Ad ve gövde biçimlerini [TextFormat] üretir (saf Kotlin, test edilebilir);
 * bu nesne yalnızca dosya sistemi ve Android paylaşım ayrıntılarını üstlenir.
 *
 * Üretilen dosyalar:
 *  - `<ad>-metin.txt` — ham metin (UTF-8)
 *  - `<ad>-metin.md` — markdown (UTF-8)
 */
object TextExporter {

    private const val TEXT_SUFFIX = "-metin.txt"
    private const val MARKDOWN_SUFFIX = "-metin.md"
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /** [text]'i `<güvenli ad>-metin.txt` olarak UTF-8 ile yazar ve dosyayı döner. */
    fun writeText(dir: File, baseName: String, text: String): File =
        write(dir, baseName, TEXT_SUFFIX, text)

    /** [markdown]'ı `<güvenli ad>-metin.md` olarak UTF-8 ile yazar ve dosyayı döner. */
    fun writeMarkdown(dir: File, baseName: String, markdown: String): File =
        write(dir, baseName, MARKDOWN_SUFFIX, markdown)

    private fun write(dir: File, baseName: String, suffix: String, content: String): File {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Çıktı dizini oluşturulamadı: ${dir.absolutePath}")
        }
        val file = File(dir, TextFormat.safeFileName(baseName) + suffix)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    /**
     * [file]'ı `ACTION_SEND` ile paylaşan bir intent üretir.
     *
     * URI, `res/xml/file_paths.xml` üzerinden tanımlı `FileProvider` ile alınır;
     * görünüm katmanı bunu `Intent.createChooser` ile gönderebilir. Paylaşım
     * menüsü hem kaydetmeyi (Drive/İndirilenler) hem de başka bir uygulamaya
     * (örneğin bir yapay zekâ sohbetine) göndermeyi mümkün kılar.
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
