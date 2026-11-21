import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val googleServicesFile: File = project.file("google-services.json")
if (!googleServicesFile.exists()) {
    val userHome = System.getProperty("user.home")
    val verifiedFirebaseConfig = listOf(
        File(rootProject.rootDir.parentFile, "AldanmazDrive_99/app/google-services.json"),
        File(userHome, "Desktop/AldanmazDrive_99/app/google-services.json"),
        File(userHome, "Desktop/GPTCHT/AldanmazDrive_72/app/google-services.json"),
    ).firstOrNull { it.isFile }

    if (verifiedFirebaseConfig != null) {
        verifiedFirebaseConfig.copyTo(googleServicesFile, overwrite = true)
        println("102: Firebase yapilandirmasi calisan Aldanmaz Drive projesinden kopyalandi: " + verifiedFirebaseConfig.parentFile.parentFile.name)
    } else {
        println("102: Firebase dosyasi bulunamadi. Calisan 99 projesindeki app/google-services.json dosyasini bu projenin app klasorune kopyalayin.")
    }
}
if (googleServicesFile.exists()) apply(plugin = "com.google.gms.google-services")

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { load(it) }
}
val tomTomApiKey = localProperties.getProperty("TOMTOM_API_KEY", "")
val hereApiKey = "XxFmf94n6m2-uI8bkUWv5RFnears4QND0dGMDYu_13M"
fun navProp(key: String, default: String): String = localProperties.getProperty(key, default).trim()

android {
    namespace = "com.aldanmaz.drivedashboard"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.aldanmaz.drivedashboard"
        minSdk = 29
        targetSdk = 37
        // libvlc tum islemci mimarilerini icerir; boyutu sinirlamak icin yalnizca ARM (telefon / multimedya) tutulur.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        versionCode = 285
        versionName = "2111261546-1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TOMTOM_API_KEY", "\"$tomTomApiKey\"")
        buildConfigField("String", "HERE_API_KEY", "\"$hereApiKey\"" )
        // 3D navigasyon saglayicilari: hepsi local.properties'ten degistirilebilir, API anahtari gerekmez.
        buildConfigField("String", "NAV_STYLE_URL", "\"${navProp("NAV_STYLE_URL", "https://tiles.openfreemap.org/styles/liberty")}\"")
        buildConfigField("String", "NAV_GEOCODER_URL", "\"${navProp("NAV_GEOCODER_URL", "https://photon.komoot.io/api/")}\"")
        buildConfigField("String", "NAV_GEOCODER_FALLBACK_URL", "\"${navProp("NAV_GEOCODER_FALLBACK_URL", "https://nominatim.openstreetmap.org/search")}\"")
        buildConfigField("String", "NAV_ROUTER_URL", "\"${navProp("NAV_ROUTER_URL", "https://valhalla1.openstreetmap.de/route")}\"")
        buildConfigField("String", "NAV_ROUTER_FALLBACK_URL", "\"${navProp("NAV_ROUTER_FALLBACK_URL", "https://router.project-osrm.org")}\"")
        buildConfigField("String", "NAV_USER_AGENT", "\"AldanmazDrive/$versionName (Android; +https://github.com/ademme3-a11y/aldanmazapps)\"")
    }
    signingConfigs {
        create("stableDebug") {
            storeFile = file("${rootProject.projectDir}/app/aldanmaz-debug.keystore")
            storePassword = System.getenv("ALDANMAZ_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("ALDANMAZ_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("ALDANMAZ_KEY_PASSWORD") ?: "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release { optimization { enable = false } }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation(libs.androidx.activity.compose)
    implementation("com.google.android.gms:play-services-location:21.4.0")
    // 3D navigasyon: yerel (native) vektor harita motoru, WebView/CDN bagimliligi yok. BSD-2 lisansli.
    implementation("org.maplibre.gl:android-sdk:13.6.1")
    // Arac kamerasi canli goruntu: VLC oynatici kutuphanesi (kameranin RTSP akisi ExoPlayer ile acilmadi: "missing attribute fmtp")
    implementation("org.videolan.android:libvlc-all:3.6.0")
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// 147: stable debug signing is intentionally fixed for upgrade installs; cache loss must fail rather than silently rotate the signing key.
