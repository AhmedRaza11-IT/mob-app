pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VibeSync"
include(":androidApp")
include(":adminApp")
include(":shared")
project(":shared").projectDir = file("kmp_shared")
