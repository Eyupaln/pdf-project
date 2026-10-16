plugins {
    // AGP 9+ yerleşik Kotlin desteğiyle gelir; AYRICA kotlin.android uygulanmaz.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.scanpdf.ocr"
    // Compose 1.12.x / core-ktx 1.19.x AAR'ları compileSdk 37 ister (AGP 9 AAR metadata denetimi).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scanpdf.ocr"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        // Yalnızca Türkçe + İngilizce kaynaklar (AGP 9: localeFilters)
        localeFilters += listOf("tr", "en")
        // Tesseract dil modelleri sıkıştırılmasın: kurulum sırasında boyut denetimi
        // O(1) olur (openFd çalışır) ve kopyalama hızlanır.
        noCompress += "traineddata"
    }

    testOptions {
        // android.graphics sınıfları birim testlerde "Stub!" yerine varsayılan değer döndürsün
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
        jniLibs {
            // Telefonlar arm'dır. x86/x86_64 yalnızca emülatör içindir ve APK'yı
            // ~35 MB şişirir. Tüm bağımlılıklar arm64-v8a + armeabi-v7a içerir.
            excludes += setOf("lib/x86/**", "lib/x86_64/**")
        }
    }
}

// Not: Yerleşik Kotlin'de jvmTarget, android.compileOptions.targetCompatibility'den (17) türetilir.

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // PDF okuma / rasterize / metin katmanı
    implementation(libs.pdfbox.android)

    // OCR motoru 1 — Tesseract (KillerPDF ile aynı motor)
    implementation(libs.tesseract4android)

    // OCR motoru 2 — ML Kit (cihaz üstü)
    implementation(libs.mlkit.text.recognition)

    // Testler — JVM birim testleri
    testImplementation(libs.junit)
    // Döndürülmüş sayfa koordinatlarını gerçek bir PDF ile doğrulamak için masaüstü PDFBox
    testImplementation(libs.pdfbox.desktop)
    // Testler — cihaz üstü (instrumented) uçtan uca testler
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
}
