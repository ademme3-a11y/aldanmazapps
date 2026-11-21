plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aldanmaz.kameratest"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.aldanmaz.kameratest"
        minSdk = 29
        targetSdk = 37
        // libvlc tum islemci mimarilerini icerir; boyutu sinirlamak icin yalnizca ARM (telefon / multimedya) tutulur.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        versionCode = 1
        versionName = "2111262147-1"
    }
    signingConfigs {
        create("stableDebug") {
            storeFile = file("${rootProject.projectDir}/app/kameratest-debug.keystore")
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
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Araç kamerası canlı görüntü: VLC oynatıcı kütüphanesi (RTSP akışı ExoPlayer ile açılmıyor)
    implementation("org.videolan.android:libvlc-all:3.6.0")
}
