plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace="com.aldanmaz.shoppingagent"
 compileSdk=35
 defaultConfig { applicationId="com.aldanmaz.shoppingagent"; minSdk=26; targetSdk=35; versionCode=2; versionName="2.0" }
 buildTypes { release { isMinifyEnabled=false } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies {
 val bom=platform("androidx.compose:compose-bom:2025.02.00")
 implementation(bom)
 androidTestImplementation(bom)
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("org.jsoup:jsoup:1.18.3")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
