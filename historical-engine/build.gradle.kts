import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

// The historical engine in src/, built only for app-core and the apps (owner, 2026-11-01). It is quarantined pending
// the provenance review (docs/CLEAN_ROOM.md): never published, never part of the clean-room eXcor artifact, which
// the root project builds from cleanroom-src/ alone.
plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

repositories {
    google()
    mavenCentral()
}

val root = rootProject.layout.projectDirectory

// The challenge solver scripts must reach every target, and Kotlin/Native has no resource loading, so they are
// compiled in as string constants. This is the historical engine's own copy in ejs/, not cleanroom-src/ejs/.
val generateEjs = tasks.register("generateEjs") {
    val input = root.dir("ejs")
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
        val file = output.get().file("com/excor/internal/Ejs.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package com.excor.internal\n\n" +
                "// Generated from ejs/ (yt-dlp/ejs 0.8.0, Unlicense; bundles meriyah ISC and astring MIT).\n" +
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
    android {
        // Apart from the clean-room library's "com.excor": two Android libraries may not share one.
        namespace = "com.excor.historical"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
        // MergeCheck runs ExcorMerge on an emulator in CI; see the android-device job.
        withDeviceTest { instrumentationRunner = "com.excor.MergeCheck" }
    }
    // app-core declares them; only their compilation is needed, no framework is linked.
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    // The default hierarchy (iosMain and friends) plus jvmCommonMain: the OkHttp and java.io.File host API that the
    // JVM and Android share.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        configureEach { kotlin.setSrcDirs(listOf(root.dir("src/$name"))) }
        commonMain {
            kotlin.srcDir(generateEjs)
            dependencies {
                implementation("io.ktor:ktor-client-core:3.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
                implementation("io.github.dokar3:quickjs-kt:1.0.15")
            }
        }
        getByName("jvmCommonMain").dependencies { api("io.ktor:ktor-client-okhttp:3.6.0") }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.6.0") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:3.6.0")
        }
    }
}

// The historical tests read the older captures in the top-level fixtures/ folder by absolute path.
val generateFixturesPath = tasks.register("generateFixturesPath") {
    val path = root.dir("fixtures").asFile.absolutePath
    val output = layout.buildDirectory.dir("generated/fixtures")
    inputs.property("path", path)
    outputs.dir(output)
    doLast {
        output.get().file("com/excor/FixturesPath.kt").asFile.apply { parentFile.mkdirs() }
            .writeText("package com.excor\n\ninternal const val FIXTURES = \"${path.replace("\\", "/")}\"\n")
    }
}
kotlin.sourceSets.commonTest { kotlin.srcDir(generateFixturesPath) }

// Declared so that changing these re-runs the opt-in tests instead of reusing results.
tasks.withType<Test>().configureEach {
    listOf("EXCOR_LIVE", "EXCOR_LIVE_IDS", "EXCOR_LIVE_SOUNDCLOUD", "EXCOR_BENCH", "EXCOR_BENCH_IDS", "EXCOR_CAPTURE", "EXCOR_CAPTURE_ONLY")
        .forEach { environment(it, System.getenv(it) ?: "") }
}
tasks.withType<AbstractTestTask>().configureEach {
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
