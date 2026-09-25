plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.whatsapp.clone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vibesync.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "2.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "app_name", "VibeSync")
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/keystore/release.jks")
            storePassword = "vibesync123"
            keyAlias = "vibesync"
            keyPassword = "vibesync123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    
    // Jetpack Compose Foundation & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Media & Audio Playback (ExoPlayer wrapper for Voice Notes)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Agora RTC Calling SDK (16 KB page-aligned release)
    implementation("io.agora.rtc:full-sdk:4.5.0")

    // Ktor Client & WebSockets
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-websockets:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // Android WorkManager for Background Sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Google Play Services Location for FusedLocationProviderClient
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // OkHttp Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // SQLDelight / Room KMP Local Storage
    implementation("androidx.room:room-runtime:2.6.1")

    // KMP Shared Module Dependency
    implementation(project(":shared"))

    // Settings: DataStore, Navigation Compose, Biometric
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.biometric:biometric:1.1.0")

    // AndroidX Test & JUnit4 Runner
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")

    // Jetpack Compose UI Testing & Manifest
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.0")
}
