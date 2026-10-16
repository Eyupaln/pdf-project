package com.scanpdf.ocr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpdf.ocr.text.TextExporter
import com.scanpdf.ocr.ui.MainScreen
import com.scanpdf.ocr.ui.MainViewModel
import com.scanpdf.ocr.ui.theme.ScanPdfTheme
import java.io.File

/**
 * Tek ekranlı uygulamanın giriş noktası.
 *
 * Durum ve iş yönetimi [MainViewModel]'de; bu sınıf yalnızca Compose ağacını
 * kurar ve çıktı dosyalarının paylaşım intent'lerini başlatır.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScanPdfTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // PDF seçimi ekranda yapılıp buraya bildirilir; başlatma için saklanır.
                var pickedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
                var pickedName by rememberSaveable { mutableStateOf("") }

                MainScreen(
                    state = state,
                    onPick = { uri, name ->
                        pickedUri = uri
                        pickedName = name
                    },
                    onStart = {
                        val uri = pickedUri
                        if (uri != null && !state.running) {
                            viewModel.start(this@MainActivity, uri, pickedName)
                        }
                    },
                    onCancel = viewModel::cancel,
                    onEngine = viewModel::setEngine,
                    onLayout = viewModel::setLayout,
                    onDpi = viewModel::setDpi,
                    onPreprocess = viewModel::setPreprocess,
                    onDeskew = viewModel::setDeskew,
                    onWriteTextLayer = viewModel::setWriteTextLayer,
                    onShareTxt = {
                        shareFile(state.outcome?.outputText, TEXT_MIME, R.string.share_txt)
                    },
                    onShareMd = {
                        shareFile(state.outcome?.outputMarkdown, MARKDOWN_MIME, R.string.share_md)
                    },
                    onSharePdf = {
                        shareFile(state.outcome?.outputPdf, PDF_MIME, R.string.share_pdf)
                    },
                )
            }
        }
    }

    /**
     * Çıktı dosyasını uygun MIME türüyle paylaşır.
     *
     * Paylaşım menüsü hem kaydetmeyi (Drive/İndirilenler) hem de doğrudan başka
     * uygulamaya göndermeyi sağlar; bu yüzden "indirme" ve "paylaşma" aynı
     * yoldan yürür.
     */
    private fun shareFile(file: File?, mime: String, @StringRes chooserTitle: Int) {
        if (file == null || !file.exists()) return
        val intent = TextExporter.shareUri(this, file, mime)
        startActivity(Intent.createChooser(intent, getString(chooserTitle)))
    }

    private companion object {
        const val TEXT_MIME = "text/plain"
        const val MARKDOWN_MIME = "text/markdown"
        const val PDF_MIME = "application/pdf"
    }
}
