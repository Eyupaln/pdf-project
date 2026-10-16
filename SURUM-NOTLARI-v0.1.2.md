# MetinPDF 0.1.2 — Sürüm Notları

**Tarih:** 17 Ekim 2026
**APK:** `MetinPDF-0.1.2-debug.apk` (arm64-v8a + armeabi-v7a)
**Paket:** `com.scanpdf.ocr.debug` (debug yapısı)

## Bu sürümün konusu: tablo/cevap anahtarı sayfaları okunmuyordu

Koyu başlık bandı (koyu zemin + beyaz yazı) ve ızgara satırları olan sayfalarda
numara/harf satırları tamamen kayboluyordu. Ölçüm ve düzeltme üç ayaklıdır.

### 1) Polarite normalizasyonu (asıl neden)

Tesseract, sayfanın polaritesini (siyah mı beyaz mı zemin) **tüm sayfa için tek
karar** olarak verir. Koyu bantlar sayfanın küçük bir bölümü olduğu için karar
beyaz zemin lehine çıkıyor ve bant içeriği (beyaz yazı) tamamen düşüyordu.

Artık sayfa, **satır satır koyu-piksel oranına** göre taranır; koyu bant olarak
tespit edilen bölgeler **yerinde** (aynı koordinatlarda) ters çevrilir. Böylece
sayfada tek polarite kalır, bant içeriği normal siyah-yazı hâline gelir ve koyu
bantta **hiçbir şey okunmazken** cevap harfleri ile numara satırı okunur.
Bu adım `image/PolarityBands.kt` içinde saf Kotlin olarak yazıldı ve birim
testleriyle sabitlendi.

### 2) Tablo düzeninde otomatik yeniden okuma

Tesseract'ın varsayılan otomatik bölütlemesi ızgara satırlarının sonundaki
sütunları düşürüyordu (ölçümde 12–18. sütunlar kayboldu). Pipeline, kelime
kutularına bakarak sayfanın **ızgara/tablo gibi** göründüğünü anlar; yalnızca
bu durumda sayfa **tek blok** modunda yeniden okunur ve iki sonuçtan **daha çok
içerik veren** korunur. Arayüzde "Düzen: Otomatik / Tablo" seçimi bulunur;
"Otomatik" varsayılandır ve normal sayfalarda ek maliyet doğurmaz.

### 3) `.md` çıktısında gerçek markdown tablosu

Kelime kutuları sütunlara bölünür (`text/WordLayout.kt`) ve tablo blokları
gerçek markdown tablosuna çevrilir:

```
| 1 | 2 | 3 | ... | 18 |
| --- | --- | --- | ... | --- |
| C | C | E | ... | D |
```

`.txt` çıktısı ham satırları vermeye devam eder (kopyala-yapıştır için).
Düz metinde kaybolan hücre–sütun ilişkisi `.md`'de korunur.

### 4) İç düzen

- `android.graphics.Rect` yerine saf `core/Box.kt` tipi kullanılır; polarite ve
  tablo algoritmaları Android'siz JVM'de test edilebilir.
- Ölçüm ve doğrulama **gerçek Tesseract** (Tess4J + uygulamanın `assets/tessdata`
  modelleri) ile yapılır; test sayfası kod içinde üretilir (dış dosya/telif yok).

## Doğrulama

- `gradle :app:assembleDebug :app:testDebugUnitTest` → **BUILD SUCCESSFUL**
- **80/80** JVM testi geçti. Yeni testler: `BoxTest`, `PolarityBandsTest`,
  `WordLayoutTest` ve **gerçek Tesseract ile** `TableRecognitionTest`.
- `TableRecognitionTest` ölçtüğü davranış: koyu bantlı sayfada polarite
  normalizasyonu olmadan numara satırı okunmaz; normalizasyondan sonra cevap
  harfleri satırı 18/18 hücre ile ve numara satırının kuyruğu (iki basamaklı
  sayılar) okunur, markdown tablosu üretilir.

## Kurulum

1. `MetinPDF-0.1.2-debug.apk` dosyasını telefona aktarın.
2. Dosyayı açın, "bilinmeyen kaynaklardan kurulum" iznini onaylayın.
3. Dil modelleri (~22 MB) yalnızca ilk OCR işleminde bir kez kopyalanır.

Ayrıntılı kullanım ve doğrulama adımları: `KURULUM.txt`.

## Bilinen sınırlar

- Tek karakterlik hücrelerde (koyu bantta dar sütunlar) hücre çerçeve çizgileri
  eşiklemeden sonra gliflerle birleşebilir; bu durumda tek rakam yanlış
  okunabilir. İki basamaklı sayılar ve cevap harfleri güvenilir okunur;
  "Düzen: Tablo" bu sayfalarda yardımcı olur.
- OCR tek iş parçacığında sıralı çalışır; süre sayfa sayısıyla artar.
- Uzun kenarı 6000 pikseli aşan sayfalar bellek koruması için küçültülür.
- ML Kit yalnızca Latin alfabesini tanır; dil seçimini desteklemez.
- El yazısı metinlerde doğruluk düşer (Tesseract'ın bilinen sınırı).

## Önceki sürüm

- 0.1.1 — `.txt` **ve** `.md` çıktısı, ayrı indirme/paylaşma düğmeleri.
- 0.1.0 — ilk sürüm: 300 DPI rasterize, Sauvola eşikleme, eğim düzeltme,
  `tessdata_best` (Türkçe + İngilizce) veya ML Kit, aranabilir PDF.
