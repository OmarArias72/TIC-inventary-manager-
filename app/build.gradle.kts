plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // compilador de Compose
    alias(libs.plugins.ksp)            // generador de código (Room y Hilt)
    alias(libs.plugins.hilt)           // inyección de dependencias
}

android {
    namespace = "com.example.inventory"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.inventory"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        buildConfig = true // BuildConfig.DEBUG se usa para el log de red
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Con AGP 9.x el soporte de Kotlin viene integrado (built-in Kotlin),
    // por eso NO se aplica aquí el plugin org.jetbrains.kotlin.android.
}

// Room exporta el esquema de cada versión a app/schemas. Hay que subir esa carpeta a git:
// sirve para escribir y probar las migraciones futuras.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose y ciclo de vida
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm) // Hilt + Kotlin 2.4 (ver libs.versions.toml)
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel()
    implementation(libs.androidx.hilt.work)               // @HiltWorker
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Red
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Corrutinas
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services) // .await() de las Task de ML Kit

    // Excel, OCR y recorte
    implementation(libs.apache.poi.ooxml)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.image.cropper)
    implementation(libs.androidx.exifinterface) // orientación de la foto (EXIF)

    // Pruebas
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
