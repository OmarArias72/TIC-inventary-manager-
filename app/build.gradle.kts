plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // compilador de Compose (InventarioScreen.kt)
    alias(libs.plugins.ksp)            // generador de código de Room (DatabaseService.kt)
}

android {
    namespace = "com.example.inventory"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.inventory"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Nota: con AGP 9.x el soporte de Kotlin viene integrado (built-in Kotlin),
    // por eso NO se aplica aquí el plugin org.jetbrains.kotlin.android.
}

dependencies {
    // Base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose — usado en InventarioScreen.kt
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // Room — usado en DatabaseService.kt / ItemInventario.kt
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Apache POI — usado en ExcelService.kt
    implementation(libs.apache.poi.ooxml)

    // ML Kit OCR — usado en OcrService.kt
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services) // para el .await() de Task<T>

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.vanniktech:android-image-cropper:4.6.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")


}