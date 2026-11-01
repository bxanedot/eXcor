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

rootProject.name = "eXcor"

// The historical engine in src/, quarantined: built only for the apps, never part of the clean-room root artifact.
include(":historical-engine")
// The Android app's shared logic, on top of the historical engine.
include(":app-core")
// The Android app itself.
include(":android-app")

// JVM desktop shell: Windows, macOS and Linux use the same Kotlin engine.
include(":desktop-app")
