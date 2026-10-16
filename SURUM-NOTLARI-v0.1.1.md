# MetinPDF 0.1.1 — Sürüm Notları

**Tarih:** 16 Ekim 2026
**APK:** `MetinPDF-0.1.1-debug.apk` (arm64-v8a + armeabi-v7a, ~74 MB)
**Paket:** `com.scanpdf.ocr.debug` (debug yapısı)

## Yenilikler

### 1) Çıktı artık iki biçimde üretiliyor: `.txt` **ve** `.md`

Bir PDF işlendiğinde metin iki dosyaya birden yazılır:

| Dosya | İçerik |
|---|---|
| `<belge adı>-metin.txt` | Sayfa metinleri **ham** hâlde; aralarında yalnızca bir boş satır. Hiçbir başlık, numara veya işaret eklenmez. |
| `<belge adı>-metin.md` | Aynı metin, markdown olarak: başta `# <belge adı>`, birden çok sayfa varsa her sayfanın başında `## Sayfa N` başlığı. Sayfa metinleri yine değiştirilmez. |

İkisi de UTF-8'dir; `.md` yalnızca **sayfa sınırlarını görünür** kılar, metni
değiştirmez. Yapay zekâya metni verirken hangisini isterseniz onu kullanabilirsiniz.

### 2) Sonuç ekranında ayrı indirme/paylaşma düğmeleri

Eskiden tek "Metni paylaş" düğmesi vardı; artık üç ayrı düğme var:

- **TXT olarak indir/paylaş** → `<belge adı>-metin.txt`
- **MD olarak indir/paylaş** → `<belge adı>-metin.md`
- **PDF'i paylaş** → `<belge adı>-aranabilir.pdf`

Her düğme Android paylaşım menüsünü açar. Menüden dosyayı **Drive/İndirilenler'e
kaydedebilir** (indirme) veya doğrudan bir uygulamaya — örneğin bir yapay zekâ
sohbetine — gönderebilirsiniz.

### 3) Küçük iyileştirmeler

- Çıktı dizini oluşturulamazsa artık sessizce başarısız olmak yerine anlaşılır bir
  hata verilir.
- Dosya adı ve biçim üretimi saf Kotlin'e (`TextFormat`) ayrıldı; çıktı biçimleri
  birim testleriyle birebir sabitlendi.
- Kullanılmayan arayüz metinleri (Türkçe + İngilizce) kaldırıldı.

## Doğrulama

- `gradle :app:assembleDebug :app:testDebugUnitTest` → **BUILD SUCCESSFUL**
- **50/50** JVM birim testi geçti (yeni: `TextFormatTest`, 19 test)

## Kurulum

1. `MetinPDF-0.1.1-debug.apk` dosyasını telefona aktarın.
2. Dosyayı açın, "bilinmeyen kaynaklardan kurulum" iznini onaylayın.
3. İlk OCR işleminde dil modelleri (~22 MB) bir kez kopyalanır.

Ayrıntılı kullanım ve test adımları: `KURULUM.txt`.

## Bilinen sınırlar

- OCR tek iş parçacığında sıralı çalışır; büyük belgelerde süre sayfa sayısıyla artar.
- Uzun kenarı 6000 pikseli aşan sayfalar bellek koruması için küçültülür.
- ML Kit yalnızca Latin alfabesini tanır; dil seçimini desteklemez.
- El yazısı metinlerde doğruluk düşer (Tesseract'ın bilinen sınırı).
