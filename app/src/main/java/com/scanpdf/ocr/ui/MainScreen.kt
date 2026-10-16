package com.scanpdf.ocr.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanpdf.ocr.R
import com.scanpdf.ocr.core.OcrEngineKind
import com.scanpdf.ocr.core.OcrOutcome
import com.scanpdf.ocr.core.OcrProgress
import com.scanpdf.ocr.core.PageLayout
import com.scanpdf.ocr.core.PageOcrResult
import java.util.Locale
import kotlin.math.roundToInt

/** DPI kaydırıcısının sınırları ve adımı. */
private val DPI_RANGE = 150f..400f
private const val DPI_STEP = 50
private const val DPI_STEPS = ((400 - 150) / DPI_STEP) - 1

/**
 * Uygulamanın tek ekranı.
 *
 * Seçim ve ayarlar üstte, ilerleme ve sonuç altta olacak şekilde tek bir
 * `LazyColumn` içinde toplanmıştır. PDF seçimi burada başlatılır ve çözülen
 * belge adı [onPick] ile dışarı bildirilir; işlem [onStart] ile tetiklenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainViewModel.UiState,
    onPick: (Uri, String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onEngine: (OcrEngineKind) -> Unit,
    onLayout: (PageLayout) -> Unit,
    onDpi: (Int) -> Unit,
    onPreprocess: (Boolean) -> Unit,
    onDeskew: (Boolean) -> Unit,
    onWriteTextLayer: (Boolean) -> Unit,
    onShareTxt: () -> Unit,
    onShareMd: () -> Unit,
    onSharePdf: () -> Unit,
) {
    val context = LocalContext.current
    val fallbackName = stringResource(R.string.default_document_name)

    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedName by rememberSaveable { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = displayNameOf(context, uri, fallbackName)
            selectedUri = uri
            selectedName = name
            onPick(uri, name)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { insets ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                DocumentSection(
                    fileName = selectedName,
                    hasSelection = selectedUri != null,
                    onPick = { pdfPicker.launch(arrayOf("application/pdf")) },
                )
            }
            item { EngineSection(state.engine, onEngine) }
            item { LayoutSection(state.layout, onLayout) }
            item { DpiSection(state.dpi, onDpi) }
            item {
                OptionsSection(
                    preprocess = state.preprocess,
                    deskew = state.deskew,
                    writeTextLayer = state.writeTextLayer,
                    onPreprocess = onPreprocess,
                    onDeskew = onDeskew,
                    onWriteTextLayer = onWriteTextLayer,
                )
            }
            item {
                ActionSection(
                    running = state.running,
                    canStart = selectedUri != null,
                    onStart = onStart,
                    onCancel = onCancel,
                )
            }
            state.progress?.let { progress -> item { ProgressSection(progress) } }
            state.error?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.outcome?.let { outcome ->
                item { SummarySection(outcome, onShareTxt, onShareMd, onSharePdf) }
                itemsIndexed(
                    items = outcome.pages,
                    key = { _, page -> page.pageIndex },
                ) { index, page ->
                    PageTextSection(page = page, position = index + 1)
                }
            }
        }
    }
}

/** PDF seçme düğmesi ve seçili dosya adı. */
@Composable
private fun DocumentSection(fileName: String, hasSelection: Boolean, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pick_pdf))
        }
        Text(
            text = if (hasSelection) fileName else stringResource(R.string.no_pdf_selected),
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasSelection) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** OCR motoru seçimi, diller ve ML Kit uyarısı. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineSection(engine: OcrEngineKind, onEngine: (OcrEngineKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.engine), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineChip(OcrEngineKind.TESSERACT, engine, onEngine, R.string.engine_tesseract_short)
            EngineChip(OcrEngineKind.MLKIT, engine, onEngine, R.string.engine_mlkit_short)
        }
        Text(
            text = stringResource(
                if (engine == OcrEngineKind.MLKIT) {
                    R.string.engine_mlkit_desc
                } else {
                    R.string.engine_tesseract_desc
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.languages_value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (engine == OcrEngineKind.MLKIT) {
            Text(
                text = stringResource(R.string.engine_mlkit_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Sayfa düzeni (bölütleme) seçimi: tablo/ızgara sayfaları için tek blok modu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutSection(layout: PageLayout, onLayout: (PageLayout) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.layout_label), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LayoutChip(PageLayout.AUTO, layout, onLayout, R.string.layout_auto)
            LayoutChip(PageLayout.TABLE, layout, onLayout, R.string.layout_table)
        }
        Text(
            text = stringResource(R.string.layout_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutChip(
    value: PageLayout,
    selected: PageLayout,
    onSelect: (PageLayout) -> Unit,
    labelRes: Int,
) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineChip(
    kind: OcrEngineKind,
    selected: OcrEngineKind,
    onSelect: (OcrEngineKind) -> Unit,
    labelRes: Int,
) {
    FilterChip(
        selected = kind == selected,
        onClick = { onSelect(kind) },
        label = { Text(stringResource(labelRes)) },
    )
}

/** Çözünürlük kaydırıcısı (150..400 DPI, 50'şer adım). */
@Composable
private fun DpiSection(dpi: Int, onDpi: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.dpi_value, dpi),
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = dpi.toFloat(),
            onValueChange = { onDpi(it.roundToInt()) },
            valueRange = DPI_RANGE,
            steps = DPI_STEPS,
        )
    }
}

/** İşleme anahtarları. */
@Composable
private fun OptionsSection(
    preprocess: Boolean,
    deskew: Boolean,
    writeTextLayer: Boolean,
    onPreprocess: (Boolean) -> Unit,
    onDeskew: (Boolean) -> Unit,
    onWriteTextLayer: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SwitchRow(stringResource(R.string.preprocess_switch), preprocess, onPreprocess)
        SwitchRow(stringResource(R.string.deskew_switch), deskew, onDeskew)
        SwitchRow(stringResource(R.string.write_text_layer_switch), writeTextLayer, onWriteTextLayer)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Başlat/İptal düğmesi. Dosya seçilmeden başlatma devre dışıdır. */
@Composable
private fun ActionSection(running: Boolean, canStart: Boolean, onStart: () -> Unit, onCancel: () -> Unit) {
    if (running) {
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    } else {
        Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.extract_text_start))
        }
    }
}

/** İlerleme çubuğu, aşama etiketi ve sayfa sayacı. */
@Composable
private fun ProgressSection(progress: OcrProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress.fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(text = progress.stage.label, style = MaterialTheme.typography.bodyMedium)
        if (progress.pageCount > 0) {
            Text(
                text = stringResource(R.string.progress_page, progress.pageIndex + 1, progress.pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (progress.message.isNotBlank()) {
            Text(
                text = progress.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ortalama güven, süre ve çıktı dosyası (TXT/MD/PDF) paylaşım düğmeleri. */
@Composable
private fun SummarySection(
    outcome: OcrOutcome,
    onShareTxt: () -> Unit,
    onShareMd: () -> Unit,
    onSharePdf: () -> Unit,
) {
    val meanConfidence = if (outcome.pages.isEmpty()) {
        0f
    } else {
        outcome.pages.map { it.meanConfidence }.average().toFloat()
    }
    val hasText = outcome.text.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.summary_confidence, formatPercent(meanConfidence)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.summary_duration, formatSeconds(outcome.elapsedMs / 1000.0)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onShareTxt,
                enabled = hasText,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.share_txt))
            }
            Button(
                onClick = onShareMd,
                enabled = hasText,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.share_md))
            }
        }
        Button(
            onClick = onSharePdf,
            enabled = outcome.outputPdf != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.share_pdf))
        }
    }
}

/** Bir sayfanın seçilebilir metni. */
@Composable
private fun PageTextSection(page: PageOcrResult, position: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.page_header, position),
            style = MaterialTheme.typography.titleSmall,
        )
        if (page.text.isBlank()) {
            Text(
                text = stringResource(R.string.page_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SelectionContainer {
                Text(text = page.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Belge adını `OpenableColumns.DISPLAY_NAME` üzerinden çözer; bulunamazsa [fallback]. */
private fun displayNameOf(context: Context, uri: Uri, fallback: String): String {
    val queried = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
    }.getOrNull()

    if (!queried.isNullOrBlank()) return queried
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: fallback
}

/** Güven değerini (0..100) yüzde metnine çevirir. */
private fun formatPercent(value: Float): String =
    String.format(Locale.getDefault(), "%.1f%%", value)

/** Saniye değerini tek ondalıklı metne çevirir. */
private fun formatSeconds(seconds: Double): String =
    String.format(Locale.getDefault(), "%.1f", seconds)
