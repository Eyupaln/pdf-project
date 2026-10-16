package com.scanpdf.ocr

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Uygulama açılışında PDFBox'un gömülü kaynaklarını (cmap tabloları,
 * standart yazı tipi metrikleri) hazırlar. PDFBox-Android için zorunludur.
 */
class OcrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
