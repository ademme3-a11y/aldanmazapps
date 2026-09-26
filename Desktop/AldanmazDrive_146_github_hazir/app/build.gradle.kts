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
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { load(it) }
}
val tomTomApiKey = localProperties.getProperty("TOMTOM_API_KEY", "")

android {
    namespace = "com.aldanmaz.drivedashboard"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.aldanmaz.drivedashboard"
        minSdk = 29
        targetSdk = 37
        versionCode = 185
        versionName = "146"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TOMTOM_API_KEY", "\"$tomTomApiKey\"")
    }
    signingConfigs {
        create("stableDebug") {
            storeFile = file("aldanmaz-debug.keystore")
            storePassword = "Aldanmaz146Debug"
            keyAlias = "aldanmazdebug"
            keyPassword = "Aldanmaz146Debug"
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
    implementation(platform(libs.compose.bom))
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation(libs.androidx.activity.compose)
    implementation("com.google.android.gms:play-services-location:21.4.0")
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}