pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "vyonex"

// The Android app's shared logic; the library itself is the root project.
include(":app-core")
// The Android app itself.
include(":android-app")

// JVM desktop shell: Windows, macOS and Linux use the same Kotlin engine.
include(":desktop-app")
