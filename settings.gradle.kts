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

// Preview apps for trying the library by hand: an Android APK and a Windows EXE. Not published.
include(":preview-android")
include(":preview-desktop")
project(":preview-android").projectDir = file("modules/platforms/preview/android")
project(":preview-desktop").projectDir = file("modules/platforms/preview/desktop")
