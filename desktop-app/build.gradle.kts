import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Native Swing shell backed by the SAME VyonEx JVM engine used by Android and iOS.
plugins {
    kotlin("jvm") version "2.4.21"
    application
}

repositories { mavenCentral(); google() }

kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget = JvmTarget.JVM_26 }
}

dependencies {
    implementation(project(":app-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
}

application { mainClass.set("com.vyonex.desktop.MainKt") }
