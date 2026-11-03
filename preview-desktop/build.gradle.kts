// A preview app for trying the library by hand on the desktop: YouTube Music and YouTube search, albums, playlists and
// artists, video details, audio and video playback in the system's player, formats, saving, and the library's log.
// `packageApp` makes a self-contained app folder with its own Java runtime; on Windows that folder holds
// "eXcor Preview.exe". Never published.
plugins {
    kotlin("jvm")
    application
}

repositories { mavenCentral(); google() }

kotlin {
    jvmToolchain(27)
    // The browsing and download code shared with the Android preview.
    sourceSets.named("main") { kotlin.srcDir("../preview-common/kotlin") }
}

// Kotlin 2.4 emits JVM 26 at most; the Java task must target the same, or the build stops.
java { targetCompatibility = JavaVersion.VERSION_26 }

dependencies {
    implementation(project(":"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // Reads the youtube.com cookies of Zen or Firefox for "Import from Zen or Firefox".
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
}

application {
    mainClass.set("com.excor.preview.MainKt")
    applicationName = "excor-preview"
}

// -PpreviewPackageType=exe makes a Windows installer instead (jpackage needs the WiX toolset for that).
val packageType = providers.gradleProperty("previewPackageType").getOrElse("app-image")

val packageApp = tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "Packages the preview as a self-contained app folder (an .exe on Windows) with jpackage."
    dependsOn(tasks.named("installDist"))
    val install = layout.buildDirectory.dir("install/excor-preview/lib")
    val output = layout.buildDirectory.dir(if (packageType == "app-image") "app" else "installer")
    val jdk = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(27)) }.map { it.metadata.installationPath }
    inputs.dir(install)
    outputs.dir(output)
    doFirst { output.get().asFile.deleteRecursively() }
    executable = jdk.get().file(if (System.getProperty("os.name").startsWith("Windows")) "bin/jpackage.exe" else "bin/jpackage").asFile.path
    args(
        "--type", packageType,
        "--name", "eXcor Preview",
        "--app-version", "0.2.0",
        "--vendor", "eXcor",
        "--input", install.get().asFile.path,
        "--main-jar", "preview-desktop.jar",
        "--main-class", "com.excor.preview.MainKt",
        "--java-options", "-Xmx512m",
        "--dest", output.get().asFile.path,
    )
    // The installer goes into the user's own folders (no administrator rights) and adds a Start menu entry.
    if (packageType == "exe") args("--win-per-user-install", "--win-menu", "--win-shortcut", "--win-dir-chooser")
}
