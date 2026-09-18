plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
}

android {
    namespace = "com.whatsapp.clone.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
