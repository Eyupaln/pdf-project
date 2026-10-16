# Üçüncü taraf bileşenler

MetinPDF, aşağıdaki açık kaynak bileşenleri kullanır. Hiçbiri bu projenin
lisansını değiştirmez; her biri kendi lisansı altında kullanılır.

## OCR motorları

| Bileşen | Sürüm | Lisans | Kullanım |
|---|---|---|---|
| [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) | 4.9.0 | Apache-2.0 | Tesseract 5.5.1 + Leptonica 1.85.0 native motoru |
| [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) | 5.5.1 | Apache-2.0 | Motor (`libtesseract.so`) |
| [Leptonica](http://www.leptonica.org/) | 1.85.0 | BSD-2-Clause | Görüntü işleme (`libleptonica.so`) |
| [tessdata_best](https://github.com/tesseract-ocr/tessdata_best) | — | Apache-2.0 | `tur.traineddata`, `eng.traineddata` dil modelleri |
| [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition) | 16.0.1 | Google APIs Terms of Service | Alternatif cihaz üstü OCR motoru |

Not: `tessdata_best` modelleri Apache-2.0 lisanslıdır ve özgün Tesseract
projesinden alınmıştır; dosyalar değiştirilmemiştir.

## PDF

| Bileşen | Sürüm | Lisans | Kullanım |
|---|---|---|---|
| [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) | 2.0.27.0 | Apache-2.0 | Rasterize, belge yazma, Unicode font gömme |
| [Apache PDFBox](https://pdfbox.apache.org/) | 2.0.27 | Apache-2.0 | Yalnızca testlerde (döndürme doğrulaması) |
| [Bouncy Castle](https://www.bouncycastle.org/) | (geçişli) | MIT benzeri (BC lisansı) | PDFBox'un şifreleme desteği |
| [Apache Commons Logging](https://commons.apache.org/proper/commons-logging/) | (geçişli) | Apache-2.0 | PDFBox bağımlılığı |

## Android / AndroidX

| Bileşen | Lisans |
|---|---|
| AndroidX Core, Activity, Lifecycle, DocumentFile | Apache-2.0 |
| Jetpack Compose (UI, Material 3) | Apache-2.0 |
| Kotlin, kotlinx.coroutines | Apache-2.0 |

## Yazı tipi

| Bileşen | Lisans |
|---|---|
| [Noto Sans](https://fonts.google.com/noto/specimen/Noto+Sans) (`assets/fonts/NotoSans-Regular.ttf`) | SIL Open Font License 1.1 — tam metin: [licenses/NotoSans-OFL.txt](licenses/NotoSans-OFL.txt) |

Noto Sans yalnızca üretilen PDF'in metin katmanında kullanılır (Türkçe
karakterlerin kayıpsız kodlanabilmesi için gömülür).

## İnceleme konusu (kod içermez)

KillerPDF, yalnızca **davranış referansı** olarak incelenmiştir (frontend'de
kullanılan motor, DPI, model ve akış tercihleri). Bu proje KillerPDF'in kodunu,
kaynaklarını veya ikili dosyalarını içermez, kopyalamaz ve dağıtmaz.
