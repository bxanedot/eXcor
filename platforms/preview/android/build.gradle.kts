import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// A preview app for trying the library by hand on Android: YouTube Music and YouTube search, albums, playlists and
// artists, video details, audio and video playback, downloads, formats and the library's log. Built as a debug APK;
// never published.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
}

repositories {
    google()
    mavenCentral()
}

// The preview-only Android graph includes Guava; keep it on OSV's fixed Android artifact.
configurations.configureEach {
    resolutionStrategy.force("com.google.guava:guava:33.7.2-android")
}

android {
    namespace = "com.excor.preview"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.excor.preview"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        // A plain Instrumentation rather than androidx.test's runner, which needs JUnit 4 (EPL-1.0).
        testInstrumentationRunner = "com.excor.preview.JoinCheck"
    }
    // The browsing and download code shared with the desktop preview.
    sourceSets.getByName("main").kotlin.srcDir("../common/kotlin")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(27)
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    implementation(project(":"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
