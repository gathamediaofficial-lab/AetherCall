plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.aether.call"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aether.call"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Jetpack Compose & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // WebRTC Android SDK
    implementation("io.getstream:stream-webrtc-android:1.3.0")

    // On-Device Neural Noise Suppression (ONNX Runtime Mobile with 16 KB page alignment)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // Telecom & Coroutines
    implementation("androidx.core:core-telecom:1.0.0-beta01")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Coil for Jetpack Compose image rendering
    implementation("io.coil-kt:coil-compose:2.7.0")

    // AndroidX Palette for dynamic color extraction
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Room Database for contact posters and custom photo overrides
    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    "kapt"("androidx.room:room-compiler:$roomVersion")

    // Jetpack DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Jetpack Glance for App Widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // ZXing Core for QR Code Generation
    implementation("com.google.zxing:core:3.5.4")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}