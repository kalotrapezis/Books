plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kalotrapezis.books"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kalotrapezis.books"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
    }

    buildFeatures {
        compose = true
    }

    sourceSets.named("main") {
        assets.setSrcDirs(listOf("src/main/assets", "../third_party/foliate-js"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.webkit:webkit:1.16.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
