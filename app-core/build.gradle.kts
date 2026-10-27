import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's shared logic (queue, playback orchestration), on top of the library at the repository root.
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(27)
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_26 }
        // TestNG (Apache-2.0) instead of JUnit 4 (EPL-1.0), as in the library.
        testRuns.named("test") { executionTask.configure { useTestNG() } }
    }
    android {
        namespace = "com.vyonex.app.core"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            // Its types (VyonExStream, VyonExUnplayableException) are part of this module's API.
            api(project(":"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
