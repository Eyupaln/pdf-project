# MetinPDF

Taranmış (resim tabanlı) PDF'leri **aranabilir** ve **kopyalanabilir** hale getiren,
tamamen **cihaz üstünde** çalışan açık kaynak bir Android uygulaması.

Amaç, masaüstündeki KillerPDF'in OCR **yaklaşımını** Android'de yakalamak: aynı
açık kaynak motor (Tesseract 5 + Leptonica), aynı dil modelleri (`tessdata_best`)
ve benzer bir akış (yüksek DPI rasterize → ön işleme → OCR → görünmez metin
katmanı).

Bu proje **bağımsız** bir uygulamadır. KillerPDF ile ilişkili değildir; onun
kodu, kaynağı veya ikili dosyalarını içermez ve ondan türetilmemiştir. KillerPDF
yalnızca davranış referansı olarak incelenmiştir (bkz.
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)).

## Ne yapar

1. Seçilen PDF'in her sayfasını **300 DPI**'da görüntüye çevirir (PDFBox).
2. İsteğe bağlı **ön işleme**: Sauvola eşikleme, kendini doğrulayan eğim düzeltme.
3. **Polarite normalizasyonu**: koyu zeminli bantlar (ör. cevap anahtarı başlıkları)
   yerinde ters çevrilir; aksi hâlde Tesseract polarite kararını tüm sayfa için
   verdiği için bant içeriği tamamen kaybolur.
4. **OCR**: Tesseract (`tessdata_best`, `tur` + `eng`) veya ML Kit (cihaz üstü).
   Tablo/ızgara sayfaları otomatik algılanıp tek blok modunda yeniden okunur.
5. Sayfa metnini gösterir; **seçilebilir/kopyalanabilir** ve dosya olarak
   **indirilebilir/paylaşılabilir**.
6. Üç çıktı üretir:
   - `<ad>-aranabilir.pdf` — orijinal sayfanın üzerine **görünmez metin katmanı**
     yazılmış aranabilir PDF,
   - `<ad>-metin.txt` — sayfa metinleri **ham** hâlde (aralarında bir boş satır,
     hiçbir başlık yok); bir yapay zekâ sohbetine doğrudan verilebilir,
   - `<ad>-metin.md` — `# belge adı` ve `## Sayfa N` başlıkları; **tablolar gerçek
     markdown tablosuna** çevrilir (düz metinde hücre-sütun ilişkisi kaybolur).

## Kalite neden "gerçek"

Kalite tek bir sihirli ayardan gelmez; bunlar birlikte gerekir:

