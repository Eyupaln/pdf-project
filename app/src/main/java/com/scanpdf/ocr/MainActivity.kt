package com.scanpdf.ocr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
 * kurar ve paylaşım intent'lerini başlatır.
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
                    onDpi = viewModel::setDpi,
                    onPreprocess = viewModel::setPreprocess,
                    onDeskew = viewModel::setDeskew,
                    onWriteTextLayer = viewModel::setWriteTextLayer,
                    onShareText = { shareText(state.outcome?.text) },
                    onSharePdf = { sharePdf(state.outcome?.outputPdf) },
                )
            }
        }
    }

    /** Tanınan metni düz metin olarak paylaşır. */
    private fun shareText(text: String?) {
        if (text.isNullOrBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_text)))
    }

    /** Üretilen aranabilir PDF'i paylaşır. */
    private fun sharePdf(file: File?) {
        if (file == null || !file.exists()) return
        val intent = TextExporter.shareUri(this, file, "application/pdf")
        startActivity(Intent.createChooser(intent, getString(R.string.share_pdf)))
    }
}
