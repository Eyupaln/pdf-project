package com.scanpdf.ocr.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanpdf.ocr.core.OcrEngineKind
import com.scanpdf.ocr.core.OcrOptions
import com.scanpdf.ocr.core.OcrOutcome
import com.scanpdf.ocr.core.OcrProgress
import com.scanpdf.ocr.core.PageLayout
import com.scanpdf.ocr.pipeline.OcrPipeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Arayüzün durumunu ve OCR işini yönetir.
 *
 * ViewModel hiçbir `Context` tutmaz; [start] çağrısında verilen bağlamın
 * `applicationContext`'i iş boyunca yerel olarak kullanılır. İş, `cacheDir`
 * altındaki geçici çalışma dizininde yürütülür.
 */
class MainViewModel : ViewModel() {

    /** Arayüzün tek durum kaynağı. */
    data class UiState(
        val running: Boolean = false,
        val progress: OcrProgress? = null,
        val outcome: OcrOutcome? = null,
        val error: String? = null,
        val engine: OcrEngineKind = OcrEngineKind.TESSERACT,
        val dpi: Int = 300,
        val preprocess: Boolean = true,
        val deskew: Boolean = false,
        val writeTextLayer: Boolean = true,
        val languages: List<String> = listOf("tur", "eng"),
        val layout: PageLayout = PageLayout.AUTO,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Çalışan iş; [cancel] ile iptal edilir. */
    private var job: Job? = null

    fun setEngine(engine: OcrEngineKind) = _uiState.update { it.copy(engine = engine) }

    fun setDpi(dpi: Int) = _uiState.update { it.copy(dpi = dpi) }

    fun setPreprocess(value: Boolean) = _uiState.update { it.copy(preprocess = value) }

    fun setDeskew(value: Boolean) = _uiState.update { it.copy(deskew = value) }

    fun setWriteTextLayer(value: Boolean) = _uiState.update { it.copy(writeTextLayer = value) }

    fun setLanguages(languages: List<String>) = _uiState.update { it.copy(languages = languages) }

    fun setLayout(layout: PageLayout) = _uiState.update { it.copy(layout = layout) }

    /**
     * Seçilen PDF için işlem hattını başlatır. Devam eden bir iş varsa yenisini
     * başlatmaz. [onProgress] geri çağrısı arka plan iş parçacığından gelir;
     * durum güncellemesi [MutableStateFlow.update] ile iş parçacığı güvenlidir.
     */
    fun start(context: Context, uri: Uri, displayName: String) {
        if (job?.isActive == true) return

        val appContext = context.applicationContext
        val options = _uiState.value.let { snapshot ->
            OcrOptions(
                engine = snapshot.engine,
                languages = snapshot.languages,
                dpi = snapshot.dpi,
                preprocess = snapshot.preprocess,
                deskew = snapshot.deskew,
                writeTextLayer = snapshot.writeTextLayer,
                layout = snapshot.layout,
            )
        }

        _uiState.update { it.copy(running = true, progress = null, outcome = null, error = null) }
        job = viewModelScope.launch {
            try {
                val workDir = File(appContext.cacheDir, WORK_SUBDIR)
                val outcome = OcrPipeline(appContext).run(
                    inputUri = uri,
                    workDir = workDir,
                    displayName = displayName,
                    options = options,
                    onProgress = { progress -> _uiState.update { it.copy(progress = progress) } },
                )
                _uiState.update { it.copy(running = false, progress = null, outcome = outcome) }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(running = false, progress = null) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(running = false, progress = null, error = e.message ?: "Bilinmeyen hata")
                }
            }
        }
    }

    /** Çalışan işi iptal eder (varsa). */
    fun cancel() {
        job?.cancel()
        job = null
        _uiState.update { it.copy(running = false, progress = null) }
    }

    private companion object {
        const val WORK_SUBDIR = "ocr"
    }
}