| Etken | Uygulama |
|---|---|
| **Model** | `tessdata_best` — KillerPDF'in kullandığı modellerin aynısı |
| **Çözünürlük** | 300 DPI rasterize; Tesseract'a `user_defined_dpi` açıkça bildirilir (aksi halde 70 DPI varsayıp kalite çöker) |
| **Ön işleme** | Sauvola eşikleme + yönü doğrulanan eğim düzeltme |
| **Polarite** | Koyu zeminli bantlar yerinde ters çevrilir (koyu zemin + beyaz yazı, Tesseract'ta o satırları tamamen düşürür) |
| **Bölütleme** | Tablo/ızgara sayfaları otomatik algılanır ve tek blok modunda yeniden okunur (varsayılan mod ızgara satırlarının bir bölümünü düşürür) |
| **Metin katmanı** | Kelime kutuları sayfa `/Rotate` değerine (0/90/180/270) göre sayfa uzayına eşlenir; Unicode TrueType gömülür (Standart-14 fontlar `ş ğ ı İ` yazamaz) |

## Proje yapısı

```
app/src/main/java/com/scanpdf/ocr/
├── core/        Modeller ve sözleşmeler (Box, OcrEngine, OcrOptions/PageLayout, PageOcrResult)
├── engine/      tesseract/ (TessdataInstaller, TesseractEngine), mlkit/
├── pdf/         PdfRasterizer, PdfFontProvider, PdfTextLayerWriter, PageGeometry
├── image/       ImagePreprocessor (Leptonica: eşikleme, eğim), PolarityBands (koyu bant tespiti)
├── pipeline/    OcrPipeline (rasterize → ön işleme → polarite → OCR → metin katmanı)
├── text/        TextFormat (ad, .txt/.md gövdesi), WordLayout (tablo yapısı), TextExporter
└── ui/          MainScreen, MainViewModel (Compose)
```

Bağımlılıklar içe doğru akar: `ui` → `pipeline` → `engine`/`pdf`/`image` → `core`.

### Döndürülmüş sayfa eşlemesi (`PageGeometry`)

PDF `/Rotate` sayfayı **görüntülerken saat yönünde** döndürür; görüntü koordinatı
yukarıdan aşağı, sayfa uzayı alttan yukarı ölçülür. Ters eşleme:

| r | sayfa x | sayfa y | metin açısı |
|---|---|---|---|
| 0 | dx | H − dy | 0° |
| 90 | dy | dx | +90° |
| 180 | W − dx | dy | 180° |
| 270 | W − dy | H − dx | −90° |

Bu eşleme `PageGeometryRenderingTest` ile **gerçek PDFBox çıktısına** karşı
doğrulanır (test, döndürülmüş sayfayı çizip mürekkebin pikseldeki yerini
öngörüyle karşılaştırır).

## Derleme

Gereksinimler: **JDK 17+** (JDK 21 ile doğrulandı), Android SDK
(`platforms;android-37`, `build-tools;37.0.0`), Gradle 9.8+.

```bash
# local.properties içine SDK yolunu yazın
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

gradle :app:assembleDebug       # APK: app/build/outputs/apk/debug/app-debug.apk
```

`app-debug.apk` dosyasını telefona kopyalayıp kurun ("bilinmeyen kaynaklardan
kurulum" izni gerekir). Uygulama ilk açılışta dil modellerini varlıklardan
uygulama dizinine kopyalar (~22 MB, bir kez).

Not: `ndk` mimarisi için APK yalnızca `arm64-v8a` ve `armeabi-v7a` içerir
(gerçek telefonlar). Emülatör (x86_64) için `app/build.gradle.kts` içindeki
`packaging.jniLibs.excludes` satırını kaldırın.

## Testler

```bash
gradle :app:testDebugUnitTest    # JVM birim testleri (rapor: app/build/reports/tests/)
```

JVM testleri şunları kapsar:

- `PageGeometryTest` — döndürme eşlemesi: köşe eşlemeleri, metin açıları,
  gidiş-dönüş tutarlılığı, taşma denetimi.
- `PageGeometryRenderingTest` — **gerçek PDF ile kanıt**: 0/90/180/270 döndürülmüş
  sayfa çizilir, PDFBox ile görüntülenir ve mürekkebin piksel konumu öngörüyle
  karşılaştırılır (testte masaüstü PDFBox kullanılır; uygulamadaki
  pdfbox-android onun portudur).
- `TextFormatTest` — çıktı biçimleri: `.txt` ve `.md` gövdeleri tam metin
  karşılaştırmasıyla sabitlenir; dosya adı güvenliği (yasak karakterler, uzunluk
  sınırı, boş ad), sayfa başlıkları, sayfa numarası sırası, tablo → markdown tablosu.
- `WordLayoutTest` — tablo yapısı: hücre ayırma eşiği, ızgara satırı tespiti,
  seçenek satırının tablo sayılmaması, tablo bloğu sütun doldurma.
- `PolarityBandsTest` — koyu bant tespiti ve yerinde ters çevirme (alfa korunur).
- `TableRecognitionTest` — **gerçek Tesseract ile kanıt** (Tess4J + uygulamanın
  `assets/tessdata` modelleri): cevap anahtarı sayfasının benzeri üretilir; koyu
  bantlı hâlde numara satırı okunamaz, polarite normalizasyonundan sonra cevap
  harfleri ve numara kuyruğu okunur ve markdown tablosu üretilir.
- `BoxTest` — saf geometri: ölçüler, örtüşme, birleşim, döndürme.
- `PipelineTypesTest`, `ModelsTest` — seçenekler, ilerleme oranı, motor seçimi,
  metin birleştirme, güven ortalaması.

Cihaz üstü (instrumented) testler `app/src/androidTest/` altındadır ve bağlı bir
telefon gerektirir:

```bash
gradle :app:connectedDebugAndroidTest
```

## Sınırlar

- OCR ve PDF işleme tek bir arka plan iş parçacığında sıralıdır (Tesseract
  iş parçacığı güvenli değildir); büyük belgelerde süre sayfa sayısıyla artar.
- Çok büyük kareli sayfalar için uzun kenar 6000 piksel ile sınırlanır
  (bellek koruması); bu durumda DPI etkin olarak düşer.
- ML Kit yalnızca Latin alfabesini tanır ve dil seçimini desteklemez.
- Taranmış el yazısı için doğruluk düşer (Tesseract'ın bilinen sınırı).

## Lisans

MIT — bkz. [LICENSE](LICENSE). Üçüncü taraf bileşenler ve lisansları için
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
