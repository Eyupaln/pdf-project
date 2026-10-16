# MetinPDF 0.1.0

Taranmış (resim tabanlı) PDF'leri **aranabilir** ve **kopyalanabilir** hale getiren,
tamamen **cihaz üstünde** çalışan Android uygulaması.

## Öne çıkanlar

- **İki OCR motoru:** Tesseract 5 (`tessdata_best`, Türkçe + İngilizce) ve ML Kit (cihaz üstü)
- **Görünmez metin katmanı:** 300 DPI rasterize → ön işleme → OCR → aranabilir PDF
- **Türkçe karakterler:** Unicode TrueType gömülü olduğu için `ş ğ ı İ ö ü ç` kayıpsız
- **Kendini doğrulayan eğim düzeltme:** döndürme yönü ölçümle doğrulanır; iyileşme yoksa görüntü değişmez
- **Tamamen çevrimdışı:** hiçbir veri cihazdan çıkmaz

## Kurulum

1. Aşağıdaki `MetinPDF-0.1.0-debug.apk` dosyasını indir (telefonda bu sayfayı açıp indirmek en kolayı).
2. Dosyaya dokun → "bilinmeyen kaynaklardan yüklemeye izin ver" → **Kur**.
3. Uygulama adı **MetinPDF** (paket: `com.scanpdf.ocr.debug`).

İlk OCR başlatıldığında dil modelleri (~22 MB) uygulamanın özel dizinine bir kez kopyalanır;
ilk sefer birkaç saniye sürebilir.

**SHA-256** (indirdiğin dosyayı doğrulamak için):

```
1A8431DC026540052420A470EA6F7B30E5FDED04B803327625190288F6075666
```

Windows'ta doğrulama: `Get-FileHash .\MetinPDF-0.1.0-debug.apk -Algorithm SHA256`
Telefonda: herhangi bir SHA-256 hesaplayıcı uygulama.

## Teknik notlar

- Yalnızca `arm64-v8a` + `armeabi-v7a` içerir (gerçek telefonlar). Emülatör (x86_64) için
  `app/build.gradle.kts` içindeki `packaging.jniLibs.excludes` satırını kaldırıp yeniden derle.
- Derleme gereksinimleri: JDK 17+, Android SDK (`platforms;android-37`, `build-tools;37.0.0`).
- Kaynaktan derleme: `gradlew.bat :app:assembleDebug` · Testler: `gradlew.bat :app:testDebugUnitTest`

## Kalite doğrulaması

- 31 JVM birim testi (döndürme koordinat matematiği, ilerleme, motor seçimi, metin birleştirme)
- Döndürme testi gerçek PDFBox çıktısına karşı çalışır: 0/90/180/270 döndürülmüş sayfalar
  çizilip mürekkebin pikseldeki yeri öngörüyle karşılaştırılır
- Testler ve APK, teslim ZIP'inin kendisinden temiz bir klasöre açılıp yeniden derlenerek doğrulandı

## Bilinen sınırlar

- OCR tek bir arka plan iş parçacığında sıralı çalışır; süre sayfa sayısıyla doğrusal artar.
- Rasterize edilen sayfanın uzun kenarı 6000 pikseli aşarsa görüntü küçültülür (etkin DPI düşer).
- ML Kit yalnızca Latin alfabesini tanır ve dil seçimi desteklemez.
- El yazısı metinlerde doğruluk düşer (Tesseract'ın bilinen sınırı).

## Lisans

MIT — bkz. `LICENSE`. Üçüncü taraf bileşenler ve lisansları: `NOTICE`, `THIRD-PARTY-NOTICES.md`, `licenses/`.
