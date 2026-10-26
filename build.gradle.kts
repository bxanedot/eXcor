import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform") version "2.4.21"
    `maven-publish`
}

group = "com.vyonex"
version = "0.2.0"

repositories { mavenCentral() }

// The challenge solver scripts must reach every target, and Kotlin/Native has no resource
// loading, so they are compiled in as string constants.
val generateEjs = tasks.register("generateEjs") {
    val input = layout.projectDirectory.dir("swift/Sources/VyonExKit/ejs")
    val output = layout.buildDirectory.dir("generated/ejs")
    inputs.dir(input)
    outputs.dir(output)
    doLast {
        fun literal(s: String) = s.chunked(16_000).joinToString(" +\n") { chunk ->
            buildString {
                append('"')
                for (c in chunk) when {
                    c == '\\' || c == '"' || c == '$' -> append('\\').append(c)
                    c == '\n' -> append("\\n")
                    c.code in 0x20..0x7e -> append(c)
                    else -> append("\\u%04x".format(c.code))
                }
                append('"')
            }
        }
        val dir = input.asFile
        val file = output.get().file("com/vyonex/internal/Ejs.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.vyonex.internal\n\n" +
                "// Generated from swift/Sources/VyonExKit/ejs (yt-dlp/ejs 0.8.0, Unlicense; bundles meriyah ISC and astring MIT).\n" +
                "internal val EJS_LIB: String =\n${literal(dir.resolve("lib.min.js").readText())}\n\n" +
                "internal val EJS_CORE: String =\n${literal(dir.resolve("core.min.js").readText())}\n",
        )
    }
}

kotlin {
    jvmToolchain(27)
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_26 }
        // TestNG (Apache-2.0) instead of the default JUnit 4 (EPL-1.0), so every dependency is permissive.
        testRuns.named("test") { executionTask.configure { useTestNG() } }
    }

    val xcf = XCFramework("VyonEx")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "VyonEx"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateEjs)
            dependencies {
                implementation("io.ktor:ktor-client-core:3.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
                implementation("io.github.dokar3:quickjs-kt:1.0.15")
            }
        }
        jvmMain.dependencies { api("io.ktor:ktor-client-okhttp:3.6.0") }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.6.0") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

// Tests read the shared top-level fixtures/ folder by absolute path, which also works from the iOS simulator.
val generateFixturesPath = tasks.register("generateFixturesPath") {
    val path = file("fixtures").absolutePath
    val output = layout.buildDirectory.dir("generated/fixtures")
    inputs.property("path", path)
    outputs.dir(output)
    doLast {
        output.get().file("com/vyonex/FixturesPath.kt").asFile.apply { parentFile.mkdirs() }
            .writeText("package com.vyonex\n\ninternal const val FIXTURES = \"${path.replace("\\", "/")}\"\n")
    }
}
kotlin.sourceSets.commonTest { kotlin.srcDir(generateFixturesPath) }

// Declared so that toggling VYONEX_LIVE re-runs the tests instead of reusing results.
tasks.withType<Test>().configureEach { environment("VYONEX_LIVE", System.getenv("VYONEX_LIVE") ?: "") }
tasks.withType<AbstractTestTask>().configureEach {
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
