// Native Swing shell backed by the SAME Excor JVM engine used by Android and iOS.
plugins {
    kotlin("jvm")
    application
}

repositories { mavenCentral(); google() }

kotlin { jvmToolchain(27) }

// Kotlin 2.4 emits JVM 26 at most; the Java task must target the same, or the build stops.
java { targetCompatibility = JavaVersion.VERSION_26 }

dependencies {
    implementation(project(":app-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
}

application { mainClass.set("com.excor.desktop.MainKt") }
